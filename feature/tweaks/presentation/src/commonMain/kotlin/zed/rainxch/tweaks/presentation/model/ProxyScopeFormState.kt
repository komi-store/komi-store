package zed.rainxch.tweaks.presentation.model

data class ProxyScopeFormState(
    val type: ProxyType = ProxyType.SYSTEM,
    val host: String = "",
    val port: String = "",
    val username: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isTestInProgress: Boolean = false,
    val isDraftDirty: Boolean = false,
)
