package zed.rainxch.core.data.services

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import zed.rainxch.core.data.download.PartialIdentity
import zed.rainxch.core.data.download.PartialNaming
import zed.rainxch.core.data.download.PartialRetry
import zed.rainxch.core.data.network.DesktopDigestVerifier
import zed.rainxch.core.domain.network.AssetIdentity
import zed.rainxch.core.domain.utils.AssetFileName

class ResumeAgainstServerTest {

    private val body = ByteArray(4096) { (it * 7 and 0xFF).toByte() }
    private val resumeFrom = 500L
    private val scopedName = AssetFileName.scoped("owner", "repo", "app.apk")
    private val identity =
        AssetIdentity(assetId = 42L, digest = "sha256:" + sha256Hex(body), size = body.size.toLong())

    @Test
    fun a_206_at_the_requested_offset_resumes_and_lands_the_whole_asset() =
        runBlocking {
            withServer(Mode.HONOUR_RANGE) { server ->
                withTempDownloadsDir { dir ->
                    writeResumablePartial(dir)
                    val downloader = newDownloader(dir)

                    downloader
                        .download(
                            url = server.url,
                            suggestedFileName = scopedName,
                            bypassMirror = true,
                            identity = identity,
                        ).toList()

                    assertContentEquals(body, File(dir, scopedName).readBytes())
                    assertEquals(listOf("bytes=$resumeFrom-"), server.ranges)
                }
            }
        }

    @Test
    fun a_206_naming_a_different_span_restarts_instead_of_splicing() =
        runBlocking {
            withServer(Mode.ANSWER_206_FOR_THE_WRONG_SPAN) { server ->
                withTempDownloadsDir { dir ->
                    writeResumablePartial(dir)
                    val downloader = newDownloader(dir)

                    downloader
                        .download(
                            url = server.url,
                            suggestedFileName = scopedName,
                            bypassMirror = true,
                            identity = identity,
                        ).toList()

                    assertContentEquals(
                        body,
                        File(dir, scopedName).readBytes(),
                        "the asset, not our prefix spliced onto a body that did not continue it",
                    )
                    assertEquals(
                        "bytes=$resumeFrom-",
                        server.ranges.first(),
                        "the partial must genuinely have been offered for resume",
                    )
                    assertNull(
                        server.ranges.getOrNull(1),
                        "the restart must re-request without a Range header",
                    )
                }
            }
        }

    @Test
    fun a_cancel_during_the_retry_backoff_stops_the_transfer() =
        runBlocking {
            withTimeout(30_000) {
                withServer(Mode.FAIL_FIRST_REQUEST) { server ->
                    withTempDownloadsDir { dir ->
                        val downloader = newDownloader(dir)

                        var failure: Throwable? = null
                        val job =
                            launch(Dispatchers.IO) {
                                try {
                                    downloader
                                        .download(
                                            url = server.url,
                                            suggestedFileName = scopedName,
                                            bypassMirror = true,
                                            identity = identity,
                                        ).toList()
                                } catch (e: Throwable) {
                                    failure = e
                                }
                            }

                        assertTrue(
                            server.awaitFirstRequest(10_000),
                            "the first attempt must reach the server",
                        )
                        Thread.sleep(BACKOFF_WINDOW_PROBE_MILLIS)

                        assertTrue(
                            downloader.cancelDownload(scopedName),
                            "a live transfer existed, so the pause must be accepted",
                        )

                        job.join()

                        assertTrue(
                            failure != null,
                            "the transfer must end, not run on to completion after being cancelled",
                        )
                        assertEquals(
                            1,
                            server.requestCount,
                            "the retry must never reach the server",
                        )
                        assertTrue(
                            !File(dir, scopedName).exists(),
                            "nothing was downloaded, so no finished file may be left behind",
                        )
                    }
                }
            }
        }

