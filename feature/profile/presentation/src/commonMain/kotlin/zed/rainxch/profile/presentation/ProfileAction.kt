package zed.rainxch.profile.presentation

sealed interface ProfileAction {
    data object OnLogoutClick : ProfileAction

    data object OnLogoutConfirmClick : ProfileAction

    data object OnStarredReposClick : ProfileAction

    data object OnFavouriteReposClick : ProfileAction

    data class OnRepositoriesClick(
        val username: String,
    ) : ProfileAction

    data object OnLogoutDismiss : ProfileAction

    data object OnLoginClick : ProfileAction

    data object OnRecentlyViewedClick : ProfileAction

    data object OnWhatsNewClick : ProfileAction

    data object OnAnnouncementsClick : ProfileAction

    data object OnTweaksClick : ProfileAction

    data object OnAboutClick : ProfileAction
}
