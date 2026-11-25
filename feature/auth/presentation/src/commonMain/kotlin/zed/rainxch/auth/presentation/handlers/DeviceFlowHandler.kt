package zed.rainxch.auth.presentation.handlers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.getString
import zed.rainxch.auth.domain.repository.AuthException
import zed.rainxch.auth.domain.repository.AuthenticationRepository
import zed.rainxch.auth.domain.repository.DeviceFlowStart
import zed.rainxch.auth.presentation.AuthenticationAction
import zed.rainxch.auth.presentation.mapper.toAuthError
import zed.rainxch.auth.presentation.mapper.toDevicePrompt
import zed.rainxch.auth.presentation.mapper.toUi
import zed.rainxch.auth.presentation.model.AuthError
import zed.rainxch.auth.presentation.model.AuthLoginState
import zed.rainxch.auth.presentation.model.GithubDeviceStartUi
import zed.rainxch.core.domain.helpers.BrowserHelper
import zed.rainxch.core.domain.helpers.ClipboardHelper
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.SystemPackagesException
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.enter_code_on_github
import java.net.URLEncoder
import kotlin.time.Duration.Companion.seconds

// The device-code sign-in: starting it, the expiry countdown, copying the code and opening GitHub.
internal class DeviceFlowHandler(
    private val store: AuthStore,
    private val repository: AuthenticationRepository,
    private val browserHelper: BrowserHelper,
    private val clipboardHelper: ClipboardHelper,
    private val savedState: AuthSavedState,
    private val logger: KomiStoreLogger,
) {
    private var countdownJob: Job? = null

    private val poller =
        DevicePoller(
            store = store,
            repository = repository,
            savedState = savedState,
            logger = logger,
            onSessionEnded = { countdownJob?.cancel() },
        )

    fun onAction(action: AuthenticationAction.DeviceFlow) {
        when (action) {
            AuthenticationAction.StartLogin -> startLogin()
            is AuthenticationAction.CopyCode -> copyCode(action.start)
            is AuthenticationAction.OpenGitHub -> openGitHub(action.start)
            AuthenticationAction.PollNow -> poller.pollNow()
            AuthenticationAction.OnResumed -> poller.resumeIfStopped()
        }
    }

    fun restore() {
        val session = savedState.restoreDeviceSession() ?: return
        poller.path = session.path
        logger.debug("Restored auth session on path=${session.path}")

        store.update { it.copy(loginState = session.start.toDevicePrompt(session.remainingSeconds)) }

        startCountdown(session.remainingSeconds)
        poller.start(session.start.deviceCode)
        poller.pollOnce(session.start.deviceCode)
    }

    fun stop() {
        countdownJob?.cancel()
        poller.stop()
    }

    private fun startLogin() {
        store.launch {
            try {
                val flowStart =
                    withContext(Dispatchers.IO) {
                        repository.startDeviceFlow()
                    }
                onStarted(flowStart)
            } catch (e: AuthException) {
                onStartFailed(e)
            }
        }
    }

    private suspend fun onStarted(flowStart: DeviceFlowStart) {
        val start = flowStart.start
        poller.path = flowStart.path
        logger.debug("Device flow started via path=${flowStart.path}")

        val startUi = start.toUi()

        withContext(Dispatchers.Main.immediate) {
            store.update {
                it.copy(
                    loginState = startUi.toDevicePrompt(start.expiresInSec),
                    copied = false,
                )
            }

            savedState.saveDeviceSession(startUi, flowStart.path)
            startCountdown(start.expiresInSec)
            poller.start(start.deviceCode)

            val label = getString(Res.string.enter_code_on_github)
            if (copyToClipboard(label, start.userCode)) {
                store.update { it.copy(copied = true) }
            }
        }
    }

    private suspend fun onStartFailed(error: AuthException) {
        stop()
        savedState.clear()
        val rootCause = generateSequence<Throwable>(error) { it.cause }.lastOrNull() ?: error
        val rootClass = rootCause::class.simpleName ?: "Throwable"
        val rootMsg = rootCause.message ?: "<no message>"
        logger.error(
            "startDeviceFlow failed. topClass=${error::class.simpleName} topMsg=${error.message} | " +
                "rootClass=$rootClass rootMsg=$rootMsg",
            error,
        )
        withContext(Dispatchers.Main.immediate) {
            store.update { it.copy(loginState = AuthLoginState.Error(error.toAuthError())) }
        }
    }

    private fun startCountdown(remainingSeconds: Int) {
        countdownJob?.cancel()
        countdownJob =
            store.launch {
                var remaining = remainingSeconds
                while (remaining > 0) {
                    if (!showRemaining(remaining)) return@launch
                    delay(1.seconds)
                    remaining--
                }

                poller.stop()
                savedState.clear()
                store.update { it.copy(loginState = AuthLoginState.Error(AuthError.CodeExpired)) }
            }
    }

    // False once the prompt is gone, which ends the countdown.
    private fun showRemaining(remaining: Int): Boolean {
        var showing = false
        store.update { current ->
            val prompt = current.loginState as? AuthLoginState.DevicePrompt
            showing = prompt != null
            if (prompt != null) {
                current.copy(loginState = prompt.start.toDevicePrompt(remaining))
            } else {
                current
            }
        }
        return showing
    }

    private fun copyCode(start: GithubDeviceStartUi) {
        val copied = copyToClipboard("GitHub Code", start.userCode)
        store.update {
            val currentRemaining = (it.loginState as? AuthLoginState.DevicePrompt)?.remainingSeconds ?: 0
            it.copy(
                loginState = start.toDevicePrompt(currentRemaining),
                copied = copied,
            )
        }
    }

    private fun copyToClipboard(
        label: String,
        userCode: String,
    ): Boolean =
        try {
            clipboardHelper.copy(label = label, text = userCode.filter { it.isLetterOrDigit() })
            true
        } catch (e: SystemPackagesException) {
            logger.debug("Failed to copy to clipboard: ${e.message}")
            false
        }

    private fun openGitHub(start: GithubDeviceStartUi) {
        val url = start.verificationUriComplete ?: buildPrefilledUrl(start.verificationUri, start.userCode)
        browserHelper.openUrl(url) { error -> logger.debug("Failed to open browser: $error") }
    }

    private fun buildPrefilledUrl(
        verificationUri: String,
        userCode: String,
    ): String {
        val separator = if ('?' in verificationUri) "&" else "?"
        return verificationUri + separator + "user_code=" + URLEncoder.encode(userCode, "UTF-8")
    }
}
