package zed.rainxch.core.data.download

import co.touchlab.kermit.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import zed.rainxch.core.data.network.MirrorRewriter
import zed.rainxch.core.data.network.ProxyManager
import zed.rainxch.core.domain.model.installation.DownloadProgress
import zed.rainxch.core.domain.model.mirror.TrafficKind
import zed.rainxch.core.domain.network.AssetIdentity
import zed.rainxch.core.domain.network.Downloader
import zed.rainxch.core.domain.system.MultiSourceDownloader

@OptIn(ExperimentalCoroutinesApi::class)
class MultiSourceDownloaderImpl(
    private val downloader: Downloader,
) : MultiSourceDownloader {
    override fun download(
        githubUrl: String,
        suggestedFileName: String?,
        identity: AssetIdentity?,
    ): Flow<DownloadProgress> {
        val mirrorUrl =
            ProxyManager
                .currentMirror()
                ?.takeIf { TrafficKind.RELEASE_ASSET in it.trafficKinds }
                ?.let { MirrorRewriter.applyTemplate(it.template, githubUrl) }
        return if (mirrorUrl == null) {
            downloader.download(githubUrl, suggestedFileName, identity = identity)
        } else {
            mirrorFirstWithFallback(mirrorUrl, githubUrl, suggestedFileName, identity)
        }
    }

    private fun mirrorFirstWithFallback(
        mirrorUrl: String,
        directUrl: String,
        suggestedFileName: String?,
        identity: AssetIdentity?,
    ): Flow<DownloadProgress> =
        channelFlow {
            val mirrorFailure =
                catchingStepFailures {
                    downloader
                        .download(mirrorUrl, suggestedFileName, identity = identity)
                        .collect { send(it) }
                }.exceptionOrNull() ?: return@channelFlow
            Logger.w(mirrorFailure) { "Mirror download failed, falling back to direct." }
            downloader
                .download(directUrl, suggestedFileName, bypassMirror = true, identity = identity)
                .collect { send(it) }
        }.flowOn(Dispatchers.IO)
}
