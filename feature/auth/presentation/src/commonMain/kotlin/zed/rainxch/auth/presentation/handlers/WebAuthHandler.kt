package zed.rainxch.auth.presentation.handlers

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import zed.rainxch.auth.domain.repository.AuthenticationRepository
import zed.rainxch.auth.domain.repository.WebAuthRegistration
import zed.rainxch.auth.presentation.AuthDeepLinkBus
import zed.rainxch.auth.presentation.AuthDeepLinkEvent
import zed.rainxch.auth.presentation.AuthenticationAction
import zed.rainxch.auth.presentation.mapper.toAuthError
import zed.rainxch.auth.presentation.model.AuthError
import zed.rainxch.auth.presentation.model.AuthLoginState
import zed.rainxch.core.domain.helpers.BrowserHelper
import zed.rainxch.core.domain.logging.KomiStoreLogger
import kotlin.time.Duration.Companion.milliseconds

// The browser sign-in: register a PKCE session, open GitHub, then take the handoff the deep link brings back.
internal class WebAuthHandler(
    private val store: AuthStore,
    private val repository: AuthenticationRepository,
    private val browserHelper: BrowserHelper,
    private val savedState: AuthSavedState,
    private val logger: KomiStoreLogger,
) {
    private var watchdogJob: Job? = null

    fun onAction(action: AuthenticationAction.WebAuth) {
        when (action) {
            AuthenticationAction.StartWebAuth -> start()
            is AuthenticationAction.ConsumeAuthHandoff -> consumeHandoff(action.handoffId, action.state)
            is AuthenticationAction.ConsumeAuthError -> consumeError(action.reason, action.state)
        }
    }

    fun observeDeepLinks() {
        store.launch {
            AuthDeepLinkBus.events.collect { event ->
                when (event) {
                    is AuthDeepLinkEvent.Handoff -> consumeHandoff(event.handoffId, event.state)
                    is AuthDeepLinkEvent.Error -> consumeError(event.reason, event.state)
                }
                AuthDeepLinkBus.resetReplay()
            }
        }
    }

    private fun start() {
        if (store.current.isWebAuthInFlight) return
        watchdogJob?.cancel()
        store.launch {
            store.update {
                it.copy(
                    isWebAuthInFlight = true,
                    loginState = AuthLoginState.Pending,
                )
            }
            repository
                .registerWebAuth()
                .onSuccess { registration -> openAuthPage(registration) }
                .onFailure { error -> fail("registerWebAuth", error) }
        }
    }

    private fun openAuthPage(registration: WebAuthRegistration) {
        savedState.beginWebAuth(registration.state)
        browserHelper.openUrl(registration.authUrl) { error ->
            logger.warn("Failed to open auth URL: $error")
            watchdogJob?.cancel()
            savedState.endWebAuth()
            store.update {
                it.copy(
                    isWebAuthInFlight = false,
                    loginState = AuthLoginState.Error(AuthError.BrowserUnavailable),
                )
            }
        }
        store.update { it.copy(isWebAuthInFlight = false) }
        startWatchdog()
    }

    private fun consumeHandoff(
        handoffId: String,
        state: String,
    ) {
        val expected = savedState.pendingWebAuthState
        if (expected == null) {
            logger.debug("Ignoring web-auth handoff with no pending session")
            return
        }
        if (expected != state) {
            logger.warn(
                "Web-auth handoff state mismatch. expected=${expected.take(LOGGED_STATE_CHARS)} " +
                    "got=${state.take(LOGGED_STATE_CHARS)}",
            )
            store.update { it.copy(loginState = AuthLoginState.Error(AuthError.Unknown)) }
            return
        }
        endPendingSession()

        store.launch {
            store.update {
                it.copy(
                    isWebAuthInFlight = true,
                    loginState = AuthLoginState.Pending,
                )
            }
            repository
                .exchangeWebAuthHandoff(handoffId)
                .onSuccess {
                    store.update {
                        it.copy(
                            isWebAuthInFlight = false,
                            loginState = AuthLoginState.LoggedIn,
                        )
                    }
                    store.navigateToMain()
                }.onFailure { error -> fail("exchangeWebAuthHandoff", error) }
        }
    }

    private fun consumeError(
        reason: String,
        state: String,
    ) {
        val expected = savedState.pendingWebAuthState
        if (expected == null) {
            logger.debug("Ignoring web-auth error with no pending session")
            return
        }
        if (expected != state) {
            logger.warn(
                "Web-auth error state mismatch (ignored). " +
                    "expected=${expected.take(LOGGED_STATE_CHARS)} got=${state.take(LOGGED_STATE_CHARS)}",
            )
            return
        }
        endPendingSession()
        logger.warn("Web-auth flow returned error: $reason")
        store.update {
            it.copy(
                isWebAuthInFlight = false,
                loginState = AuthLoginState.Error(AuthError.Unknown),
            )
        }
    }

    private fun endPendingSession() {
        watchdogJob?.cancel()
        savedState.endWebAuth()
    }

    private fun fail(
        operation: String,
        error: Throwable,
    ) {
        val rootClass = error::class.simpleName ?: "Throwable"
        val rootMsg = error.message ?: "<no message>"
        logger.error("$operation failed. class=$rootClass msg=$rootMsg", error)
        store.update {
            it.copy(
                isWebAuthInFlight = false,
                loginState = AuthLoginState.Error(error.toAuthError()),
            )
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob =
            store.launch {
                delay(WEB_AUTH_TIMEOUT_MS.milliseconds)
                if (savedState.pendingWebAuthState == null) return@launch
                if (store.current.loginState !is AuthLoginState.Pending) return@launch
                savedState.endWebAuth()
                logger.warn("Web-auth timed out waiting for callback")
                store.update {
                    it.copy(
                        isWebAuthInFlight = false,
                        loginState = AuthLoginState.Error(AuthError.Unknown),
                    )
                }
            }
    }

    private companion object {
        // 60 s past the server's 600 s OAuth ceiling, so the watchdog never cuts off a sign-in the server would still finish.
        const val WEB_AUTH_TIMEOUT_MS = 660_000L
        const val LOGGED_STATE_CHARS = 8
    }
}
