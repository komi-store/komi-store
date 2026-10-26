package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import zed.rainxch.core.data.datasource.TokenStore
import zed.rainxch.core.data.download.AssetSourceGoneException
import zed.rainxch.core.data.download.ContentRange
import zed.rainxch.core.data.download.FinishedFileReuse
import zed.rainxch.core.data.download.OrphanPolicy
import zed.rainxch.core.data.download.OrphanVerdict
import zed.rainxch.core.data.download.PartialDisposal
import zed.rainxch.core.data.download.PartialIdentity
import zed.rainxch.core.data.download.PartialNaming
import zed.rainxch.core.data.download.PartialOwnership
import zed.rainxch.core.data.download.PartialRetry
import zed.rainxch.core.data.download.PartialReuse
import zed.rainxch.core.data.download.ProgressThrottle
import zed.rainxch.core.data.download.RangeDecision
import zed.rainxch.core.data.download.RangeRequest
import zed.rainxch.core.data.network.GithubAssetAuth
import zed.rainxch.core.data.network.ProxyManager
import zed.rainxch.core.data.network.resolveAndroidSystemProxy
import zed.rainxch.core.domain.model.installation.DownloadProgress
import zed.rainxch.core.domain.model.settings.ProxyConfig
import zed.rainxch.core.domain.model.settings.ProxyScope
import zed.rainxch.core.domain.network.AssetIdentity
import zed.rainxch.core.domain.network.DigestVerifier
import zed.rainxch.core.domain.network.Downloader
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Authenticator
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

