package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.repository.DiscoveryPlatform

private val alpineApkSignature =
    Regex(
        "(^|[^a-z0-9])(" +
            "linux|amd64|386|" +
            "armhf|armel|armv7l|armv6l|arm32|" +
            "riscv64|riscv|s390x|ppc64le|ppc64|powerpc|mips64|mips|loong64|loongarch|sparc64|sparc" +
            ")([^a-z0-9]|$)",
    )

fun isAndroidApk(assetName: String): Boolean {
    val lower = assetName.lowercase()
    if (!lower.endsWith(".apk")) return false
    return !alpineApkSignature.containsMatchIn(lower)
}

private val installerExtensions: Map<DiscoveryPlatform, List<String>> =
    mapOf(
        DiscoveryPlatform.Ios to listOf(".ipa"),
        DiscoveryPlatform.Windows to listOf(".exe", ".msi"),
        DiscoveryPlatform.Macos to listOf(".dmg", ".pkg"),
        DiscoveryPlatform.Linux to listOf(".deb", ".rpm", ".appimage", ".pkg.tar.zst", ".snap", ".flatpakref"),
    )

fun assetPlatformOf(assetName: String): DiscoveryPlatform? {
    val lower = assetName.lowercase()
    if (lower.endsWith(".apk")) {
        return if (isAndroidApk(assetName)) DiscoveryPlatform.Android else DiscoveryPlatform.Linux
    }
    return installerExtensions.entries
        .firstOrNull { (_, extensions) -> extensions.any { lower.endsWith(it) } }
        ?.key
}
