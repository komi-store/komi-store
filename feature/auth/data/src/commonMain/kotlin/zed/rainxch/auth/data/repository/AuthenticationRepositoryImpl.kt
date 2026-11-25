package zed.rainxch.auth.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import zed.rainxch.auth.data.crypto.PkceGenerator
import zed.rainxch.auth.data.network.BackendHttpException
import zed.rainxch.auth.data.network.GitHubAuthApi
import zed.rainxch.auth.data.network.PatValidation
import zed.rainxch.auth.data.network.WebAuthApi
import zed.rainxch.auth.domain.repository.AuthException
import zed.rainxch.auth.domain.repository.AuthPath
import zed.rainxch.auth.domain.repository.AuthenticationRepository
import zed.rainxch.auth.domain.repository.DeviceFlowStart
import zed.rainxch.auth.domain.repository.DevicePollResult
import zed.rainxch.auth.domain.repository.PatRejectedException
import zed.rainxch.auth.domain.repository.PollOutcome
import zed.rainxch.auth.domain.repository.WebAuthRegistration
import zed.rainxch.core.data.datasource.TokenStore
import zed.rainxch.core.data.dto.GithubDeviceTokenSuccessDto
import zed.rainxch.core.data.mappers.toData
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.network.BACKEND_ORIGIN
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubDeviceTokenSuccess
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.NetworkException
import zed.rainxch.feature.auth.data.BuildKonfig

