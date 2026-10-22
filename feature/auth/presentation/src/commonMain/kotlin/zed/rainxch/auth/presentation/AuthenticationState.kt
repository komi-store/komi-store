package zed.rainxch.auth.presentation

import zed.rainxch.auth.presentation.model.AuthLoginState
import zed.rainxch.auth.presentation.model.PatError

data class AuthenticationState(
    val loginState: AuthLoginState = AuthLoginState.LoggedOut,
    val copied: Boolean = false,
    val info: String? = null,
    val isPolling: Boolean = false,
    val pollIntervalSec: Int = 0,
    val isPatSheetVisible: Boolean = false,
    val patInput: String = "",
    val patError: PatError? = null,
    val isPatSubmitting: Boolean = false,
    val isAdvancedAuthVisible: Boolean = false,
    val isWebAuthInFlight: Boolean = false,
)
