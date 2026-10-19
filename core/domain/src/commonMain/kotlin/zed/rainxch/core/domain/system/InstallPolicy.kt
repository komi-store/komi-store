package zed.rainxch.core.domain.system

enum class InstallPolicy {
    AlwaysInstall,

    InstallWhileForeground,

    DeferUntilUserAction,
}
