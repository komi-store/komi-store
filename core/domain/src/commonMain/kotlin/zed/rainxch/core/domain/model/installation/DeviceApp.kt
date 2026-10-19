package zed.rainxch.core.domain.model.installation

data class DeviceApp(
    val packageName: String,
    val appName: String,
    val versionName: String?,
    val versionCode: Long,
    val signingFingerprint: String?,
    val installerPackageName: String? = null,
    val isUpdatedSystemApp: Boolean = false,
)
