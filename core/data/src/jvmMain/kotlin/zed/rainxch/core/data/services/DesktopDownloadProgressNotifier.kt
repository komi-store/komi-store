package zed.rainxch.core.data.services

import zed.rainxch.core.domain.model.installation.DownloadProgress
import zed.rainxch.core.domain.system.DownloadProgressNotifier

class DesktopDownloadProgressNotifier : DownloadProgressNotifier {
    override fun notifyProgress(
        packageName: String,
        appName: String,
        versionTag: String,
        progress: DownloadProgress,
        paused: Boolean,
    ) = Unit

    override fun clearProgress(packageName: String) = Unit
}
