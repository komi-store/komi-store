package zed.rainxch.core.domain.model.system

enum class SystemArchitecture {
    X86_64,
    AARCH64,
    X86,
    ARM,
    UNKNOWN,
    ;

    companion object {
        fun fromString(arch: String): SystemArchitecture {
            val normalized = arch.lowercase().trim()
            return when (normalized) {
                in listOf("x86_64", "amd64", "x64") -> X86_64
                in listOf("aarch64", "arm64", "arm64-v8a", "armv8", "armv8a", "armv8l") -> AARCH64
                in listOf("x86", "i386", "i686") -> X86
                in listOf("arm", "armv7l", "armv7", "armv7a", "armeabi", "armeabi-v7a") -> ARM
                else -> UNKNOWN
            }
        }
    }
}
