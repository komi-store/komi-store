package zed.rainxch.core.data.network

import io.ktor.http.HttpStatusCode
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.error.RateLimitInfo
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

fun shouldFallbackToGithubOrRethrow(cause: Throwable): Boolean =
    when (cause) {
        is CancellationException -> throw cause
        is RateLimitedException -> throw cause.toDomainRateLimitException()
        is BackendException -> cause.statusCode in SERVER_ERROR_CODES
        else -> true
    }

fun shouldFallbackToGithubOrRethrow(
    cause: Throwable,
    isSignedIn: Boolean,
): Boolean =
    when (cause) {
        is CancellationException -> {
            throw cause
        }

        is RateLimitedException -> {
            throw cause.toDomainRateLimitException()
        }

        is BackendException -> {
            cause.statusCode in SERVER_ERROR_CODES ||
                (isSignedIn && cause.statusCode in SIGNED_IN_FALLBACK_CODES)
        }

        else -> {
            true
        }
    }

private val SERVER_ERROR_CODES = 500..599

private val SIGNED_IN_FALLBACK_CODES =
    setOf(HttpStatusCode.Unauthorized.value, HttpStatusCode.Forbidden.value, HttpStatusCode.NotFound.value)

private fun RateLimitedException.toDomainRateLimitException(): RateLimitException {
    val nowSec = Clock.System.now().epochSeconds
    val reset =
        resetEpochSeconds
            ?: retryAfterSeconds?.let { nowSec + it }
            ?: nowSec
    return RateLimitException(
        rateLimitInfo =
            RateLimitInfo(
                limit = 0,
                remaining = 0,
                resetTimestamp = reset,
                resource = "backend",
            ),
    )
}
