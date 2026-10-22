package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.system.SystemArchitecture

object AssetArchitectureMatcher {
    sealed interface Match {
        data class Known(
            val arch: SystemArchitecture,
        ) : Match

        data object Universal : Match

        data object Foreign : Match
    }

    private val universalRegex = boundary("universal", "noarch", "all-arch", "fat")
    private val x86_64Regex = boundary("x86[_-]64", "amd64", "win64", "x64")
    private val arm64Regex =
        boundary("aarch64", "arm64", "arm64-v8a", "armv8a", "armv8l", "armv8", "arm-v8", "v8a")
    private val x86Regex = boundary("i386", "i586", "i686", "ia32", "win32", "x86", "386")
    private val armRegex =
        boundary(
            "armeabi-v7a",
            "armeabi",
            "armv7l",
            "armv7a",
            "armv7",
            "armv6l",
            "armv6",
            "armhf",
            "armel",
            "arm-v7",
            "arm32",
            "v7a",
            "arm",
        )
    private val foreignRegex =
        boundary(
            "riscv64",
            "riscv",
            "ppc64le",
            "ppc64",
            "powerpc",
            "ppc",
            "s390x",
            "loong64",
            "loongarch",
            "mips64",
            "mips",
            "sparc64",
            "sparc",
        )

    fun matchArchitecture(assetName: String): Match {
        val name = assetName.lowercase().replace('_', '-')
        return when {
            universalRegex.containsMatchIn(name) -> Match.Universal
            x86_64Regex.containsMatchIn(name) -> Match.Known(SystemArchitecture.X86_64)
            arm64Regex.containsMatchIn(name) -> Match.Known(SystemArchitecture.AARCH64)
            x86Regex.containsMatchIn(name) -> Match.Known(SystemArchitecture.X86)
            armRegex.containsMatchIn(name) -> Match.Known(SystemArchitecture.ARM)
            foreignRegex.containsMatchIn(name) -> Match.Foreign
            else -> Match.Universal
        }
    }

    fun detectArchitecture(assetName: String): SystemArchitecture? = (matchArchitecture(assetName) as? Match.Known)?.arch

    fun isCompatible(
        assetName: String,
        systemArch: SystemArchitecture,
    ): Boolean =
        when (val match = matchArchitecture(assetName)) {
            Match.Universal -> true
            Match.Foreign -> false
            is Match.Known -> isKnownCompatible(match.arch, systemArch)
        }

    private fun isKnownCompatible(
        assetArch: SystemArchitecture,
        systemArch: SystemArchitecture,
    ): Boolean =
        when (systemArch) {
            SystemArchitecture.X86_64 -> {
                assetArch == SystemArchitecture.X86_64 || assetArch == SystemArchitecture.X86
            }

            SystemArchitecture.AARCH64 -> {
                assetArch == SystemArchitecture.AARCH64 || assetArch == SystemArchitecture.ARM
            }

            SystemArchitecture.X86 -> {
                assetArch == SystemArchitecture.X86
            }

            SystemArchitecture.ARM -> {
                assetArch == SystemArchitecture.ARM
            }

            SystemArchitecture.UNKNOWN -> {
                true
            }
        }

    fun isExactMatch(
        assetName: String,
        systemArch: SystemArchitecture,
    ): Boolean = detectArchitecture(assetName) == systemArch

    private fun boundary(vararg tokens: String): Regex = Regex("(^|[^a-z0-9])(${tokens.joinToString("|")})([^a-z0-9]|$)")
}
