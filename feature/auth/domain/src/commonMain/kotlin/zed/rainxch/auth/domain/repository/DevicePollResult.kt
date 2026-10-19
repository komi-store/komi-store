package zed.rainxch.auth.domain.repository

import zed.rainxch.core.domain.model.account.github.GithubDeviceTokenSuccess

sealed interface DevicePollResult {
    data class Success(
        val token: GithubDeviceTokenSuccess,
    ) : DevicePollResult

    data object Pending : DevicePollResult

    data object SlowDown : DevicePollResult

    data class Failed(
        val error: AuthException,
    ) : DevicePollResult
}