class AndroidDownloader(
    private val files: FileLocationsProvider,
    private val tokenStore: TokenStore,
    private val digestVerifier: DigestVerifier,
) : Downloader {
    private val activeDownloads = ConcurrentHashMap<String, Call>()
    private val idsByName = ConcurrentHashMap<String, MutableSet<String>>()

    private val cancelRequested = ConcurrentHashMap.newKeySet<String>()

    private val nameLocks = ConcurrentHashMap<String, Mutex>()

    private val clientLock = Any()
    private var cachedConfig: ProxyConfig? = null
    private var cachedClient: OkHttpClient? = null

    private fun downloadClient(): OkHttpClient {
        val config = ProxyManager.currentConfig(ProxyScope.DOWNLOAD)
        if (!config.isCacheable()) return buildClient(config)
        return synchronized(clientLock) {
            cachedClient?.takeIf { cachedConfig == config } ?: run {
                cachedClient?.connectionPool?.evictAll()
                buildClient(config).also {
                    cachedConfig = config
                    cachedClient = it
                }
            }
        }
    }

    private fun ProxyConfig.isCacheable(): Boolean =
        when (this) {
            ProxyConfig.None, is ProxyConfig.Http -> true
            ProxyConfig.System -> false
            is ProxyConfig.Socks -> username == null || password == null
        }

    private fun buildClient(config: ProxyConfig): OkHttpClient {
        Authenticator.setDefault(null)

        return OkHttpClient
            .Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .apply {
                when (config) {
                    is ProxyConfig.None -> {
                        proxy(Proxy.NO_PROXY)
                    }

                    is ProxyConfig.System -> {
                        proxy(resolveAndroidSystemProxy())
                    }

                    is ProxyConfig.Http -> {
                        proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(config.host, config.port)))
                        if (config.username != null && config.password != null) {
                            proxyAuthenticator { _, response ->
                                response.request
                                    .newBuilder()
                                    .header(
                                        "Proxy-Authorization",
                                        Credentials.basic(config.username!!, config.password!!),
                                    ).build()
                            }
                        }
                    }

                    is ProxyConfig.Socks -> {
                        proxy(Proxy(Proxy.Type.SOCKS, InetSocketAddress(config.host, config.port)))
                        if (config.username != null && config.password != null) {
                            Authenticator.setDefault(
                                object : Authenticator() {
                                    override fun getPasswordAuthentication() =
                                        PasswordAuthentication(
                                            config.username,
                                            config.password!!.toCharArray(),
                                        )
                                },
                            )
                        }
                    }
                }
            }.build()
    }

    override fun download(
        url: String,
        suggestedFileName: String?,
        bypassMirror: Boolean,
        identity: AssetIdentity?,
    ): Flow<DownloadProgress> =
        flow {
            val client = downloadClient()

            val dir = File(files.appDownloadsDir())
            if (!dir.exists()) dir.mkdirs()

            val safeName = safeFileName(url, suggestedFileName)
            val request =
                DownloadRequest(
                    client = client,
                    url = url,
                    id = UUID.randomUUID().toString(),
                    identity = identity,
                    dir = dir,
                    name = safeName,
                )

            Logger.d { "Starting download: $url (id=${request.id}, partial=${request.partFile.name})" }

            val lock = nameLocks.computeIfAbsent(safeName) { Mutex() }
            lock.withLock {
                idsByName.computeIfAbsent(safeName) { ConcurrentHashMap.newKeySet() }.add(request.id)
                try {
                    downloadWithRetries(request) { emit(it) }
                } finally {
                    activeDownloads.remove(request.id)
                    cancelRequested.remove(request.id)
                    idsByName.computeIfPresent(safeName) { _, set ->
                        set.remove(request.id)
                        if (set.isEmpty()) null else set
                    }
                }
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun downloadWithRetries(
        request: DownloadRequest,
        emit: suspend (DownloadProgress) -> Unit,
    ) {
        val budget = RetryBudget(request.destination.name)
        while (true) {
            when (val attempt = attemptOrFailure(request, emit)) {
                Attempt.Complete -> return
                Attempt.Restart -> budget.spendRestart()
                is Attempt.Aborted -> throw attempt.cause
                is Attempt.Failed -> budget.backOff(attempt.cause)
            }
        }
    }

    // A gone source and cancellation propagate; the other step failures come back as Attempt.Failed.
    private suspend fun attemptOrFailure(
        request: DownloadRequest,
        emit: suspend (DownloadProgress) -> Unit,
    ): Attempt =
        try {
            downloadAttempt(request, emit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            Attempt.Failed(e)
        } catch (e: IllegalStateException) {
            Attempt.Failed(e)
        } catch (e: IllegalArgumentException) {
            Attempt.Failed(e)
        } catch (e: SecurityException) {
            Attempt.Failed(e)
        }

    private suspend fun downloadAttempt(
        request: DownloadRequest,
        emit: suspend (DownloadProgress) -> Unit,
    ): Attempt {
        val stored = readIdentity(request.metaFile)
        prepareOwnedPartial(request, stored)
        val resumeFrom = if (request.partFile.exists()) request.partFile.length() else 0L
        if (reuseFinishedFile(request, emit)) return Attempt.Complete

        val call = request.client.newCall(buildRequest(request.url, RangeRequest.headerValue(resumeFrom)))
        activeDownloads[request.id] = call
        if (cancelRequested.remove(request.id)) call.cancel()
        val cancellationHandle =
            coroutineContext[Job]?.invokeOnCompletion { cause ->
                if (cause is CancellationException) call.cancel()
            }
        return try {
            call.execute().use { response -> receive(response, Transfer(request, resumeFrom, stored), emit) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            abortedOrRethrow(call, e)
        } catch (e: AssetSourceGoneException) {
            abortedOrRethrow(call, e)
        } catch (e: IllegalStateException) {
            abortedOrRethrow(call, e)
        } catch (e: IllegalArgumentException) {
            abortedOrRethrow(call, e)
        } catch (e: SecurityException) {
            abortedOrRethrow(call, e)
        } finally {
            cancellationHandle?.dispose()
        }
    }

    // A failure after the call was cancelled is the pause or discard itself, so it is not retried.
    private suspend fun abortedOrRethrow(
        call: Call,
        failure: Exception,
    ): Attempt {
        if (!call.isCanceled()) throw failure
        coroutineContext.ensureActive()
        return Attempt.Aborted(failure)
    }

    private fun prepareOwnedPartial(
        request: DownloadRequest,
        stored: PartialIdentity?,
    ) {
        val partFile = request.partFile
        val reusable =
            PartialOwnership.decideReuse(
                partExists = partFile.exists(),
                partLength = if (partFile.exists()) partFile.length() else 0L,
                meta = stored,
                incoming = request.identity,
            ) == PartialReuse.REUSE
        if (reusable) return

        request.discardPartial()
        if (!partFile.exists()) return
        val truncated =
            runCatching { FileOutputStream(partFile, false).close() }
                .isSuccess && partFile.length() == 0L
        if (!truncated) {
            throw IOException(
                "Cannot discard a partial that failed its ownership check: ${partFile.absolutePath}",
            )
        }
    }

    private suspend fun reuseFinishedFile(
        request: DownloadRequest,
        emit: suspend (DownloadProgress) -> Unit,
    ): Boolean {
        val destination = request.destination
        val expectedDigest =
            FinishedFileReuse.digestToVerify(
                destinationExists = destination.exists(),
                destinationLength = destination.length(),
                expectedDigest = request.identity?.digest,
                expectedSize = request.identity?.size ?: -1L,
            )
        if (expectedDigest == null || digestVerifier.verify(destination.absolutePath, expectedDigest) != null) {
            return false
        }
        request.discardPartial()
        val length = destination.length()
        Logger.d { "Reusing completed file: ${destination.absolutePath} ($length bytes)" }
        emit(DownloadProgress(length, length, PERCENT_COMPLETE))
        return true
    }

    private suspend fun receive(
        response: Response,
        transfer: Transfer,
        emit: suspend (DownloadProgress) -> Unit,
    ): Attempt {
        val contentRange = ContentRange.parse(response.header("Content-Range"))
        val decision = RangeRequest.decide(response.code, transfer.resumeFrom, contentRange)
        if (decision == RangeDecision.UNEXPECTED) throw unexpectedResponse(response.code, transfer.request)

        val total = totalBytes(response, transfer.resumeFrom, contentRange)
        if (transfer.mustRestart(decision, total)) {
            transfer.request.discardPartial()
            return Attempt.Restart
        }
        write(response, transfer, decision == RangeDecision.APPEND, total, emit)
        return Attempt.Complete
    }

    private fun unexpectedResponse(
        code: Int,
        request: DownloadRequest,
    ): Exception {
        if (!PartialDisposal.shouldDiscardPartial(code, explicitDiscard = false)) {
            return IOException("Unexpected code $code")
        }
        request.discardPartial()
        return AssetSourceGoneException(code)
    }

    private suspend fun write(
        response: Response,
        transfer: Transfer,
        append: Boolean,
        total: Long?,
        emit: suspend (DownloadProgress) -> Unit,
    ) {
        val request = transfer.request
        writeIdentity(
            request.metaFile,
            request.identity?.let {
                PartialIdentity(
                    assetId = it.assetId,
                    digest = it.digest,
                    size = if (it.size > 0L) it.size else (total ?: transfer.resumeFrom),
                )
            } ?: PartialIdentity(assetId = 0L, digest = null, size = total ?: transfer.resumeFrom),
        )

        if (!append) request.partFile.delete()
        val base = if (append) transfer.resumeFrom else 0L

        response.body.byteStream().use { input ->
            BufferedOutputStream(FileOutputStream(request.partFile, append), BUFFER_SIZE).use { output ->
                copyWithProgress(input, output, base, total, emit)
            }
        }
        finish(request, total, emit)
    }

    private suspend fun copyWithProgress(
        input: InputStream,
        output: OutputStream,
        base: Long,
        total: Long?,
        emit: suspend (DownloadProgress) -> Unit,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        val throttle = ProgressThrottle()
        var downloaded = base
        var bytesRead: Int
        while (input.read(buffer).also { bytesRead = it } != -1) {
            output.write(buffer, 0, bytesRead)
            downloaded += bytesRead
            val progress = DownloadProgress(downloaded, total, percentOf(downloaded, total))
            if (throttle.shouldEmit(progress)) emit(progress)
        }
    }

    private suspend fun finish(
        request: DownloadRequest,
        total: Long?,
        emit: suspend (DownloadProgress) -> Unit,
    ) {
        val finalLength = request.partFile.length()
        check(finalLength > 0L) { "Download produced empty file: ${request.partFile.absolutePath}" }
        if (total != null && total > 0L && finalLength != total) {
            throw IOException("Incomplete download: got $finalLength of $total bytes")
        }

        moveAtomic(request.partFile, request.destination)
        request.metaFile.delete()

        Logger.d { "Download complete: ${request.destination.absolutePath}" }
        emit(DownloadProgress(finalLength, total, percentOf(finalLength, total) ?: PERCENT_COMPLETE))
    }

    private fun percentOf(
        downloaded: Long,
        total: Long?,
    ): Int? = total?.takeIf { it > 0L }?.let { ((downloaded * PERCENT_COMPLETE) / it).toInt() }

    private suspend fun buildRequest(
        url: String,
        rangeHeader: String?,
    ): Request =
        Request
            .Builder()
            .url(url)
            .apply {
                val token = githubToken()
                if (token != null && GithubAssetAuth.isGithubHost(url)) {
                    header("Authorization", "Bearer $token")
                    if (GithubAssetAuth.isGithubApiHost(url)) {
                        header("Accept", "application/octet-stream")
                    }
                }
                if (rangeHeader != null) header("Range", rangeHeader)
            }.build()

    private fun readIdentity(metaFile: File): PartialIdentity? =
        try {
            if (metaFile.exists()) PartialIdentity.parse(metaFile.readText()) else null
        } catch (e: IOException) {
            Logger.w(e) { "Unreadable sidecar ${metaFile.name}; treating the partial as unattributable" }
            null
        }

    private fun writeIdentity(
        metaFile: File,
        identity: PartialIdentity,
    ) {
        try {
            metaFile.writeText(identity.serialize())
        } catch (e: IOException) {
            Logger.w(e) { "Failed to persist sidecar ${metaFile.name}" }
        }
    }

    private fun totalBytes(
        response: Response,
        resumeFrom: Long,
        contentRange: ContentRange?,
    ): Long? {
        val declared = contentRange?.total?.takeIf { it > 0L }
        val contentLength = response.body.contentLength()
        return when {
            declared != null -> declared
            contentLength <= 0L -> null
            response.code == HttpURLConnection.HTTP_PARTIAL -> resumeFrom + contentLength
            else -> contentLength
        }
    }

    private suspend fun githubToken(): String? =
        try {
            tokenStore
                .currentToken()
                ?.accessToken
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private fun moveAtomic(
        source: File,
        target: File,
    ) {
        try {
            Files.move(
                source.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    override suspend fun reclaimOrphanedPartials(claimedNames: Set<String>): Int =
        withContext(Dispatchers.IO) {
            val dir = File(files.appDownloadsDir())
            if (!dir.exists()) return@withContext 0

            val names = dir.list()?.toList().orEmpty()
            val liveNames = claimedNames + idsByName.keys
            val legacyPartNames = names.filter { PartialNaming.isLegacyPartFile(it) }
            val metaNames = names.filter { PartialNaming.isMetaFile(it) }
            val assetNames =
                (
                    legacyPartNames.mapNotNull { PartialNaming.assetNameOfPart(it) } +
                        metaNames.mapNotNull { PartialNaming.assetNameOfMeta(it) }
                ).toSet()

            var removed = 0
            for (assetName in assetNames) {
                val part = File(dir, PartialNaming.part(assetName))
                val meta = File(dir, PartialNaming.meta(assetName))

                val verdict =
                    OrphanPolicy.classify(
                        partExists = part.exists(),
                        metaExists = meta.exists(),
                        isClaimed = assetName in liveNames,
                    )
                removed += applyVerdict(verdict, part, meta)

                if (assetName in liveNames) continue
                legacyPartNames
                    .filter { PartialNaming.assetNameOfPart(it) == assetName && it != part.name }
                    .map { File(dir, it) }
                    .forEach { if (it.delete()) removed++ }
            }
            if (removed > 0) Logger.d { "Reclaimed $removed orphaned partial file(s)" }
            removed
        }

    private fun applyVerdict(
        verdict: OrphanVerdict,
        part: File,
        meta: File,
    ): Int {
        var removed = 0
        when (verdict) {
            OrphanVerdict.KEEP -> Unit
            OrphanVerdict.DROP_PART_ONLY -> if (part.delete()) removed++
            OrphanVerdict.DROP_META_ONLY -> if (meta.delete()) removed++
        }
        return removed
    }

    override suspend fun saveToFile(
        url: String,
        suggestedFileName: String?,
    ): String =
        withContext(Dispatchers.IO) {
            val file = File(files.appDownloadsDir(), safeFileName(url, suggestedFileName))

            Logger.d { "saveToFile downloading file..." }
            download(url, suggestedFileName).collect { }

            file.absolutePath
        }

    private fun safeFileName(
        url: String,
        suggestedFileName: String?,
    ): String {
        val rawName =
            suggestedFileName?.takeIf { it.isNotBlank() }
                ?: url
                    .substringAfterLast('/')
                    .substringBefore('?')
                    .substringBefore('#')
                    .ifBlank { "asset-${UUID.randomUUID()}.apk" }
        val safeName = rawName.substringAfterLast('/').substringAfterLast('\\')
        require(safeName.isNotBlank() && safeName != "." && safeName != "..") {
            "Invalid file name: $rawName"
        }
        return safeName
    }

    override suspend fun getDownloadedFilePath(fileName: String): String? =
        withContext(Dispatchers.IO) {
            val file = File(files.appDownloadsDir(), fileName)

            if (file.exists() && file.length() > 0) {
                file.absolutePath
            } else {
                null
            }
        }

    override suspend fun cancelDownload(fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            val ids = idsByName.remove(fileName)?.toList().orEmpty()
            if (ids.isEmpty()) return@withContext false

            for (id in ids) {
                cancelRequested.add(id)
                activeDownloads.remove(id)?.takeIf { !it.isCanceled() }?.cancel()
            }

            true
        }

    override suspend fun discardPartial(fileName: String): Boolean =
        withContext(Dispatchers.IO) {
            cancelDownload(fileName)

            val dir = File(files.appDownloadsDir())
            val lock = nameLocks[fileName]
            if (lock == null) {
                deletePartialPair(dir, fileName)
            } else {
                lock.withLock { deletePartialPair(dir, fileName) }
            }
        }

    private fun deletePartialPair(
        dir: File,
        safeName: String,
    ): Boolean {
        val partRemoved = File(dir, PartialNaming.part(safeName)).delete()
        val metaRemoved = File(dir, PartialNaming.meta(safeName)).delete()
        return partRemoved || metaRemoved
    }

    private class DownloadRequest(
        val client: OkHttpClient,
        val url: String,
        val id: String,
        val identity: AssetIdentity?,
        dir: File,
        name: String,
    ) {
        val destination = File(dir, name)
        val partFile = File(dir, PartialNaming.part(name))
        val metaFile = File(dir, PartialNaming.meta(name))

        fun discardPartial() {
            partFile.delete()
            metaFile.delete()
        }
    }

    private class Transfer(
        val request: DownloadRequest,
        val resumeFrom: Long,
        val stored: PartialIdentity?,
    ) {
        // A refused range restarts, and so does an append whose server total no longer matches the partial's.
        fun mustRestart(
            decision: RangeDecision,
            total: Long?,
        ): Boolean =
            when (decision) {
                RangeDecision.RESTART -> true
                RangeDecision.APPEND -> resumeFrom > 0L && storedSizeDiffers(total)
                RangeDecision.TRUNCATE_REWRITE, RangeDecision.UNEXPECTED -> false
            }

        private fun storedSizeDiffers(total: Long?): Boolean {
            if (stored == null || total == null) return false
            return stored.size > 0L && stored.size != total
        }
    }

    private sealed interface Attempt {
        data object Complete : Attempt

        data object Restart : Attempt

        class Aborted(
            val cause: Exception,
        ) : Attempt

        class Failed(
            val cause: Exception,
        ) : Attempt
    }

    private class RetryBudget(
        private val name: String,
    ) {
        private var attemptsMade = 0
        private var restartsMade = 0

        fun spendRestart() {
            if (restartsMade >= MAX_RESTARTS) {
                throw IOException("The bytes on disk could not be resumed for $name")
            }
            restartsMade++
            Logger.d {
                "The partial cannot be resumed (server refused the range, or served a span we did not ask for); " +
                    "restarting from zero"
            }
        }

        suspend fun backOff(failure: Exception) {
            coroutineContext.ensureActive()
            if (!PartialRetry.hasAttemptsLeft(attemptsMade + 1)) {
                Logger.e(failure) { "Download failed after ${PartialRetry.MAX_ATTEMPTS} attempts" }
                throw failure
            }
            val backoff = PartialRetry.backoffMillis(attemptsMade)
            Logger.w(failure) { "Download attempt ${attemptsMade + 1} failed; retrying in ${backoff}ms" }
            delay(backoff)
            attemptsMade++
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
        const val MAX_RESTARTS = 2
        const val PERCENT_COMPLETE = 100
        const val CONNECT_TIMEOUT_SECONDS = 30L
        const val READ_TIMEOUT_SECONDS = 60L
        const val WRITE_TIMEOUT_SECONDS = 60L
    }
}
