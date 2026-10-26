package zed.rainxch.core.data.local.db.entities

// A partial installed_apps row for Room's @Update: property names are column names, matched on packageName.
data class InstalledAppVersionInfo(
    val packageName: String,
    val isUpdateAvailable: Boolean,
    val latestVersion: String?,
    val latestAssetName: String?,
    val latestAssetUrl: String?,
    val latestAssetSize: Long?,
    val latestReleaseId: Long?,
    val latestAssetId: Long?,
    val latestAssetDigest: String?,
    val releaseNotes: String?,
    val lastCheckedAt: Long,
    val latestVersionName: String?,
    val latestVersionCode: Long?,
    val latestReleasePublishedAt: String?,
)
