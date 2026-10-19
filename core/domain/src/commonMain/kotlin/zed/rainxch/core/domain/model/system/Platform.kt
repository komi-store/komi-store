package zed.rainxch.core.domain.model.system

enum class Platform {
    ANDROID,
    WINDOWS,
    MACOS,
    LINUX,
    ;

    val isMobile: Boolean get() = this == ANDROID
    val isDesktop: Boolean get() = !isMobile
}
