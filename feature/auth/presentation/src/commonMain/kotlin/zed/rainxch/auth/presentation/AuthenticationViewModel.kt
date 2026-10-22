package zed.rainxch.auth.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import zed.rainxch.auth.domain.repository.AuthPath
import zed.rainxch.auth.domain.repository.AuthenticationRepository
import zed.rainxch.auth.domain.repository.DevicePollResult
import zed.rainxch.auth.presentation.mapper.toAuthError
import zed.rainxch.auth.presentation.mapper.toPatError
import zed.rainxch.auth.presentation.mapper.toUi
import zed.rainxch.auth.presentation.model.AuthError
import zed.rainxch.auth.presentation.model.AuthLoginState
import zed.rainxch.auth.presentation.model.GithubDeviceStartUi
import zed.rainxch.auth.presentation.model.PatError
import zed.rainxch.core.domain.helpers.BrowserHelper
import zed.rainxch.core.domain.helpers.ClipboardHelper
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.githubstore.core.presentation.res.*
import java.net.URLEncoder
import kotlin.time.Duration.Companion.milliseconds

class AuthenticationViewModel(
    private val authenticationRepository: AuthenticationRepository,
    private val browserHelper: BrowserHelper,
    private val clipboardHelper: ClipboardHelper,
    private val scope: CoroutineScope,
    private val logger: KomiStoreLogger,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private var hasLoadedInitialData = false
    private var countdownJob: Job? = null
    private var pollingJob: Job? = null
    private var patSubmissionJob: Job? = null
    private var webAuthWatchdogJob: Job? = null
    private var pollingIntervalMs: Long = DEFAULT_POLL_INTERVAL_SEC * 1000L
    private var authPath: AuthPath = AuthPath.Backend

    private var lastPollStartedAtMs: Long = 0L

    private val _state: MutableStateFlow<AuthenticationState> =
        MutableStateFlow(AuthenticationState())

    private val _events = Channel<AuthenticationEvents>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    scope.launch {
                        authenticationRepository.accessTokenFlow.collect { token ->
                            _state.update {
                                it.copy(
                                    loginState =
                                        if (token.isNullOrEmpty()) {
                                            AuthLoginState.LoggedOut
                                        } else {
                                            _events.trySend(AuthenticationEvents.OnNavigateToMain)
                                            AuthLoginState.LoggedIn
                                        },
                                )
                            }
                        }
                    }

                    restoreFromSavedState()
                    observeAuthDeepLinks()
                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = AuthenticationState(),
            )

    fun onAction(action: AuthenticationAction) {
        when (action) {
            is AuthenticationAction.StartLogin -> {
                startLogin()
            }

            is AuthenticationAction.CopyCode -> {
                copyCode(action.start)
            }

            is AuthenticationAction.OpenGitHub -> {
                openGitHub(action.start)
            }

            AuthenticationAction.MarkLoggedIn -> {
                _state.update { it.copy(loginState = AuthLoginState.LoggedIn) }
            }

            AuthenticationAction.MarkLoggedOut -> {
                _state.update { it.copy(loginState = AuthLoginState.LoggedOut) }
            }

            is AuthenticationAction.OnInfo -> {
                _state.update {
                    it.copy(
                        info = action.message,
                    )
                }
            }

            AuthenticationAction.SkipLogin -> {
                _events.trySend(AuthenticationEvents.OnNavigateToMain)
            }

            AuthenticationAction.PollNow -> {
                forcePollNow()
            }

            AuthenticationAction.OnResumed -> {
                tryPollIfReady()
            }

            AuthenticationAction.OpenPatSheet -> {
                _state.update {
                    it.copy(
                        isPatSheetVisible = true,
                        patInput = "",
                        patError = null,
                    )
                }
            }

            AuthenticationAction.DismissPatSheet -> {
                patSubmissionJob?.cancel()
                patSubmissionJob = null
                _state.update {
                    it.copy(
                        isPatSheetVisible = false,
                        patInput = "",
                        patError = null,
                        isPatSubmitting = false,
                    )
                }
            }

            is AuthenticationAction.OnPatInputChanged -> {
                _state.update {
                    it.copy(patInput = action.input, patError = null)
                }
            }

            AuthenticationAction.SubmitPat -> {
                submitPat()
            }

            AuthenticationAction.OpenPatSettingsPage -> {
                openPatSettingsPage()
            }

            AuthenticationAction.StartWebAuth -> {
                startWebAuth()
            }

            is AuthenticationAction.ConsumeAuthHandoff -> {
                consumeAuthHandoff(action.handoffId, action.state)
            }

            is AuthenticationAction.ConsumeAuthError -> {
                consumeAuthError(action.reason, action.state)
            }

            AuthenticationAction.OpenAdvancedAuth -> {
                _state.update { it.copy(isAdvancedAuthVisible = true) }
            }

            AuthenticationAction.DismissAdvancedAuth -> {
                _state.update { it.copy(isAdvancedAuthVisible = false) }
            }
        }
    }

    private fun submitPat() {
        if (_state.value.isPatSubmitting) return
        val input = _state.value.patInput.trim()
        if (input.isEmpty()) {
            _state.update { it.copy(patError = PatError.Empty) }
            return
        }

        patSubmissionJob?.cancel()
        patSubmissionJob =
            viewModelScope
                .launch {
                    _state.update { it.copy(isPatSubmitting = true, patError = null) }
                    val result = authenticationRepository.signInWithPat(input)
                    result
                        .onSuccess {
                            _state.update {
                                it.copy(
                                    isPatSheetVisible = false,
                                    patInput = "",
                                    patError = null,
                                    isPatSubmitting = false,
                                    loginState = AuthLoginState.LoggedIn,
                                )
                            }
                            _events.trySend(AuthenticationEvents.OnNavigateToMain)
                        }.onFailure { t ->
                            logger.debug("PAT sign-in failed: ${t.message}")
                            _state.update {
                                it.copy(
                                    isPatSubmitting = false,
                                    patError = t.toPatError(),
                                )
                            }
                        }
                }.also { job ->
                    job.invokeOnCompletion { patSubmissionJob = null }
                }
    }

    private fun observeAuthDeepLinks() {
        viewModelScope.launch {
            AuthDeepLinkBus.events.collect { event ->
                when (event) {
                    is AuthDeepLinkEvent.Handoff -> {
                        consumeAuthHandoff(event.handoffId, event.state)
                        AuthDeepLinkBus.resetReplay()
                    }

                    is AuthDeepLinkEvent.Error -> {
                        consumeAuthError(event.reason, event.state)
                        AuthDeepLinkBus.resetReplay()
                    }
                }
            }
        }
    }

    private fun startWebAuth() {
        if (_state.value.isWebAuthInFlight) return
        webAuthWatchdogJob?.cancel()
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isWebAuthInFlight = true,
                    loginState = AuthLoginState.Pending,
                )
            }
            val result = authenticationRepository.registerWebAuth()
            result
                .onSuccess { registration ->
                    savedStateHandle[KEY_WEB_AUTH_STATE] = registration.state
                    browserHelper.openUrl(registration.authUrl) { error ->
                        logger.warn("Failed to open auth URL: $error")
                        webAuthWatchdogJob?.cancel()
                        savedStateHandle.remove<String>(KEY_WEB_AUTH_STATE)
                        _state.update {
                            it.copy(
                                isWebAuthInFlight = false,
                                loginState = AuthLoginState.Error(AuthError.BrowserUnavailable),
                            )
                        }
                    }
                    _state.update { it.copy(isWebAuthInFlight = false) }
                    startWebAuthWatchdog()
                }.onFailure { error ->
                    val rootClass = error::class.simpleName ?: "Throwable"
                    val rootMsg = error.message ?: "<no message>"
                    logger.error(
                        "registerWebAuth failed. class=$rootClass msg=$rootMsg",
                        error,
                    )
                    _state.update {
                        it.copy(
                            isWebAuthInFlight = false,
                            loginState = AuthLoginState.Error(error.toAuthError()),
                        )
                    }
                }
        }
    }

    private fun consumeAuthHandoff(
        handoffId: String,
        state: String,
    ) {
        val expected = savedStateHandle.get<String>(KEY_WEB_AUTH_STATE)

        if (expected == null) {
            logger.debug("Ignoring web-auth handoff with no pending session")
            return
        }
        if (expected != state) {
            logger.warn(
                "Web-auth handoff state mismatch. expected=${expected.take(8)} got=${state.take(8)}",
            )
            _state.update { it.copy(loginState = AuthLoginState.Error(AuthError.Unknown)) }
            return
        }
        webAuthWatchdogJob?.cancel()
        savedStateHandle.remove<String>(KEY_WEB_AUTH_STATE)

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isWebAuthInFlight = true,
                    loginState = AuthLoginState.Pending,
                )
            }
            val result = authenticationRepository.exchangeWebAuthHandoff(handoffId)
            result
                .onSuccess {
                    _state.update {
                        it.copy(
                            isWebAuthInFlight = false,
                            loginState = AuthLoginState.LoggedIn,
                        )
                    }
                    _events.trySend(AuthenticationEvents.OnNavigateToMain)
                }.onFailure { error ->
                    val rootClass = error::class.simpleName ?: "Throwable"
                    val rootMsg = error.message ?: "<no message>"
                    logger.error(
                        "exchangeWebAuthHandoff failed. class=$rootClass msg=$rootMsg",
                        error,
                    )
                    _state.update {
                        it.copy(
                            isWebAuthInFlight = false,
                            loginState = AuthLoginState.Error(error.toAuthError()),
                        )
                    }
                }
        }
    }

    private fun consumeAuthError(
        reason: String,
        state: String,
    ) {
        val expected = savedStateHandle.get<String>(KEY_WEB_AUTH_STATE)
        if (expected == null) {
            logger.debug("Ignoring web-auth error with no pending session")
            return
        }
        if (expected != state) {
            logger.warn(
                "Web-auth error state mismatch (ignored). " +
                    "expected=${expected.take(8)} got=${state.take(8)}",
            )
            return
        }
        webAuthWatchdogJob?.cancel()
        savedStateHandle.remove<String>(KEY_WEB_AUTH_STATE)
        logger.warn("Web-auth flow returned error: $reason")
        _state.update {
            it.copy(
                isWebAuthInFlight = false,
                loginState = AuthLoginState.Error(AuthError.Unknown),
            )
        }
    }

    private fun startWebAuthWatchdog() {
        webAuthWatchdogJob?.cancel()
        webAuthWatchdogJob =
            viewModelScope.launch {
                delay(WEB_AUTH_TIMEOUT_MS.milliseconds)
                if (savedStateHandle.get<String>(KEY_WEB_AUTH_STATE) == null) return@launch
                if (_state.value.loginState !is AuthLoginState.Pending) return@launch
                savedStateHandle.remove<String>(KEY_WEB_AUTH_STATE)
                logger.warn("Web-auth timed out waiting for callback")
                _state.update {
                    it.copy(
                        isWebAuthInFlight = false,
                        loginState = AuthLoginState.Error(AuthError.Unknown),
                    )
                }
            }
    }

    private fun openPatSettingsPage() {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            try {
                browserHelper.openUrl(PAT_SETTINGS_URL)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.debug("Failed to open PAT settings page: ${e.message}")
            }
        }
    }

    private fun devicePrompt(
        start: GithubDeviceStartUi,
        remainingSeconds: Int,
    ): AuthLoginState.DevicePrompt {
        val total = start.expiresInSec.coerceAtLeast(1)
        val minutes = remainingSeconds / 60
        val seconds = remainingSeconds % 60
        return AuthLoginState.DevicePrompt(
            start = start,
            remainingSeconds = remainingSeconds,
            progressFraction = (remainingSeconds.toFloat() / total).coerceIn(0f, 1f),
            formattedTimer = "%02d:%02d".format(minutes, seconds),
            isUrgent = remainingSeconds in 1 until 60,
        )
    }

    private fun startCountdown(remainingSeconds: Int) {
        countdownJob?.cancel()
        countdownJob =
            viewModelScope.launch {
                var remaining = remainingSeconds
                while (remaining > 0) {
                    _state.update { currentState ->
                        val loginState = currentState.loginState
                        if (loginState is AuthLoginState.DevicePrompt) {
                            currentState.copy(
                                loginState = devicePrompt(loginState.start, remaining),
                            )
                        } else {
                            return@launch
                        }
                    }
                    delay(1000L.milliseconds)
                    remaining--
                }

                pollingJob?.cancel()
                clearSavedState()
                _state.update { it.copy(loginState = AuthLoginState.Error(AuthError.CodeExpired)) }
            }
    }

    private fun tryPollIfReady() {
        val loginState = _state.value.loginState
        if (loginState !is AuthLoginState.DevicePrompt) return
        if (_state.value.isPolling) return

        if (pollingJob?.isActive == true) return

        logger.debug("Resume poll: background loop was dead, restarting")
        startPolling(loginState.start.deviceCode)
        pollOnce(loginState.start.deviceCode)
    }

    private fun forcePollNow() {
        val loginState = _state.value.loginState
        if (loginState !is AuthLoginState.DevicePrompt) return
        val deviceCode = loginState.start.deviceCode

        val sinceLast = System.currentTimeMillis() - lastPollStartedAtMs
        if (sinceLast < MIN_MANUAL_POLL_SPACING_MS) {
            logger.debug("Manual poll suppressed — only ${sinceLast}ms since last poll")
            return
        }

        logger.debug("Manual poll requested (pollingJobActive=${pollingJob?.isActive})")

        startPolling(deviceCode, resetInterval = false)
        pollOnce(deviceCode)
    }

    private fun startLogin() {
        viewModelScope.launch {
            try {
                val flowStart =
                    withContext(Dispatchers.IO) {
                        authenticationRepository.startDeviceFlow()
                    }

                val start = flowStart.start
                authPath = flowStart.path
                logger.debug("Device flow started via path=$authPath")

                val startUi = start.toUi()

                withContext(Dispatchers.Main.immediate) {
                    _state.update {
                        it.copy(
                            loginState = devicePrompt(startUi, start.expiresInSec),
                            copied = false,
                        )
                    }

                    saveToSavedState(start.deviceCode, startUi, authPath)
                    startCountdown(start.expiresInSec)
                    startPolling(start.deviceCode)

                    try {
                        clipboardHelper.copy(
                            label = getString(Res.string.enter_code_on_github),
                            text = start.userCode.filter { it.isLetterOrDigit() },
                        )
                        _state.update { it.copy(copied = true) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        logger.debug("Failed to copy to clipboard: ${e.message}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                countdownJob?.cancel()
                pollingJob?.cancel()
                clearSavedState()
                val rootCause = generateSequence<Throwable>(t) { it.cause }.lastOrNull() ?: t
                val rootClass = rootCause::class.simpleName ?: "Throwable"
                val rootMsg = rootCause.message ?: "<no message>"
                logger.error(
                    "startDeviceFlow failed. topClass=${t::class.simpleName} topMsg=${t.message} | rootClass=$rootClass rootMsg=$rootMsg",
                    t,
                )
                withContext(Dispatchers.Main.immediate) {
                    _state.update { it.copy(loginState = AuthLoginState.Error(t.toAuthError())) }
                }
            }
        }
    }

    private fun startPolling(
        deviceCode: String,
        resetInterval: Boolean = true,
    ) {
        pollingJob?.cancel()
        if (resetInterval) {
            val loginState = _state.value.loginState
            val intervalSec =
                (loginState as? AuthLoginState.DevicePrompt)?.start?.intervalSec
                    ?: DEFAULT_POLL_INTERVAL_SEC

            pollingIntervalMs = (intervalSec * 1000).toLong() + 1000L
        }
        pollingJob =
            viewModelScope.launch {
                while (isActive) {
                    delay(pollingIntervalMs.milliseconds)
                    doPoll(deviceCode)
                }
            }
    }

    private fun pollOnce(deviceCode: String) {
        lastPollStartedAtMs = System.currentTimeMillis()
        viewModelScope.launch {
            doPoll(deviceCode)
        }
    }

    private suspend fun doPoll(deviceCode: String) {
        lastPollStartedAtMs = System.currentTimeMillis()
        _state.update { it.copy(isPolling = true) }
        try {
            logger.debug("Polling device token (code=${deviceCode.take(8)}..., interval=${pollingIntervalMs}ms, path=$authPath)")
            val outcome =
                withContext(Dispatchers.IO) {
                    authenticationRepository.pollDeviceTokenOnce(deviceCode, authPath)
                }

            if (outcome.path != authPath) {
                val isLegalEscalation =
                    authPath == AuthPath.Backend && outcome.path == AuthPath.Direct
                if (isLegalEscalation) {
                    logger.debug("Auth path escalated from $authPath to ${outcome.path}")
                    authPath = outcome.path
                    savedStateHandle[KEY_AUTH_PATH] = authPath.name
                } else {
                    logger.warn(
                        "Refusing invalid auth path transition $authPath → ${outcome.path}",
                    )
                }
            }

            when (val result = outcome.result) {
                is DevicePollResult.Success -> {
                    logger.debug("Poll success — token received, navigating")
                    pollingJob?.cancel()
                    countdownJob?.cancel()
                    clearSavedState()
                    _state.update {
                        it.copy(loginState = AuthLoginState.LoggedIn, isPolling = false)
                    }
                    _events.trySend(AuthenticationEvents.OnNavigateToMain)
                }

                is DevicePollResult.Pending -> {
                    logger.debug("Poll result: still pending")
                    _state.update { it.copy(isPolling = false, pollIntervalSec = 0) }
                }

                is DevicePollResult.SlowDown -> {
                    val bumped = (pollingIntervalMs + 5000L).coerceAtMost(MAX_POLL_INTERVAL_MS)
                    val clamped =
                        bumped == MAX_POLL_INTERVAL_MS && pollingIntervalMs >= MAX_POLL_INTERVAL_MS
                    pollingIntervalMs = bumped
                    logger.debug(
                        if (clamped) {
                            "Poll result: slow_down — interval already at cap ${MAX_POLL_INTERVAL_MS}ms"
                        } else {
                            "Poll result: slow_down — increased interval to ${pollingIntervalMs}ms"
                        },
                    )
                    _state.update {
                        it.copy(
                            isPolling = false,
                            pollIntervalSec = (pollingIntervalMs / 1000).toInt(),
                        )
                    }
                }

                is DevicePollResult.Failed -> {
                    logger.debug("Poll failed terminally: ${result.error.message}")
                    pollingJob?.cancel()
                    countdownJob?.cancel()
                    clearSavedState()
                    _state.update {
                        it.copy(
                            loginState = AuthLoginState.Error(result.error.toAuthError()),
                            isPolling = false,
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            _state.update { it.copy(isPolling = false) }
            logger.debug("Unexpected poll error: ${t.message}")
        }
    }

    private fun saveToSavedState(
        deviceCode: String,
        startUi: GithubDeviceStartUi,
        path: AuthPath,
    ) {
        savedStateHandle[KEY_DEVICE_CODE] = deviceCode
        savedStateHandle[KEY_USER_CODE] = startUi.userCode
        savedStateHandle[KEY_VERIFICATION_URI] = startUi.verificationUri
        savedStateHandle[KEY_VERIFICATION_URI_COMPLETE] = startUi.verificationUriComplete
        savedStateHandle[KEY_INTERVAL_SEC] = startUi.intervalSec
        savedStateHandle[KEY_EXPIRES_IN_SEC] = startUi.expiresInSec
        savedStateHandle[KEY_START_TIME_MILLIS] = System.currentTimeMillis()
        savedStateHandle[KEY_AUTH_PATH] = path.name
    }

    private fun clearSavedState() {
        SAVED_STATE_KEYS.forEach { savedStateHandle.remove<Any>(it) }
    }

    private fun restoreFromSavedState() {
        val deviceCode = savedStateHandle.get<String>(KEY_DEVICE_CODE) ?: return
        val userCode = savedStateHandle.get<String>(KEY_USER_CODE) ?: return
        val verificationUri = savedStateHandle.get<String>(KEY_VERIFICATION_URI) ?: return
        val expiresInSec = savedStateHandle.get<Int>(KEY_EXPIRES_IN_SEC) ?: return
        val intervalSec = savedStateHandle.get<Int>(KEY_INTERVAL_SEC) ?: 5
        val startTimeMillis = savedStateHandle.get<Long>(KEY_START_TIME_MILLIS) ?: return
        val restoredPath =
            savedStateHandle.get<String>(KEY_AUTH_PATH)?.let {
                runCatching { AuthPath.valueOf(it) }.getOrNull()
            } ?: run {
                clearSavedState()
                return
            }

        val elapsedSec = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()
        val remainingSec = expiresInSec - elapsedSec

        if (remainingSec <= 0) {
            clearSavedState()
            return
        }

        authPath = restoredPath
        logger.debug("Restored auth session on path=$authPath")

        val startUi =
            GithubDeviceStartUi(
                deviceCode = deviceCode,
                userCode = userCode,
                verificationUri = verificationUri,
                verificationUriComplete = savedStateHandle.get<String>(KEY_VERIFICATION_URI_COMPLETE),
                intervalSec = intervalSec,
                expiresInSec = expiresInSec,
            )

        _state.update {
            it.copy(loginState = devicePrompt(startUi, remainingSec))
        }

        startCountdown(remainingSec)
        startPolling(deviceCode)
        pollOnce(deviceCode)
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
        pollingJob?.cancel()
    }

    private fun openGitHub(start: GithubDeviceStartUi) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            try {
                val url =
                    start.verificationUriComplete
                        ?: buildPrefilledUrl(start.verificationUri, start.userCode)
                browserHelper.openUrl(url)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.debug("Failed to open browser: ${e.message}")
            }
        }
    }

    private fun buildPrefilledUrl(
        verificationUri: String,
        userCode: String,
    ): String {
        val separator = if ('?' in verificationUri) "&" else "?"
        return verificationUri + separator + "user_code=" + URLEncoder.encode(userCode, "UTF-8")
    }

    private fun copyCode(start: GithubDeviceStartUi) {
        viewModelScope.launch(Dispatchers.Main.immediate) {
            try {
                clipboardHelper.copy(
                    label = "GitHub Code",
                    text = start.userCode.filter { it.isLetterOrDigit() },
                )

                _state.update {
                    val currentRemaining =
                        (it.loginState as? AuthLoginState.DevicePrompt)?.remainingSeconds ?: 0

                    it.copy(
                        loginState = devicePrompt(start, currentRemaining),
                        copied = true,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.debug("Failed to copy to clipboard: ${e.message}")
                _state.update {
                    val currentRemaining =
                        (it.loginState as? AuthLoginState.DevicePrompt)?.remainingSeconds ?: 0

                    it.copy(
                        loginState = devicePrompt(start, currentRemaining),
                        copied = false,
                    )
                }
            }
        }
    }

    companion object {
        private const val KEY_DEVICE_CODE = "auth_device_code"
        private const val KEY_USER_CODE = "auth_user_code"
        private const val KEY_VERIFICATION_URI = "auth_verification_uri"
        private const val KEY_VERIFICATION_URI_COMPLETE = "auth_verification_uri_complete"
        private const val KEY_INTERVAL_SEC = "auth_interval_sec"
        private const val KEY_EXPIRES_IN_SEC = "auth_expires_in_sec"
        private const val KEY_START_TIME_MILLIS = "auth_start_time_millis"
        private const val KEY_AUTH_PATH = "auth_path"
        private const val KEY_WEB_AUTH_STATE = "auth_web_state"

        // 11 min. Sits 60s past the server-side OAuth ceiling (600s for both
        // the CF Worker's VERIFIER_TTL_SECONDS and the backend's STATE_TTL
        // on `oauth_ephemeral`). The watchdog must outlive the longest
        // legitimate slow sign-in the server will still accept; otherwise it
        // false-cuts a flow the server would have completed. Only after
        // both server ceilings have passed do we treat the absence of a
        // deep-link callback as a genuine delivery failure (the Windows
        // browser-handler case from GitHub-Store#730) and surface a retry.
        private const val WEB_AUTH_TIMEOUT_MS = 660_000L
        private const val DEFAULT_POLL_INTERVAL_SEC = 5

        private const val MIN_MANUAL_POLL_SPACING_MS = 2_000L

        private const val MAX_POLL_INTERVAL_MS = 15_000L

        private const val PAT_SETTINGS_URL = "https://github.com/settings/tokens/new"

        private val SAVED_STATE_KEYS =
            listOf(
                KEY_DEVICE_CODE,
                KEY_USER_CODE,
                KEY_VERIFICATION_URI,
                KEY_VERIFICATION_URI_COMPLETE,
                KEY_INTERVAL_SEC,
                KEY_EXPIRES_IN_SEC,
                KEY_START_TIME_MILLIS,
                KEY_AUTH_PATH,
                KEY_WEB_AUTH_STATE,
            )
    }
}