    @Test
    fun a_source_without_range_support_rewrites_the_partial_instead_of_splicing() =
        runBlocking {
            withServer(Mode.IGNORE_RANGE) { server ->
                withTempDownloadsDir { dir ->
                    writeResumablePartial(dir)
                    val downloader = newDownloader(dir)

                    downloader
                        .download(
                            url = server.url,
                            suggestedFileName = scopedName,
                            bypassMirror = true,
                            identity = identity,
                        ).toList()

                    assertContentEquals(
                        body,
                        File(dir, scopedName).readBytes(),
                        "a 200 says the server ignored Range, so the partial had to be rewritten",
                    )
                    assertEquals(
                        "bytes=$resumeFrom-",
                        server.ranges.first(),
                        "the partial must genuinely have been offered for resume first",
                    )
                    assertEquals(
                        1,
                        server.requestCount,
                        "the fallback is a rewrite, not an error, so it must not need a retry",
                    )
                    assertTrue(
                        !File(dir, PartialNaming.meta(scopedName)).exists(),
                        "completing the transfer consumes the sidecar",
                    )
                }
            }
        }

    private enum class Mode {
        HONOUR_RANGE,

        ANSWER_206_FOR_THE_WRONG_SPAN,

        IGNORE_RANGE,

        FAIL_FIRST_REQUEST,
    }

    private class RangeServer(
        private val body: ByteArray,
        private val mode: Mode,
    ) {
        private val pool = Executors.newFixedThreadPool(4)
        private val server =
            HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
                executor = pool
                createContext("/app.apk") { exchange -> respond(exchange) }
            }
        private val requestLog = Collections.synchronizedList(mutableListOf<String?>())
        private val arrival = CountDownLatch(1)

        val url: String get() = "http://127.0.0.1:${server.address.port}/app.apk"
        val requestCount: Int get() = requestLog.size

        val ranges: List<String?> get() = requestLog.toList()

        fun start() = server.start()

        fun stop() {
            server.stop(0)
            pool.shutdownNow()
        }

        fun awaitFirstRequest(timeoutMillis: Long): Boolean =
            arrival.await(timeoutMillis, TimeUnit.MILLISECONDS)

        private fun respond(exchange: HttpExchange) {
            val range = exchange.requestHeaders.getFirst("Range")
            requestLog.add(range)
            if (requestLog.size == 1) arrival.countDown()

            if (mode == Mode.FAIL_FIRST_REQUEST && requestLog.size == 1) {
                exchange.sendResponseHeaders(500, -1)
                exchange.close()
                return
            }

            if (mode == Mode.IGNORE_RANGE) {
                send(exchange, 200, body, contentRange = null)
                return
            }

            val requested = range?.let { RANGE_HEADER.find(it)?.groupValues?.get(1)?.toLong() }
            if (requested == null) {
                send(exchange, 200, body, contentRange = null)
                return
            }
            when (mode) {
                Mode.ANSWER_206_FOR_THE_WRONG_SPAN ->
                    send(exchange, 206, body, contentRange = "bytes 0-${body.size - 1}/${body.size}")

                else ->
                    send(
                        exchange,
                        206,
                        body.copyOfRange(requested.toInt(), body.size),
                        contentRange = "bytes $requested-${body.size - 1}/${body.size}",
                    )
            }
        }

        private fun send(exchange: HttpExchange, code: Int, payload: ByteArray, contentRange: String?) {
            if (contentRange != null) exchange.responseHeaders.add("Content-Range", contentRange)
            exchange.sendResponseHeaders(code, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
    }

    private inline fun withServer(mode: Mode, block: (RangeServer) -> Unit) {
        val server = RangeServer(body, mode)
        server.start()
        try {
            block(server)
        } finally {
            server.stop()
        }
    }

    private fun writeResumablePartial(dir: File) {
        File(dir, PartialNaming.part(scopedName)).writeBytes(body.copyOfRange(0, resumeFrom.toInt()))
        File(dir, PartialNaming.meta(scopedName)).writeText(
            PartialIdentity(assetId = 42L, digest = identity.digest, size = identity.size)
                .serialize(),
        )
    }

    private fun newDownloader(dir: File) =
        DesktopDownloader(
            files = FakeFileLocations(dir),
            tokenStore = FakeTokenStore(),
            digestVerifier = DesktopDigestVerifier(),
        )

    private suspend fun withTempDownloadsDir(block: suspend (File) -> Unit) {
        val dir = Files.createTempDirectory("komi-resume-server").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        val RANGE_HEADER = Regex("""bytes=(\d+)-""")

        val BACKOFF_WINDOW_PROBE_MILLIS = PartialRetry.backoffMillis(0) / 3
    }
}
