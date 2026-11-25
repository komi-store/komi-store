package zed.rainxch.auth.presentation.handlers

import androidx.lifecycle.SavedStateHandle
import zed.rainxch.auth.domain.repository.AuthPath
import zed.rainxch.auth.presentation.model.GithubDeviceStartUi

internal const val DEFAULT_POLL_INTERVAL_SEC = 5
internal const val MILLIS_PER_SECOND = 1000L

internal class RestoredDeviceSession(
    val start: GithubDeviceStartUi,
    val remainingSeconds: Int,
    val path: AuthPath,
)

// The sign-in in progress, kept in SavedStateHandle so a device code or web-auth state survives process death.
internal class AuthSavedState(
    private val handle: SavedStateHandle,
) {
    val pendingWebAuthState: String? get() = handle[KEY_WEB_AUTH_STATE]

    fun beginWebAuth(state: String) {
        handle[KEY_WEB_AUTH_STATE] = state
    }

    fun endWebAuth() {
        handle.remove<String>(KEY_WEB_AUTH_STATE)
    }

    fun saveDeviceSession(
        start: GithubDeviceStartUi,
        path: AuthPath,
    ) {
        handle[KEY_DEVICE_CODE] = start.deviceCode
        handle[KEY_USER_CODE] = start.userCode
        handle[KEY_VERIFICATION_URI] = start.verificationUri
        handle[KEY_VERIFICATION_URI_COMPLETE] = start.verificationUriComplete
        handle[KEY_INTERVAL_SEC] = start.intervalSec
        handle[KEY_EXPIRES_IN_SEC] = start.expiresInSec
        handle[KEY_START_TIME_MILLIS] = System.currentTimeMillis()
        savePath(path)
    }

    fun savePath(path: AuthPath) {
        handle[KEY_AUTH_PATH] = path.name
    }

    fun restoreDeviceSession(): RestoredDeviceSession? {
        val start = savedStart() ?: return null
        val startTimeMillis = handle.get<Long>(KEY_START_TIME_MILLIS) ?: return null
        val path = handle.get<String>(KEY_AUTH_PATH)?.let { name -> AuthPath.entries.firstOrNull { it.name == name } }
        val elapsedSec = ((System.currentTimeMillis() - startTimeMillis) / MILLIS_PER_SECOND).toInt()
        val remainingSec = start.expiresInSec - elapsedSec
        if (path == null || remainingSec <= 0) {
            clear()
            return null
        }
        return RestoredDeviceSession(start = start, remainingSeconds = remainingSec, path = path)
    }

    fun clear() {
        SAVED_STATE_KEYS.forEach { handle.remove<Any>(it) }
    }

    private fun savedStart(): GithubDeviceStartUi? {
        val deviceCode = handle.get<String>(KEY_DEVICE_CODE) ?: return null
        val userCode = handle.get<String>(KEY_USER_CODE) ?: return null
        val verificationUri = handle.get<String>(KEY_VERIFICATION_URI) ?: return null
        val expiresInSec = handle.get<Int>(KEY_EXPIRES_IN_SEC) ?: return null
        return GithubDeviceStartUi(
            deviceCode = deviceCode,
            userCode = userCode,
            verificationUri = verificationUri,
            verificationUriComplete = handle.get<String>(KEY_VERIFICATION_URI_COMPLETE),
            intervalSec = handle.get<Int>(KEY_INTERVAL_SEC) ?: DEFAULT_POLL_INTERVAL_SEC,
            expiresInSec = expiresInSec,
        )
    }

    private companion object {
        const val KEY_DEVICE_CODE = "auth_device_code"
        const val KEY_USER_CODE = "auth_user_code"
        const val KEY_VERIFICATION_URI = "auth_verification_uri"
        const val KEY_VERIFICATION_URI_COMPLETE = "auth_verification_uri_complete"
        const val KEY_INTERVAL_SEC = "auth_interval_sec"
        const val KEY_EXPIRES_IN_SEC = "auth_expires_in_sec"
        const val KEY_START_TIME_MILLIS = "auth_start_time_millis"
        const val KEY_AUTH_PATH = "auth_path"
        const val KEY_WEB_AUTH_STATE = "auth_web_state"

        val SAVED_STATE_KEYS =
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
