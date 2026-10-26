package zed.rainxch.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import zed.rainxch.core.domain.model.reviews.ReviewError
import zed.rainxch.core.domain.model.reviews.ReviewException

// A missing token never reaches the backend; any failure that isn't ours (no network, a timeout) is NETWORK.
internal suspend fun <T> BackendApiClient.reviewsCall(
    authed: Boolean,
    block: suspend HttpClient.(token: String) -> Result<T>,
): Result<T> {
    val result =
        request(authed) { token ->
            if (authed && token == null) {
                Result.failure(ReviewException(ReviewError.AUTH_REQUIRED))
            } else {
                this.block(token.orEmpty())
            }
        }
    return result.exceptionOrNull()?.let { error ->
        if (error is ReviewException) result else Result.failure(ReviewException(ReviewError.NETWORK))
    } ?: result
}

internal suspend inline fun <reified T> HttpResponse.reviewBodyOrFailure(): Result<T> =
    if (status.isSuccess()) Result.success(body()) else Result.failure(toReviewException())

internal suspend fun HttpResponse.reviewEmptyOrFailure(): Result<Unit> =
    if (status.isSuccess()) Result.success(Unit) else Result.failure(toReviewException())

internal suspend fun HttpResponse.toReviewException(): ReviewException {
    if (status == HttpStatusCode.TooManyRequests) {
        return ReviewException(ReviewError.RATE_LIMITED, headers["Retry-After"]?.toLongOrNull())
    }
    val code = errorBodyOrNull()?.stringField("error")
    val kind =
        when {
            code != null && code in ERROR_KINDS -> ERROR_KINDS.getValue(code)

            code in INPUT_ERRORS -> ReviewError.INVALID_INPUT

            status == HttpStatusCode.Unauthorized -> ReviewError.AUTH_INVALID

            // A plain 404 is the backend with REVIEWS_ENABLED off; 5xx and upstream_unavailable read the same way.
            else -> ReviewError.UNAVAILABLE
        }
    return ReviewException(kind)
}

private val ERROR_KINDS =
    mapOf(
        "auth_required" to ReviewError.AUTH_REQUIRED,
        "auth_invalid" to ReviewError.AUTH_INVALID,
        "account_too_new" to ReviewError.ACCOUNT_TOO_NEW,
        "own_repo" to ReviewError.OWN_REPO,
        "own_review" to ReviewError.OWN_REVIEW,
        "banned" to ReviewError.BANNED,
        "not_maintainer" to ReviewError.NOT_MAINTAINER,
        "not_moderator" to ReviewError.NOT_MODERATOR,
        "repo_not_found" to ReviewError.REPO_NOT_FOUND,
        "review_not_found" to ReviewError.REVIEW_NOT_FOUND,
    )

private val INPUT_ERRORS =
    setOf(
        "invalid_owner",
        "invalid_name",
        "invalid_host",
        "invalid_request",
        "payload_too_large",
        "invalid_rating",
        "text_too_long",
        "note_too_long",
        "invalid_platform",
        "invalid_reason",
    )
