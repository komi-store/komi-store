package zed.rainxch.core.domain.model.installation
data class SystemPackageInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val isInstalled: Boolean,
    val signingFingerprint: String?,
    // Where the installed APK lives on disk, for reads that must hash the bytes themselves.
    // Null where the platform cannot say (desktop has no such file).
    val apkPath: String? = null,
)
