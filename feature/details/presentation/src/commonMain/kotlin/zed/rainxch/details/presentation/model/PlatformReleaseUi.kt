package zed.rainxch.details.presentation.model

import zed.rainxch.core.domain.model.repository.DiscoveryPlatform

data class PlatformReleaseUi(
    val platform: DiscoveryPlatform,
    val release: ReleaseUi,
)
