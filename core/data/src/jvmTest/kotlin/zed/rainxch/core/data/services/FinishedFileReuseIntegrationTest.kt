package zed.rainxch.core.data.services

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import zed.rainxch.core.data.network.DesktopDigestVerifier
import zed.rainxch.core.domain.network.AssetIdentity
import zed.rainxch.core.domain.utils.AssetFileName

class FinishedFileReuseIntegrationTest {

    private val deadUrl = "http://127.0.0.1:1/dead.apk"

    private val content = ByteArray(256) { (it and 0xFF).toByte() }
    private val contentSize = content.size.toLong()
    private val contentDigest = "sha256:" + sha256Hex(content)

    @Test
    fun a_finished_file_with_a_matching_digest_is_reused_without_touching_the_network() = runBlocking {
        withTempDownloadsDir { dir ->
            val safeName = AssetFileName.scoped("owner", "repo", "app.apk")
            File(dir, safeName).writeBytes(content)

            val downloader = newDownloader(dir)
            val progress =
                downloader
                    .download(
                        url = deadUrl,
                        suggestedFileName = safeName,
                        bypassMirror = false,
                        identity =
                            AssetIdentity(assetId = 42L, digest = contentDigest, size = contentSize),
                    ).toList()

            val last =
                assertNotNull(
                    progress.lastOrNull(),
                    "the reuse path must emit at least one progress event",
                )
            assertEquals(100, last.percent, "a reused file is reported as fully present")
            assertEquals(contentSize, last.bytesDownloaded)
            assertEquals(contentSize, last.totalBytes)
        }
    }

    @Test
    fun a_size_mismatch_is_not_reused_and_the_transfer_reaches_the_dead_url() = runBlocking {
        withTempDownloadsDir { dir ->
            val safeName = AssetFileName.scoped("owner", "repo", "app.apk")
            File(dir, safeName).writeBytes(content)

            val downloader = newDownloader(dir)
            var failure: Throwable? = null
            try {
                downloader
                    .download(
                        url = deadUrl,
                        suggestedFileName = safeName,
                        bypassMirror = false,
                        identity =
                            AssetIdentity(
                                assetId = 42L,
                                digest = contentDigest,
                                size = contentSize + 1,
                            ),
                    ).toList()
            } catch (e: Exception) {
                failure = e
            }

            assertNotNull(
                failure,
                "with reuse refused the downloader has no choice but to reach the dead URL and fail",
            )
        }
    }

    private fun newDownloader(dir: File) =
        DesktopDownloader(
            files = FakeFileLocations(dir),
            tokenStore = FakeTokenStore(),
            digestVerifier = DesktopDigestVerifier(),
        )

    private suspend fun withTempDownloadsDir(block: suspend (File) -> Unit) {
        val dir = Files.createTempDirectory("komi-finished-reuse").toFile()
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
