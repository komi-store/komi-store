package zed.rainxch.auth.presentation

import zed.rainxch.auth.presentation.model.GithubDeviceStartUi

sealed interface AuthenticationAction {
    sealed interface DeviceFlow : AuthenticationAction

    sealed interface WebAuth : AuthenticationAction

    sealed interface Pat : AuthenticationAction

    data object StartLogin : DeviceFlow

    data class CopyCode(
        val start: GithubDeviceStartUi,
    ) : DeviceFlow

    data class OpenGitHub(
        val start: GithubDeviceStartUi,
    ) : DeviceFlow

    data object MarkLoggedOut : AuthenticationAction

    data object MarkLoggedIn : AuthenticationAction

    data class OnInfo(
        val message: String,
    ) : AuthenticationAction

    data object SkipLogin : AuthenticationAction

    data object PollNow : DeviceFlow

    data object OnResumed : DeviceFlow

    data object OpenPatSheet : Pat

    data object DismissPatSheet : Pat

    data class OnPatInputChanged(
        val input: String,
    ) : Pat

    data object SubmitPat : Pat

    data object OpenPatSettingsPage : Pat

    data object StartWebAuth : WebAuth

    data class ConsumeAuthHandoff(
        val handoffId: String,
        val state: String,
    ) : WebAuth

    data class ConsumeAuthError(
        val reason: String,
        val state: String,
    ) : WebAuth

    data object DismissAdvancedAuth : AuthenticationAction

    data object OpenAdvancedAuth : AuthenticationAction
}
