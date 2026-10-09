package zed.rainxch.core.domain.model.installation

data class SystemPackageInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isInstalled: Boolean,
    val signingFingerprint: String?,
    // When the package manager last placed or replaced this package. The parked-file sweep needs
    // it to prove an install happened after the parked file was written; null where the platform
    // does not report it.
    val lastUpdateTime: Long? = null,
) {
    companion object {
        // What core/data's monitors write where Android's PackageInfo.versionName is null. Shared
        // so the landing proof can treat it as "no name reported" instead of a real build name
        // that arrived.
        const val UNKNOWN_VERSION_NAME: String = "unknown"
    }
}
