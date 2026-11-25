package zed.rainxch.auth.data.repository

import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import zed.rainxch.auth.data.network.BackendHttpException
import zed.rainxch.auth.domain.repository.AuthException
import zed.rainxch.core.domain.model.error.NetworkException
import java.io.IOException

private const val HTTP_TOO_MANY_REQUESTS = 429
private val HTTP_SERVER_ERRORS = 500..599

internal fun Throwable.toAuthException(): AuthException =
    when {
        this is AuthException -> this
        this is BackendHttpException && statusCode == HTTP_TOO_MANY_REQUESTS -> AuthException.RateLimited(this)
        transportFailure() is IOException || isAuthInfrastructureError() -> AuthException.Unreachable(this)
        else -> AuthException.Failed(this)
    }

internal fun Throwable.isAuthInfrastructureError(): Boolean =
    when (val failure = transportFailure()) {
        is HttpRequestTimeoutException,
        is SocketTimeoutException,
        is ConnectTimeoutException,
        -> true

        is BackendHttpException -> failure.statusCode in HTTP_SERVER_ERRORS

        else -> isNetworkError((failure.message ?: "").lowercase())
    }

internal fun Throwable.isBackendStartFallbackEligible(): Boolean =
    isAuthInfrastructureError() || (this is BackendHttpException && statusCode == HTTP_TOO_MANY_REQUESTS)

internal fun isNetworkError(errorMsg: String): Boolean =
    errorMsg.contains("unable to resolve") ||
        errorMsg.contains("no address") ||
        errorMsg.contains("failed to connect") ||
        errorMsg.contains("connection refused") ||
        errorMsg.contains("network is unreachable") ||
        errorMsg.contains("timeout") ||
        errorMsg.contains("timed out") ||
        errorMsg.contains("connection reset") ||
        errorMsg.contains("broken pipe") ||
        errorMsg.contains("host unreachable") ||
        errorMsg.contains("network error")

// networkFailures {} wraps what Ktor threw, so the transport error is the NetworkException's cause.
private fun Throwable.transportFailure(): Throwable = if (this is NetworkException) cause ?: this else this
