package zed.rainxch.details.presentation.model

import kotlinx.collections.immutable.ImmutableList
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform

data class PlatformHandoffUi(
    val platform: DiscoveryPlatform,
    val tagName: String,
    val publishedAt: String,
    val assets: ImmutableList<AssetUi>,
)