class AuthenticationRepositoryImpl(
    private val tokenStore: TokenStore,
    private val logger: KomiStoreLogger,
    private val gitHubAuthApi: GitHubAuthApi,
    private val webAuthApi: WebAuthApi,
) : AuthenticationRepository {
    override val accessTokenFlow: Flow<String?>
        get() = tokenStore.tokenFlow().map { it?.accessToken }

    override suspend fun startDeviceFlow(): DeviceFlowStart =
        withContext(Dispatchers.IO) {
            val clientId = BuildKonfig.GITHUB_CLIENT_ID
            if (clientId.isBlank()) {
                throw AuthException.Failed(
                    IllegalStateException("Missing GitHub CLIENT_ID. Add GITHUB_CLIENT_ID to local.properties."),
                )
            }

            try {
                val dto = gitHubAuthApi.startDeviceFlowViaBackend(BACKEND_ORIGIN)
                logger.info(
                    "Device flow started via Backend. interval=${dto.intervalSec}s expires=${dto.expiresInSec}s " +
                        "completeUri=${dto.verificationUriComplete != null}",
                )
                DeviceFlowStart(dto.toDomain(), AuthPath.Backend)
            } catch (e: NetworkException) {
                if (!e.isBackendStartFallbackEligible()) {
                    logger.error(
                        "Backend device/start non-infra failure (no fallback). ${e.summary()} origin=$BACKEND_ORIGIN",
                        e,
                    )
                    throw e.toAuthException()
                }
                logger.warn("Backend device/start failed, falling back to Direct. ${e.summary()} origin=$BACKEND_ORIGIN")
                startDeviceFlowDirect(clientId, backendFailure = e)
            }
        }

    private suspend fun startDeviceFlowDirect(
        clientId: String,
        backendFailure: NetworkException,
    ): DeviceFlowStart =
        try {
            val dto = gitHubAuthApi.startDeviceFlowDirect(clientId)
            logger.info("Device flow started via Direct fallback.")
            DeviceFlowStart(dto.toDomain(), AuthPath.Direct)
        } catch (e: NetworkException) {
            logger.error(
                "Direct device/start ALSO failed. backend: ${backendFailure.summary()} | direct: ${e.summary()}",
                e,
            )
            throw e.toAuthException()
        }

    override suspend fun pollDeviceTokenOnce(
        deviceCode: String,
        path: AuthPath,
    ): PollOutcome =
        withContext(Dispatchers.IO) {
            val clientId = BuildKonfig.GITHUB_CLIENT_ID

            val primaryResult =
                when (path) {
                    AuthPath.Backend -> gitHubAuthApi.pollDeviceTokenViaBackend(BACKEND_ORIGIN, deviceCode)
                    AuthPath.Direct -> gitHubAuthApi.pollDeviceTokenDirect(clientId, deviceCode)
                }

            val primaryError = primaryResult.exceptionOrNull()
            if (path == AuthPath.Backend && primaryError != null && primaryError.isAuthInfrastructureError()) {
                logger.debug(
                    "Backend poll infra error (${primaryError::class.simpleName}: " +
                        "${primaryError.message}), escalating to Direct for rest of session",
                )
                val fallbackResult = gitHubAuthApi.pollDeviceTokenDirect(clientId, deviceCode)
                return@withContext PollOutcome(interpretPollResult(fallbackResult), AuthPath.Direct)
            }

            PollOutcome(interpretPollResult(primaryResult), path)
        }

    private suspend fun interpretPollResult(res: Result<GithubDeviceTokenSuccessDto>): DevicePollResult {
        val success = res.getOrNull()?.toDomain()

        if (success != null) {
            logger.debug("Single poll: token received, saving")
            saveTokenWithVerification(success)
            return DevicePollResult.Success(success)
        }

        val error = res.exceptionOrNull()
        val errorMsg = (error?.message ?: "").lowercase()

        return when {
            "slow_down" in errorMsg -> {
                logger.debug("GitHub says slow down")
                DevicePollResult.SlowDown
            }

            "authorization_pending" in errorMsg -> {
                DevicePollResult.Pending
            }

            "access_denied" in errorMsg -> {
                DevicePollResult.Failed(AuthException.Denied())
            }

            "expired_token" in errorMsg ||
                "expired_device_code" in errorMsg ||
                "token_expired" in errorMsg -> {
                DevicePollResult.Failed(AuthException.CodeExpired())
            }

            else -> {
                logger.debug("Single poll unrecognized error, surfacing as Failed: $errorMsg")
                DevicePollResult.Failed(
                    (error ?: IllegalStateException("Poll failed without an error")).toAuthException(),
                )
            }
        }
    }

    override suspend fun registerWebAuth(): Result<WebAuthRegistration> =
        withContext(Dispatchers.IO) {
            val pkce = PkceGenerator.generate()
            try {
                val authUrl =
                    webAuthApi.register(
                        state = pkce.state,
                        codeChallenge = pkce.codeChallenge,
                        codeVerifier = pkce.codeVerifier,
                    )
                Result.success(WebAuthRegistration(state = pkce.state, authUrl = authUrl))
            } catch (e: NetworkException) {
                Result.failure(e.toAuthException())
            }
        }

    override suspend fun exchangeWebAuthHandoff(handoffId: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val accessToken = webAuthApi.consumeHandoff(handoffId)
                saveTokenWithVerification(bearerToken(accessToken))
                Result.success(accessToken)
            } catch (e: NetworkException) {
                Result.failure(e.toAuthException())
            } catch (e: LocalStoreException) {
                Result.failure(e.toAuthException())
            }
        }

    override suspend fun signInWithPat(token: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val trimmed = token.trim()

            if (!looksLikePat(trimmed)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Token format not recognized"),
                )
            }

            when (val validation = gitHubAuthApi.validatePersonalAccessToken(trimmed)) {
                is PatValidation.Valid -> {
                    logger.debug("PAT network-validated against GitHub /user")
                }

                is PatValidation.Rejected -> {
                    logger.warn("PAT rejected by GitHub: ${validation.kind}")
                    return@withContext Result.failure(
                        PatRejectedException(validation.kind),
                    )
                }

                is PatValidation.Unreachable -> {
                    logger.debug(
                        "PAT validation unreachable (${validation.reason}), saving optimistically",
                    )
                }
            }

            try {
                saveTokenWithVerification(bearerToken(trimmed))
                logger.debug("PAT saved")
                Result.success(Unit)
            } catch (e: LocalStoreException) {
                logger.warn("PAT save failed: ${e.message}")
                Result.failure(e)
            }
        }

    // TokenStore swallows write failures, so reading the token back is how a lost write shows up.
    private suspend fun saveTokenWithVerification(token: GithubDeviceTokenSuccess) {
        repeat(TOKEN_SAVE_ATTEMPTS) { attempt ->
            tokenStore.save(token.toData())
            delay(TOKEN_READ_BACK_DELAY_MS)
            if (tokenStore.currentToken()?.accessToken == token.accessToken) return
            logger.debug("Token verification failed (attempt ${attempt + 1}/$TOKEN_SAVE_ATTEMPTS)")
        }
        throw LocalStoreException(
            IllegalStateException("Token was not persisted correctly after $TOKEN_SAVE_ATTEMPTS attempts"),
        )
    }

    private fun bearerToken(accessToken: String): GithubDeviceTokenSuccess =
        GithubDeviceTokenSuccessDto(
            accessToken = accessToken,
            tokenType = "Bearer",
            expiresIn = null,
            scope = null,
            refreshToken = null,
            refreshTokenExpiresIn = null,
            savedAtEpochMillis = System.currentTimeMillis(),
        ).toDomain()

    private fun looksLikePat(token: String): Boolean =
        token.length >= PAT_MIN_LENGTH &&
            token.none { it.isWhitespace() } &&
            PAT_PREFIXES.any { token.startsWith(it) }

    private fun Throwable.summary(): String =
        "class=${(cause ?: this)::class.simpleName} status=${(this as? BackendHttpException)?.statusCode} " +
            "msg=${message ?: "<no message>"}"

    private companion object {
        const val TOKEN_SAVE_ATTEMPTS = 5
        const val TOKEN_READ_BACK_DELAY_MS = 100L
        const val PAT_MIN_LENGTH = 20
        val PAT_PREFIXES = listOf("ghp_", "github_pat_")
    }
}
