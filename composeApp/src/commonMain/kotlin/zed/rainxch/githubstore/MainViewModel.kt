package zed.rainxch.githubstore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.RateLimitRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.domain.usecases.SyncInstalledAppsUseCase
import zed.rainxch.githubstore.app.components.toUi
import zed.rainxch.githubstore.utils.STARTUP_PREFERENCE_TIMEOUT_MS
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel(
    private val appearancePreferences: AppearancePreferences,
    private val installedAppsRepository: InstalledAppsRepository,
    private val userSessionRepository: UserSessionRepository,
    private val rateLimitRepository: RateLimitRepository,
    private val syncUseCase: SyncInstalledAppsUseCase,
    private val logger: KomiStoreLogger,
) : ViewModel() {
    private val _state = MutableStateFlow(MainState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                userSessionRepository.primeSession()
            } catch (e: LocalStoreException) {
                logger.warn("Session prime failed; continuing without it: ${e.message}")
            }
            _state.update {
                it.copy(
                    signedInAvatarUrl = userSessionRepository.lastKnownSession?.profile?.imageUrl,
                )
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            userSessionRepository
                .isUserLoggedIn()
                .collect { isLoggedIn ->
                    val avatarUrl =
                        if (isLoggedIn) {
                            userSessionRepository.lastKnownSession
                                ?.takeIf { it.isLoggedIn }
                                ?.profile
                                ?.imageUrl
                        } else {
                            null
                        }

                    _state.update {
                        it.copy(
                            isLoggedIn = isLoggedIn,
                            signedInAvatarUrl = avatarUrl,
                        )
                    }

                    if (isLoggedIn) {
                        rateLimitRepository.clear()
                    }

                    if (isLoggedIn && avatarUrl == null) {
                        launch {
                            val fetched = userSessionRepository.getUser().first()?.imageUrl
                            if (fetched != null &&
                                _state.value.isLoggedIn &&
                                userSessionRepository.lastKnownSession?.isLoggedIn == true
                            ) {
                                _state.update { it.copy(signedInAvatarUrl = fetched) }
                            }
                        }
                    }
                }
        }

        viewModelScope.launch {
            val firstEmitted = CompletableDeferred<Unit>()
            launch {
                if (
                    withTimeoutOrNull(STARTUP_PREFERENCE_TIMEOUT_MS.milliseconds) {
                        firstEmitted.await()
                    } == null
                ) {
                    logger.warn("Appearance preference load timed out, releasing gate on defaults")
                    _state.update { it.copy(isAppearanceLoaded = true) }
                }
            }
            appearancePreferences.appearance().collect { snapshot ->
                _state.update { it.withAppearance(snapshot).copy(isAppearanceLoaded = true) }
                firstEmitted.complete(Unit)
            }
        }

        viewModelScope.launch {
            appearancePreferences.scrollbarEnabled().collect { enabled ->
                _state.update { it.copy(isScrollbarEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            appearancePreferences.contentWidth().collect { width ->
                _state.update { it.copy(contentWidth = width) }
            }
        }

        viewModelScope.launch {
            rateLimitRepository.rateLimitState.collect { rateLimitInfo ->
                _state.update { currentState ->
                    currentState.copy(rateLimit = rateLimitInfo?.toUi())
                }
            }
        }

        viewModelScope.launch {
            rateLimitRepository.rateLimitExhaustedEvent.collect { info ->
                _state.update { it.copy(showRateLimitDialog = true, rateLimit = info.toUi()) }
            }
        }

        viewModelScope.launch {
            userSessionRepository.sessionExpiredEvent.collect {
                _state.update {
                    it.copy(showSessionExpiredDialog = true, signedInAvatarUrl = null)
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            syncUseCase().onSuccess {
                installedAppsRepository.checkAllForUpdates()
            }
        }
    }

    fun onAction(action: MainAction) {
        when (action) {
            MainAction.DismissRateLimitDialog -> {
                _state.update { it.copy(showRateLimitDialog = false) }
            }

            MainAction.DismissSessionExpiredDialog -> {
                _state.update { it.copy(showSessionExpiredDialog = false) }
            }
        }
    }
}

private fun MainState.withAppearance(snapshot: Appearance): MainState =
    copy(
        personality = snapshot.personality,
        accent = snapshot.accent,
        mangaPaper = snapshot.mangaPaper,
        isAmoledTheme = snapshot.isAmoledTheme,
        isDarkTheme = snapshot.isDarkTheme,
        appLanguageTag = snapshot.appLanguageTag,
    )
