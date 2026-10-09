package zed.rainxch.core.domain.system

interface DownloadProgressNotifier {

    fun notifyProgress(
        packageName: String,
        appName: String,
        versionTag: String,
        percent: Int?,
        bytesDownloaded: Long,
        totalBytes: Long?,
        paused: Boolean = false,
    )

    fun clearProgress(packageName: String)
}
