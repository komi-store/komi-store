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

fun assetPlatformOf(assetName: String): DiscoveryPlatform? {
    val lower = assetName.lowercase()
    return when {
        lower.endsWith(".apk") -> {
            if (isAndroidApk(assetName)) DiscoveryPlatform.Android else DiscoveryPlatform.Linux
        }

        lower.endsWith(".ipa") -> {
            DiscoveryPlatform.Ios
        }

        lower.endsWith(".exe") || lower.endsWith(".msi") -> {
            DiscoveryPlatform.Windows
        }

        lower.endsWith(".dmg") || lower.endsWith(".pkg") -> {
            DiscoveryPlatform.Macos
        }

        lower.endsWith(".deb") ||
            lower.endsWith(".rpm") ||
            lower.endsWith(".appimage") ||
            lower.endsWith(".pkg.tar.zst") ||
            lower.endsWith(".snap") ||
            lower.endsWith(".flatpakref") -> {
            DiscoveryPlatform.Linux
        }

        else -> {
            null
        }
    }
}
