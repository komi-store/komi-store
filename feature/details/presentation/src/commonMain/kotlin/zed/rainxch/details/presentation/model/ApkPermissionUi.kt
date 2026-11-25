package zed.rainxch.details.presentation.model

import zed.rainxch.core.domain.model.apk.ProtectionLevel

data class ApkPermissionUi(
    val name: String,
    val displayName: String,
    val description: String?,
    val protectionLevel: ProtectionLevel,
)
