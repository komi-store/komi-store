package zed.rainxch.core.data.local.db.entities

// The pending-install columns of an installed_apps row while a parked download waits for the system installer.
data class InstalledAppAwaitingInstall(
    val packageName: String,
    val pendingInstallFilePath: String,
    val pendingInstallVersion: String?,
    val pendingInstallAssetName: String?,
    val pendingInstallReleaseId: Long?,
    val pendingInstallAssetId: Long?,
    val pendingInstallAssetDigest: String?,
    val pendingInstallReleasePublishedAt: String?,
    val isPendingInstall: Boolean = true,
)
