package zed.rainxch.details.presentation.model

import zed.rainxch.core.domain.model.repository.DiscoveryPlatform

data class AssetUi(
    val id: Long,
    val name: String,
    val size: Long,
    val downloadUrl: String,
    val platform: DiscoveryPlatform?,
    val variantTag: String?,
)
