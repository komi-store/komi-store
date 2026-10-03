package zed.rainxch.githubstore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import zed.rainxch.core.data.services.LocalizationManager
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.domain.model.appearance.AppPersonality
import zed.rainxch.core.domain.model.appearance.MangaPaperId
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.RateLimitRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.domain.use_cases.SyncInstalledAppsUseCase
import zed.rainxch.githubstore.utils.STARTUP_PREFERENCE_TIMEOUT_MS
import kotlin.time.Duration.Companion.milliseconds

class MainViewModel(
    private val tweaksRepository: TweaksRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val userSessionRepository: UserSessionRepository,
    private val rateLimitRepository: RateLimitRepository,
    private val syncUseCase: SyncInstalledAppsUseCase,
    private val logger: KomiStoreLogger,
    private val localizationManager: LocalizationManager,
) : ViewModel() {
    private val _state = MutableStateFlow(MainState())
    val state = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                userSessionRepository.primeSession()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
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
                    Logger.w { "Appearance preference load timed out, releasing gate on defaults" }
                    _state.update { it.copy(isAppearanceLoaded = true) }
                }
            }
            try {
                combine(
                    tweaksRepository.getPersonality(),
                    tweaksRepository.getAccentId(),
                    tweaksRepository.getMangaPaper(),
                    tweaksRepository.getAmoledTheme(),
                    tweaksRepository.getIsDarkTheme(),
                ) { personality, accent, paper, amoled, isDark ->
                    Appearance(personality, accent, paper, amoled, isDark)
                }.combine(tweaksRepository.getAppLanguage()) { appearance, appLanguageTag ->
                    appearance.copy(appLanguageTag = appLanguageTag)
                }.collect { snapshot ->
                    localizationManager.setActiveLanguageTag(snapshot.appLanguageTag)
                    _state.update { it.withAppearance(snapshot).copy(isAppearanceLoaded = true) }
                    firstEmitted.complete(Unit)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "Appearance preference stream failed, releasing gate on defaults" }
                _state.update { it.copy(isAppearanceLoaded = true) }
                firstEmitted.complete(Unit)
            }
        }

        viewModelScope.launch {
            tweaksRepository.getScrollbarEnabled().collect { enabled ->
                _state.update { it.copy(isScrollbarEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            tweaksRepository.getContentWidth().collect { width ->
                _state.update { it.copy(contentWidth = width) }
            }
        }

        viewModelScope.launch {
            rateLimitRepository.rateLimitState.collect { rateLimitInfo ->
                _state.update { currentState ->
                    currentState.copy(rateLimitInfo = rateLimitInfo)
                }
            }
        }

        viewModelScope.launch {
            rateLimitRepository.rateLimitExhaustedEvent.collect { info ->
                _state.update { it.copy(showRateLimitDialog = true, rateLimitInfo = info) }
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

private data class Appearance(
    val personality: AppPersonality,
    val accent: AccentId,
    val mangaPaper: MangaPaperId,
    val isAmoledTheme: Boolean,
    val isDarkTheme: Boolean?,
    val appLanguageTag: String? = null,
)

private fun MainState.withAppearance(snapshot: Appearance): MainState =
    copy(
        personality = snapshot.personality,
        accent = snapshot.accent,
        mangaPaper = snapshot.mangaPaper,
        isAmoledTheme = snapshot.isAmoledTheme,
        isDarkTheme = snapshot.isDarkTheme,
        appLanguageTag = snapshot.appLanguageTag,
    )
