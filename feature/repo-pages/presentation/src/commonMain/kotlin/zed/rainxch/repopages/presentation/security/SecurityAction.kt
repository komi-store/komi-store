package zed.rainxch.repopages.presentation.security

sealed interface SecurityAction {
    data object OnBackClick : SecurityAction

    data object OnRetry : SecurityAction
}
