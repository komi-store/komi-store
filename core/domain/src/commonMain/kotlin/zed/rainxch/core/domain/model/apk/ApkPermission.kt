package zed.rainxch.core.domain.model.apk

data class ApkPermission(
    val name: String,
    val displayName: String,
    val description: String?,
    val protectionLevel: ProtectionLevel,
    val granted: Boolean?,
)
