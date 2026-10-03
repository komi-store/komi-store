package zed.rainxch.profile.presentation

import zed.rainxch.core.domain.model.account.UserProfile

sealed interface ProfileSession {
    data object Loading : ProfileSession

    data object SignedOut : ProfileSession

    data class SignedIn(val profile: UserProfile) : ProfileSession
}

data class ProfileState(
    val session: ProfileSession = ProfileSession.SignedOut,
    val isLogoutDialogVisible: Boolean = false,
) {
    val isUserLoggedIn: Boolean get() = session !is ProfileSession.SignedOut

    val userProfile: UserProfile? get() = (session as? ProfileSession.SignedIn)?.profile
}
