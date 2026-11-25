package zed.rainxch.search.data.utils

import zed.rainxch.core.domain.model.repository.DiscoveryPlatform

internal fun assetMatchesPlatform(
    name: String,
    platform: DiscoveryPlatform,
): Boolean {
    val lowercaseName = name.lowercase()
    return platform.installerExtensions().any { lowercaseName.endsWith(it) }
}

internal fun detectAvailablePlatforms(assetNames: List<String>): List<DiscoveryPlatform> =
    DiscoveryPlatform.entries.filter { platform ->
        platform != DiscoveryPlatform.All && assetNames.any { assetMatchesPlatform(it, platform) }
    }

private fun DiscoveryPlatform.installerExtensions(): List<String> =
    when (this) {
        DiscoveryPlatform.All -> DiscoveryPlatform.selectablePlatforms.flatMap { it.installerExtensions() }
        DiscoveryPlatform.Android -> listOf(".apk")
        DiscoveryPlatform.Macos -> listOf(".dmg", ".pkg")
        DiscoveryPlatform.Windows -> listOf(".exe", ".msi")
        DiscoveryPlatform.Linux -> listOf(".appimage", ".deb", ".rpm", ".pkg.tar.zst")
        DiscoveryPlatform.Ios -> listOf(".ipa")
    }
