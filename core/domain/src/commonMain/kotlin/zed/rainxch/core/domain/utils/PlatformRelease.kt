package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform

data class PlatformRelease(
    val platform: DiscoveryPlatform,
    val release: GithubRelease,
)
