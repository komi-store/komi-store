package zed.rainxch.auth.domain.repository

import kotlinx.coroutines.flow.Flow

interface AuthenticationRepository {
    val accessTokenFlow: Flow<String?>

    suspend fun startDeviceFlow(): DeviceFlowStart

    suspend fun pollDeviceTokenOnce(
        deviceCode: String,
        path: AuthPath,
    ): PollOutcome

    suspend fun signInWithPat(token: String): Result<Unit>

    suspend fun registerWebAuth(): Result<WebAuthRegistration>

    suspend fun exchangeWebAuthHandoff(handoffId: String): Result<String>
}
