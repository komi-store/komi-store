package zed.rainxch.auth.presentation.mapper

import zed.rainxch.auth.presentation.model.AuthLoginState
import zed.rainxch.auth.presentation.model.GithubDeviceStartUi

private const val SECONDS_PER_MINUTE = 60
private const val URGENT_BELOW_SECONDS = 60

internal fun GithubDeviceStartUi.toDevicePrompt(remainingSeconds: Int): AuthLoginState.DevicePrompt {
    val total = expiresInSec.coerceAtLeast(1)
    val minutes = remainingSeconds / SECONDS_PER_MINUTE
    val seconds = remainingSeconds % SECONDS_PER_MINUTE
    return AuthLoginState.DevicePrompt(
        start = this,
        remainingSeconds = remainingSeconds,
        progressFraction = (remainingSeconds.toFloat() / total).coerceIn(0f, 1f),
        formattedTimer = "%02d:%02d".format(minutes, seconds),
        isUrgent = remainingSeconds in 1 until URGENT_BELOW_SECONDS,
    )
}
