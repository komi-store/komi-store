package zed.rainxch.core.data.services

import zed.rainxch.core.domain.system.DownloadProgressNotifier

class DesktopDownloadProgressNotifier : DownloadProgressNotifier {
    override fun notifyProgress(
        packageName: String,
        appName: String,
        versionTag: String,
        percent: Int?,
        bytesDownloaded: Long,
        totalBytes: Long?,
        paused: Boolean,
    ) = Unit

    override fun clearProgress(packageName: String) = Unit
}
