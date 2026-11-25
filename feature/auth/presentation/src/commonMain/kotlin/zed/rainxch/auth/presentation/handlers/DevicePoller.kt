package zed.rainxch.auth.presentation.handlers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import zed.rainxch.auth.domain.repository.AuthPath
import zed.rainxch.auth.domain.repository.AuthenticationRepository
import zed.rainxch.auth.domain.repository.DevicePollResult
import zed.rainxch.auth.domain.repository.PollOutcome
import zed.rainxch.auth.presentation.mapper.toAuthError
import zed.rainxch.auth.presentation.model.AuthLoginState
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import kotlin.time.Duration.Companion.milliseconds

// Polls GitHub for the device-flow token, on the session's AuthPath, until it arrives, fails or the countdown ends.
internal class DevicePoller(
    private val store: AuthStore,
    private val repository: AuthenticationRepository,
    private val savedState: AuthSavedState,
    private val logger: KomiStoreLogger,
    private val onSessionEnded: () -> Unit,
) {
    private var pollingJob: Job? = null
    private var pollingIntervalMs: Long = DEFAULT_POLL_INTERVAL_SEC * MILLIS_PER_SECOND
    private var lastPollStartedAtMs: Long = 0L

    var path: AuthPath = AuthPath.Backend

    fun start(
        deviceCode: String,
        resetInterval: Boolean = true,
    ) {
        pollingJob?.cancel()
        if (resetInterval) {
            val intervalSec =
                (store.current.loginState as? AuthLoginState.DevicePrompt)?.start?.intervalSec
                    ?: DEFAULT_POLL_INTERVAL_SEC
            pollingIntervalMs = intervalSec * MILLIS_PER_SECOND + POLL_INTERVAL_MARGIN_MS
        }
        pollingJob =
            store.launch {
                while (isActive) {
                    delay(pollingIntervalMs.milliseconds)
                    poll(deviceCode)
                }
            }
    }

    fun pollOnce(deviceCode: String) {
        lastPollStartedAtMs = System.currentTimeMillis()
        store.launch { poll(deviceCode) }
    }

    fun stop() {
        pollingJob?.cancel()
    }

    fun resumeIfStopped() {
        val loginState = store.current.loginState as? AuthLoginState.DevicePrompt ?: return
        if (store.current.isPolling || pollingJob?.isActive == true) return

        logger.debug("Resume poll: background loop was dead, restarting")
        start(loginState.start.deviceCode)
        pollOnce(loginState.start.deviceCode)
    }

    fun pollNow() {
        val loginState = store.current.loginState as? AuthLoginState.DevicePrompt ?: return
        val deviceCode = loginState.start.deviceCode

        val sinceLast = System.currentTimeMillis() - lastPollStartedAtMs
        if (sinceLast < MIN_MANUAL_POLL_SPACING_MS) {
            logger.debug("Manual poll suppressed, only ${sinceLast}ms since last poll")
            return
        }

        logger.debug("Manual poll requested (pollingJobActive=${pollingJob?.isActive})")
        start(deviceCode, resetInterval = false)
        pollOnce(deviceCode)
    }

    private suspend fun poll(deviceCode: String) {
        lastPollStartedAtMs = System.currentTimeMillis()
        store.update { it.copy(isPolling = true) }
        logger.debug(
            "Polling device token (code=${deviceCode.take(LOGGED_CODE_CHARS)}..., interval=${pollingIntervalMs}ms, path=$path)",
        )
        val outcome = fetchOutcome(deviceCode) ?: return
        followPath(outcome.path)
        when (val result = outcome.result) {
            is DevicePollResult.Success -> onSignedIn()
            DevicePollResult.Pending -> onPending()
            DevicePollResult.SlowDown -> onSlowDown()
            is DevicePollResult.Failed -> onFailed(result)
        }
    }

    // A token that won't save stops this poll only; the loop and the countdown carry on.
    private suspend fun fetchOutcome(deviceCode: String): PollOutcome? =
        try {
            withContext(Dispatchers.IO) {
                repository.pollDeviceTokenOnce(deviceCode, path)
            }
        } catch (e: LocalStoreException) {
            store.update { it.copy(isPolling = false) }
            logger.debug("Unexpected poll error: ${e.message}")
            null
        }

    private fun followPath(outcomePath: AuthPath) {
        if (outcomePath == path) return
        if (path == AuthPath.Backend && outcomePath == AuthPath.Direct) {
            logger.debug("Auth path escalated from $path to $outcomePath")
            path = outcomePath
            savedState.savePath(outcomePath)
        } else {
            logger.warn("Refusing invalid auth path transition $path → $outcomePath")
        }
    }

    private fun onSignedIn() {
        logger.debug("Poll success, token received, navigating")
        endSession()
        store.update { it.copy(loginState = AuthLoginState.LoggedIn, isPolling = false) }
        store.navigateToMain()
    }

    private fun onPending() {
        logger.debug("Poll result: still pending")
        store.update { it.copy(isPolling = false, pollIntervalSec = 0) }
    }

    private fun onSlowDown() {
        val bumped = (pollingIntervalMs + SLOW_DOWN_STEP_MS).coerceAtMost(MAX_POLL_INTERVAL_MS)
        val clamped = bumped == MAX_POLL_INTERVAL_MS && pollingIntervalMs >= MAX_POLL_INTERVAL_MS
        pollingIntervalMs = bumped
        logger.debug(
            if (clamped) {
                "Poll result: slow_down, interval already at cap ${MAX_POLL_INTERVAL_MS}ms"
            } else {
                "Poll result: slow_down, increased interval to ${pollingIntervalMs}ms"
            },
        )
        store.update {
            it.copy(
                isPolling = false,
                pollIntervalSec = (pollingIntervalMs / MILLIS_PER_SECOND).toInt(),
            )
        }
    }

    private fun onFailed(result: DevicePollResult.Failed) {
        logger.debug("Poll failed terminally: ${result.error.message}")
        endSession()
        store.update {
            it.copy(
                loginState = AuthLoginState.Error(result.error.toAuthError()),
                isPolling = false,
            )
        }
    }

    private fun endSession() {
        pollingJob?.cancel()
        onSessionEnded()
        savedState.clear()
    }

    private companion object {
        const val POLL_INTERVAL_MARGIN_MS = 1_000L
        const val SLOW_DOWN_STEP_MS = 5_000L
        const val MIN_MANUAL_POLL_SPACING_MS = 2_000L
        const val MAX_POLL_INTERVAL_MS = 15_000L
        const val LOGGED_CODE_CHARS = 8
    }
}
