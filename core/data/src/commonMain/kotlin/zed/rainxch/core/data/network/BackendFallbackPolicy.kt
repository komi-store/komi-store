package zed.rainxch.core.data.network

import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.error.RateLimitInfo
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

fun shouldFallbackToGithubOrRethrow(cause: Throwable): Boolean =
    when (cause) {
        is CancellationException -> throw cause
        is RateLimitedException -> throw cause.toDomainRateLimitException()
        is BackendException -> cause.statusCode in 500..599
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
            cause.statusCode in 500..599 ||
                (isSignedIn && cause.statusCode in setOf(401, 403, 404))
        }

        else -> {
            true
        }
    }

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
