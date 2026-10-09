package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.system.SystemArchitecture

object AssetSelector {

    private const val ARCH_EXACT = 3
    private const val ARCH_UNIVERSAL = 2
    private const val ARCH_FALLBACK = 1
    private const val ARCH_INCOMPATIBLE = 0

    private const val FLAVOR_STABLE = 3
    private const val FLAVOR_PRERELEASE = 2
    private const val FLAVOR_UNSTABLE = 1
    private const val FLAVOR_DEBUG = 0

    private const val DEVICE_VARIANT_MOBILE = 2
    private const val DEVICE_VARIANT_DEFAULT = 1
    private const val DEVICE_VARIANT_LEANBACK = 0

    private val debugRegex = boundary("debug")
    private val unstableRegex = boundary("nightly", "canary", "snapshot")
    private val preReleaseRegex =
        Regex("""(^|[^a-z0-9])(alpha|beta|rc|preview|pre)[-_.]?\d*([^a-z0-9]|$)""")
    private val mobileRegex = boundary("mobile")
    private val leanbackRegex = boundary("leanback")

    fun choose(
        assets: List<GithubAsset>,
        deviceArch: SystemArchitecture,
        extensionPriority: List<String>,
    ): GithubAsset? {
        if (assets.isEmpty()) return null
        return assets.sortedWith(comparator(deviceArch, extensionPriority)).firstOrNull()
    }

    fun comparator(
        deviceArch: SystemArchitecture,
        extensionPriority: List<String>,
    ): Comparator<GithubAsset> =
        compareByDescending<GithubAsset> { extensionRank(it.name, extensionPriority) }
            .thenByDescending { flavorRank(it.name) }
            .thenByDescending { archRank(it.name, deviceArch) }
            .thenByDescending { deviceVariantRank(it.name) }
            .thenByDescending { it.size }
            .thenBy { it.name.lowercase() }

    fun archRank(
        assetName: String,
        deviceArch: SystemArchitecture,
    ): Int =
        when (val match = AssetArchitectureMatcher.matchArchitecture(assetName)) {
            AssetArchitectureMatcher.Match.Universal -> ARCH_UNIVERSAL
            AssetArchitectureMatcher.Match.Foreign -> ARCH_INCOMPATIBLE
            is AssetArchitectureMatcher.Match.Known ->
                when {
                    match.arch == deviceArch -> ARCH_EXACT
                    isFallbackCompatible(match.arch, deviceArch) -> ARCH_FALLBACK
                    else -> ARCH_INCOMPATIBLE
                }
        }

    private fun isFallbackCompatible(
        assetArch: SystemArchitecture,
        deviceArch: SystemArchitecture,
    ): Boolean =
        when (deviceArch) {
            SystemArchitecture.AARCH64 -> assetArch == SystemArchitecture.ARM
            SystemArchitecture.X86_64 -> assetArch == SystemArchitecture.X86
            else -> false
        }

    fun flavorRank(assetName: String): Int {
        val name = assetName.lowercase()
        return when {
            debugRegex.containsMatchIn(name) -> FLAVOR_DEBUG
            unstableRegex.containsMatchIn(name) -> FLAVOR_UNSTABLE
            preReleaseRegex.containsMatchIn(name) -> FLAVOR_PRERELEASE
            else -> FLAVOR_STABLE
        }
    }

    fun deviceVariantRank(assetName: String): Int {
        val name = assetName.lowercase()
        if (!name.endsWith(".apk")) return DEVICE_VARIANT_DEFAULT
        return when {
            mobileRegex.containsMatchIn(name) -> DEVICE_VARIANT_MOBILE
            leanbackRegex.containsMatchIn(name) -> DEVICE_VARIANT_LEANBACK
            else -> DEVICE_VARIANT_DEFAULT
        }
    }

    private fun extensionRank(
        assetName: String,
        extensionPriority: List<String>,
    ): Int {
        if (extensionPriority.isEmpty()) return 0
        val name = assetName.lowercase()
        val idx = extensionPriority.indexOfFirst { name.endsWith(it) }
        return if (idx == -1) -1 else extensionPriority.size - idx
    }

    private fun boundary(vararg tokens: String): Regex =
        Regex("""(^|[^a-z0-9])(${tokens.joinToString("|")})([^a-z0-9]|$)""")
}
