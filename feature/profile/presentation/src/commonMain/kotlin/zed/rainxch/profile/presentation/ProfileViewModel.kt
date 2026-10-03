package zed.rainxch.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.model.account.SessionSnapshot
import zed.rainxch.core.domain.repository.UserSessionRepository

class ProfileViewModel(
    private val userSessionRepository: UserSessionRepository
) : ViewModel() {
    private var userProfileJob: Job? = null

    private val _state =
        MutableStateFlow(
            ProfileState(session = sessionFrom(userSessionRepository.lastKnownSession)),
        )

    val state = _state.asStateFlow()

    private val _events = Channel<ProfileEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        observeLoggedInStatus()
    }

    private fun setSession(session: ProfileSession) {
        _state.update { it.copy(session = session) }
    }

    private fun observeLoggedInStatus() {
        viewModelScope.launch {
            userSessionRepository.isUserLoggedIn()
                .collect { isLoggedIn ->
                    if (isLoggedIn) {
                        if (_state.value.session !is ProfileSession.SignedIn) {
                            setSession(ProfileSession.Loading)
                        }
                        loadUserProfile()
                    } else {
                        userProfileJob?.cancel()
                        userSessionRepository.clearLastKnownSession()
                        setSession(ProfileSession.SignedOut)
                    }
                }
        }
    }

    private fun loadUserProfile() {
        userProfileJob?.cancel()

        userProfileJob = viewModelScope.launch {
            userSessionRepository.getUser().collect { profile ->
                if (profile != null) {
                    setSession(ProfileSession.SignedIn(profile))
                }
            }
        }
    }

    fun onAction(action: ProfileAction) {
        when (action) {
            ProfileAction.OnLogoutClick -> {
                _state.update {
                    it.copy(
                        isLogoutDialogVisible = true,
                    )
                }
            }

            ProfileAction.OnLogoutConfirmClick -> {
                viewModelScope.launch {
                    runCatching {
                        userSessionRepository.logout()
                    }.onSuccess {
                        _state.update { it.copy(isLogoutDialogVisible = false) }
                        setSession(ProfileSession.SignedOut)
                        _events.send(ProfileEvent.OnLogoutSuccessful)
                    }.onFailure { error ->
                        if (error is CancellationException) throw error
                        _state.update { it.copy(isLogoutDialogVisible = false) }
                        error.message?.let {
                            _events.send(ProfileEvent.OnLogoutError(it))
                        }
                    }
                }
            }

            ProfileAction.OnLogoutDismiss -> {
                _state.update {
                    it.copy(
                        isLogoutDialogVisible = false,
                    )
                }
            }

            ProfileAction.OnLoginClick,
            ProfileAction.OnFavouriteReposClick,
            ProfileAction.OnStarredReposClick,
            is ProfileAction.OnRepositoriesClick,
            ProfileAction.OnRecentlyViewedClick,
            ProfileAction.OnWhatsNewClick,
            ProfileAction.OnAnnouncementsClick,
            ProfileAction.OnTweaksClick,
            ProfileAction.OnAboutClick -> Unit
        }
    }
}

private fun sessionFrom(snapshot: SessionSnapshot?): ProfileSession {
    if (snapshot == null || !snapshot.isLoggedIn) return ProfileSession.SignedOut
    val profile = snapshot.profile
    return if (profile != null) ProfileSession.SignedIn(profile) else ProfileSession.Loading
}
