package zed.rainxch.auth.presentation.model

sealed class AuthLoginState {
    data object LoggedOut : AuthLoginState()

    data class DevicePrompt(
        val start: GithubDeviceStartUi,
        val remainingSeconds: Int = 0,
        val progressFraction: Float = 0f,
        val formattedTimer: String = "",
        val isUrgent: Boolean = false,
    ) : AuthLoginState()

    data object Pending : AuthLoginState()

    data object LoggedIn : AuthLoginState()

    data class Error(
        val error: AuthError,
    ) : AuthLoginState()
}
