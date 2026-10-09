package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import zed.rainxch.core.data.data_source.TokenStore
import zed.rainxch.core.data.download.AssetSourceGoneException
import zed.rainxch.core.data.network.GithubAssetAuth
import zed.rainxch.core.data.network.ProxyManager
import zed.rainxch.core.data.network.resolveAndroidSystemProxy
import zed.rainxch.core.domain.model.installation.DownloadProgress
import zed.rainxch.core.domain.model.settings.ProxyConfig
import zed.rainxch.core.domain.model.settings.ProxyScope
import zed.rainxch.core.domain.network.Downloader
import java.io.File
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
) : Downloader {
    private val activeDownloads = ConcurrentHashMap<String, Call>()
    private val idsByName = ConcurrentHashMap<String, MutableSet<String>>()

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

            val tempFile = File(dir, "$safeName.part-$downloadId")
            if (tempFile.exists()) tempFile.delete()

            Logger.d { "Starting download: $url (id=$downloadId)" }

            val request =
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
                    }.build()
            val call = client.newCall(request)

            activeDownloads[downloadId] = call
            idsByName.computeIfAbsent(safeName) { ConcurrentHashMap.newKeySet() }.add(downloadId)

            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        if (AssetSourceGoneException.isGoneCode(response.code)) {
                            throw AssetSourceGoneException(response.code)
                        }
                        throw kotlinx.io.IOException("Unexpected code ${response.code}")
                    }

                    val body = response.body
                    val contentLength = body.contentLength()
                    val total = if (contentLength > 0) contentLength else null

                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(8192)
                            var downloaded: Long = 0
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloaded += bytesRead
                                val percent =
                                    if (total != null) ((downloaded * 100L) / total).toInt() else null
                                emit(DownloadProgress(downloaded, total, percent))
                            }
                        }
                    }

                    if (!tempFile.exists() || tempFile.length() <= 0) {
                        throw IllegalStateException(
                            "Download produced empty file: ${tempFile.absolutePath} (contentLength=$contentLength)",
                        )
                    }

                    moveAtomic(tempFile, destination)

                    Logger.d { "Download complete: ${destination.absolutePath}" }
                    val finalDownloaded = destination.length()
                    val finalPercent =
                        if (total != null) ((finalDownloaded * 100L) / total).toInt() else 100
                    emit(DownloadProgress(finalDownloaded, total, finalPercent))
                }
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {

                tempFile.delete()
                throw e
            } catch (e: Exception) {
                tempFile.delete()
                Logger.e(e) { "Download failed" }
                throw e
            } finally {
                activeDownloads.remove(downloadId)
                idsByName.computeIfPresent(safeName) { _, set ->
                    set.remove(downloadId)
                    if (set.isEmpty()) null else set
                }
            }
        }.flowOn(Dispatchers.IO)

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

            if (file.exists()) {
                Logger.d { "Deleting existing file before download: ${file.absolutePath}" }
                file.delete()
            }

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

            var cancelled = false
            for (id in ids) {
                val call = activeDownloads.remove(id) ?: continue
                if (!call.isCanceled()) {
                    call.cancel()
                    cancelled = true
                }
            }

            cancelled
        }
}
