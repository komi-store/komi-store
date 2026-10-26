package zed.rainxch.core.domain.system

import zed.rainxch.core.domain.model.installation.DownloadProgress

interface DownloadProgressNotifier {
    fun notifyProgress(
        packageName: String,
        appName: String,
        versionTag: String,
        progress: DownloadProgress,
        paused: Boolean = false,
    )

    fun clearProgress(packageName: String)
}
