package zed.rainxch.core.domain.model.installation

data class InstalledBuild(
    val tag: String,
    val releaseId: Long? = null,
    val assetId: Long? = null,
    val assetDigest: String? = null,
    val releasePublishedAt: String? = null,
    // Unknown must be null, never "": "" never equals a real asset name.
    val assetName: String?,
    val assetUrl: String?,
    val versionName: String,
    val versionCode: Long,
    val signingFingerprint: String?,
)
