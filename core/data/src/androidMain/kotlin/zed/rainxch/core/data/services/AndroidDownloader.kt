package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
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
import zed.rainxch.core.data.data_source.TokenStore
import zed.rainxch.core.data.download.FinishedFileReuse
import zed.rainxch.core.data.download.OrphanPolicy
import zed.rainxch.core.data.download.OrphanVerdict
import zed.rainxch.core.data.download.PartialDisposal
import zed.rainxch.core.data.download.PartialIdentity
import zed.rainxch.core.data.download.PartialNaming
import zed.rainxch.core.data.download.PartialOwnership
import zed.rainxch.core.data.download.PartialReuse
import zed.rainxch.core.data.download.PartialRetry
import zed.rainxch.core.data.download.ContentRange
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
import java.io.File
import java.io.FileOutputStream
import java.net.Authenticator
import java.net.InetSocketAddress
import java.net.PasswordAuthentication
import java.net.Proxy
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class AndroidDownloader(
    private val files: FileLocationsProvider,
    private val tokenStore: TokenStore,
    private val digestVerifier: DigestVerifier,
) : Downloader {
    private val activeDownloads = ConcurrentHashMap<String, Call>()
    private val idsByName = ConcurrentHashMap<String, MutableSet<String>>()

    private val cancelRequested = ConcurrentHashMap.newKeySet<String>()

    private val nameLocks = ConcurrentHashMap<String, Mutex>()

    private fun buildClient(): OkHttpClient {
        Authenticator.setDefault(null)

        return OkHttpClient
            .Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .apply {
                when (val config = ProxyManager.currentConfig(ProxyScope.DOWNLOAD)) {
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
            val client = buildClient()

            val dirPath = files.appDownloadsDir()
            val dir = File(dirPath)
            if (!dir.exists()) dir.mkdirs()

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

            val downloadId = UUID.randomUUID().toString()

            val destination = File(dir, safeName)
            val partFile = File(dir, PartialNaming.part(safeName))
            val metaFile = File(dir, PartialNaming.meta(safeName))

            Logger.d { "Starting download: $url (id=$downloadId, partial=${partFile.name})" }

            val lock = nameLocks.computeIfAbsent(safeName) { Mutex() }
            lock.withLock {
                idsByName.computeIfAbsent(safeName) { ConcurrentHashMap.newKeySet() }.add(downloadId)
                try {
                    var attemptsMade = 0
                    var restartsMade = 0
                    while (true) {
                        try {
                            downloadAttempt(
                                client = client,
                                url = url,
                                partFile = partFile,
                                metaFile = metaFile,
                                destination = destination,
                                downloadId = downloadId,
                                identity = identity,
                                emit = { emit(it) },
                            )
                            break
                        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                            throw e
                        } catch (e: PartialRestart) {
                            if (restartsMade >= MAX_RESTARTS) {
                                throw kotlinx.io.IOException(
                                    "The bytes on disk could not be resumed for $safeName",
                                )
                            }
                            restartsMade++
                            Logger.d {
                                "The partial cannot be resumed (server refused the range, or served a span we did not ask for); restarting from zero"
                            }
                        } catch (e: PartialAborted) {
                            throw e.cause ?: e
                        } catch (e: PartialTerminal) {
                            throw e
                        } catch (e: Exception) {
                            coroutineContext.ensureActive()
                            if (!PartialRetry.hasAttemptsLeft(attemptsMade + 1)) {
                                Logger.e(e) {
                                    "Download failed after ${PartialRetry.MAX_ATTEMPTS} attempts"
                                }
                                throw e
                            }
                            val backoff = PartialRetry.backoffMillis(attemptsMade)
                            Logger.w(e) {
                                "Download attempt ${attemptsMade + 1} failed; retrying in ${backoff}ms"
                            }
                            delay(backoff)
                            attemptsMade++
                        }
                    }
                } finally {
                    activeDownloads.remove(downloadId)
                    cancelRequested.remove(downloadId)
                    idsByName.computeIfPresent(safeName) { _, set ->
                        set.remove(downloadId)
                        if (set.isEmpty()) null else set
                    }
                }
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun downloadAttempt(
        client: OkHttpClient,
        url: String,
        partFile: File,
        metaFile: File,
        destination: File,
        downloadId: String,
        identity: AssetIdentity?,
        emit: suspend (DownloadProgress) -> Unit,
    ) {
        val stored = readIdentity(metaFile)
        val reusable =
            PartialOwnership.decideReuse(
                partExists = partFile.exists(),
                partLength = if (partFile.exists()) partFile.length() else 0L,
                meta = stored,
                incoming = identity,
            ) == PartialReuse.REUSE

        if (!reusable) {
            partFile.delete()
            metaFile.delete()
            if (partFile.exists()) {
                val truncated =
                    runCatching { FileOutputStream(partFile, false).close() }
                        .isSuccess && partFile.length() == 0L
                if (!truncated) {
                    throw kotlinx.io.IOException(
                        "Cannot discard a partial that failed its ownership check: ${partFile.absolutePath}",
                    )
                }
            }
        }
        val resumeFrom = if (partFile.exists()) partFile.length() else 0L

        val expectedDigest =
            FinishedFileReuse.digestToVerify(
                destinationExists = destination.exists(),
                destinationLength = destination.length(),
                expectedDigest = identity?.digest,
                expectedSize = identity?.size ?: -1L,
            )
        if (expectedDigest != null &&
            digestVerifier.verify(destination.absolutePath, expectedDigest) == null
        ) {
            partFile.delete()
            metaFile.delete()
            val length = destination.length()
            Logger.d { "Reusing completed file: ${destination.absolutePath} ($length bytes)" }
            emit(DownloadProgress(length, length, 100))
            return
        }

        val request = buildRequest(url, RangeRequest.headerValue(resumeFrom))
        val call = client.newCall(request)
        activeDownloads[downloadId] = call
        if (cancelRequested.remove(downloadId)) call.cancel()
        val cancellationHandle =
            coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { cause ->
                if (cause is kotlin.coroutines.cancellation.CancellationException) call.cancel()
            }
        try {
            call.execute().use { response ->
                val contentRange = ContentRange.parse(response.header("Content-Range"))
                val decision = RangeRequest.decide(response.code, resumeFrom, contentRange)
                if (decision == RangeDecision.RESTART) {
                    partFile.delete()
                    metaFile.delete()
                    throw PartialRestart()
                }
                if (decision == RangeDecision.UNEXPECTED) {
                    if (PartialDisposal.shouldDiscardPartial(response.code, explicitDiscard = false)) {
                        partFile.delete()
                        metaFile.delete()
                        throw PartialTerminal("Unexpected code ${response.code}")
                    }
                    throw kotlinx.io.IOException("Unexpected code ${response.code}")
                }

                val total = totalBytes(response, resumeFrom, contentRange)
                if (decision == RangeDecision.APPEND &&
                    resumeFrom > 0L &&
                    stored != null &&
                    stored.size > 0L &&
                    total != null &&
                    stored.size != total
                ) {
                    partFile.delete()
                    metaFile.delete()
                    throw PartialRestart()
                }

                writeIdentity(
                    metaFile,
                    identity?.let {
                        PartialIdentity(
                            assetId = it.assetId,
                            digest = it.digest,
                            size = if (it.size > 0L) it.size else (total ?: resumeFrom),
                        )
                    } ?: PartialIdentity(assetId = 0L, digest = null, size = total ?: resumeFrom),
                )

                val append = decision == RangeDecision.APPEND
                if (!append) partFile.delete()
                val base = if (append) resumeFrom else 0L

                response.body.byteStream().use { input ->
                    FileOutputStream(partFile, append).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var downloaded = base
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            val percent =
                                total?.takeIf { it > 0L }?.let {
                                    ((downloaded * 100L) / it).toInt()
                                }
                            emit(DownloadProgress(downloaded, total, percent))
                        }
                    }
                }

                val finalLength = partFile.length()
                if (finalLength <= 0L) {
                    throw IllegalStateException(
                        "Download produced empty file: ${partFile.absolutePath}",
                    )
                }
                if (total != null && total > 0L && finalLength != total) {
                    throw kotlinx.io.IOException(
                        "Incomplete download: got $finalLength of $total bytes",
                    )
                }

                moveAtomic(partFile, destination)
                metaFile.delete()

                Logger.d { "Download complete: ${destination.absolutePath}" }
                val finalPercent =
                    total?.takeIf { it > 0L }?.let { ((finalLength * 100L) / it).toInt() } ?: 100
                emit(DownloadProgress(finalLength, total, finalPercent))
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            if (call.isCanceled()) {
                coroutineContext.ensureActive()
                throw PartialAborted(e)
            }
            throw e
        } finally {
            cancellationHandle?.dispose()
        }
    }

    private suspend fun buildRequest(url: String, rangeHeader: String?): Request =
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
        } catch (e: Exception) {
            Logger.w(e) { "Unreadable sidecar ${metaFile.name}; treating the partial as unattributable" }
            null
        }

    private fun writeIdentity(metaFile: File, identity: PartialIdentity) {
        try {
            metaFile.writeText(identity.serialize())
        } catch (e: Exception) {
            Logger.w(e) { "Failed to persist sidecar ${metaFile.name}" }
        }
    }

    private fun totalBytes(response: Response, resumeFrom: Long, contentRange: ContentRange?): Long? {
        val declared = contentRange?.total
        if (declared != null && declared > 0L) return declared
        val contentLength = response.body.contentLength()
        if (contentLength > 0L) return if (response.code == 206) resumeFrom + contentLength else contentLength
        return null
    }

    private suspend fun githubToken(): String? =
        try {
            tokenStore.currentToken()?.accessToken?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private fun moveAtomic(source: File, target: File) {
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
                (legacyPartNames.mapNotNull { PartialNaming.assetNameOfPart(it) } +
                    metaNames.mapNotNull { PartialNaming.assetNameOfMeta(it) }).toSet()

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

    private fun applyVerdict(verdict: OrphanVerdict, part: File, meta: File): Int {
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

            val file = File(files.appDownloadsDir(), safeName)

            Logger.d { "saveToFile downloading file..." }
            download(url, suggestedFileName).collect { }

            file.absolutePath
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

    private fun deletePartialPair(dir: File, safeName: String): Boolean {
        val partRemoved = File(dir, PartialNaming.part(safeName)).delete()
        val metaRemoved = File(dir, PartialNaming.meta(safeName)).delete()
        return partRemoved || metaRemoved
    }

    private class PartialRestart : Exception()

    private class PartialTerminal(message: String) : Exception(message)

    private class PartialAborted(cause: Throwable) : Exception(cause)

    private companion object {
        private const val BUFFER_SIZE = 8 * 1024
        private const val MAX_RESTARTS = 2
    }
}
