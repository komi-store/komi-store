package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.system.Platform

data class PlatformRelease(
    val platform: DiscoveryPlatform,
    val release: GithubRelease,
)

fun Platform.toDiscoveryPlatform(): DiscoveryPlatform =
    when (this) {
        Platform.ANDROID -> DiscoveryPlatform.Android
        Platform.WINDOWS -> DiscoveryPlatform.Windows
        Platform.MACOS -> DiscoveryPlatform.Macos
        Platform.LINUX -> DiscoveryPlatform.Linux
    }

fun GithubRelease.platforms(): Set<DiscoveryPlatform> =
    assets.mapNotNullTo(LinkedHashSet()) { assetPlatformOf(it.name) }

fun newestReleasePerPlatform(
    releases: List<GithubRelease>,
    releasePlatforms: (GithubRelease) -> Set<DiscoveryPlatform> = { it.platforms() },
    devicePlatform: DiscoveryPlatform,
): List<PlatformRelease> {
    val newest = LinkedHashMap<DiscoveryPlatform, GithubRelease>()
    releases.forEach { release ->
        releasePlatforms(release).forEach { platform -> newest.putIfAbsent(platform, release) }
    }
    return newest.map { (platform, release) -> PlatformRelease(platform, release) }
        .sortedWith(
            compareBy<PlatformRelease> { it.platform != devicePlatform }
                .thenBy { DiscoveryPlatform.selectablePlatforms.indexOf(it.platform) },
        )
}
