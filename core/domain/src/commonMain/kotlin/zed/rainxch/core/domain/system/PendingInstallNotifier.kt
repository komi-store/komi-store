package zed.rainxch.core.domain.system

interface PendingInstallNotifier {
    fun notifyPending(
        packageName: String,
        repoOwner: String,
        repoName: String,
        appName: String,
        versionTag: String,
    )

    fun clearPending(packageName: String)
}
