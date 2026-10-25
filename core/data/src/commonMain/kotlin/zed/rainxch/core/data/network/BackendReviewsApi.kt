package zed.rainxch.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import zed.rainxch.core.data.dto.BackendModerationQueueDto
import zed.rainxch.core.data.dto.BackendMyReviewDto
import zed.rainxch.core.data.dto.BackendReviewDraftDto
import zed.rainxch.core.data.dto.BackendReviewDto
import zed.rainxch.core.data.dto.BackendReviewReplyDto
import zed.rainxch.core.data.dto.BackendReviewReplyRequestDto
import zed.rainxch.core.data.dto.BackendReviewReportDto
import zed.rainxch.core.data.dto.BackendReviewSummariesDto
import zed.rainxch.core.data.dto.BackendReviewVoteDto
import zed.rainxch.core.data.dto.BackendReviewsPageDto
import zed.rainxch.core.domain.model.reviews.ReviewError
import zed.rainxch.core.domain.model.reviews.ReviewException
import zed.rainxch.core.domain.model.reviews.ReviewTarget
import kotlin.coroutines.cancellation.CancellationException

// The /v1/reviews routes (docs/agents/reviews.md). Every failure becomes a ReviewException.
class BackendReviewsApi(
    private val client: BackendApiClient,
) {
    // [fresh] is a nonce the backend ignores: a URL no one has asked for yet skips the CDN's copy.
    suspend fun getReviews(
        target: ReviewTarget,
        sort: String,
        rating: Int?,
        page: Int,
        fresh: Long? = null,
    ): Result<BackendReviewsPageDto> =
        call(authed = false) {
            val response =
                get(repoPath(target)) {
                    hostParameter(target)
                    parameter("sort", sort)
                    if (rating != null) parameter("rating", rating)
                    parameter("page", page)
                    parameter("limit", PAGE_SIZE)
                    if (fresh != null) parameter("fresh", fresh)
                }
            response.bodyOrFailure()
        }

    suspend fun getSummaries(
        repos: List<String>,
        host: String?,
    ): Result<BackendReviewSummariesDto> =
        call(authed = false) {
            val response =
                get("reviews/summaries") {
                    repos.forEach { parameter("r", it) }
                    if (host != null) parameter("host", host)
                }
            response.bodyOrFailure()
        }

    suspend fun getMine(target: ReviewTarget): Result<BackendMyReviewDto> =
        call(authed = true) { token ->
            val response =
                get("${repoPath(target)}/mine") {
                    hostParameter(target)
                    header(X_GITHUB_TOKEN_HEADER, token)
                }
            response.bodyOrFailure()
        }

    suspend fun putReview(
        target: ReviewTarget,
        draft: BackendReviewDraftDto,
    ): Result<BackendReviewDto> =
        call(authed = true) { token ->
            val response =
                put(repoPath(target)) {
                    hostParameter(target)
                    header(X_GITHUB_TOKEN_HEADER, token)
                    contentType(ContentType.Application.Json)
                    setBody(draft)
                }
            response.bodyOrFailure()
        }

    suspend fun deleteReview(target: ReviewTarget): Result<Unit> =
        call(authed = true) { token ->
            delete(repoPath(target)) {
                hostParameter(target)
                header(X_GITHUB_TOKEN_HEADER, token)
            }.emptyOrFailure()
        }

    // value is 1 or -1; null takes the vote back.
    suspend fun setVote(
        reviewId: String,
        value: Int?,
    ): Result<Unit> =
        call(authed = true) { token ->
            val path = "reviews/item/$reviewId/vote"
            val response =
                if (value != null) {
                    put(path) {
                        header(X_GITHUB_TOKEN_HEADER, token)
                        contentType(ContentType.Application.Json)
                        setBody(BackendReviewVoteDto(value))
                    }
                } else {
                    delete(path) { header(X_GITHUB_TOKEN_HEADER, token) }
                }
            response.emptyOrFailure()
        }

    suspend fun report(
        reviewId: String,
        body: BackendReviewReportDto,
    ): Result<Unit> =
        call(authed = true) { token ->
            post("reviews/item/$reviewId/report") {
                header(X_GITHUB_TOKEN_HEADER, token)
                contentType(ContentType.Application.Json)
                setBody(body)
            }.emptyOrFailure()
        }

    suspend fun putReply(
        reviewId: String,
        text: String,
    ): Result<BackendReviewReplyDto> =
        call(authed = true) { token ->
            val response =
                put("reviews/item/$reviewId/reply") {
                    header(X_GITHUB_TOKEN_HEADER, token)
                    contentType(ContentType.Application.Json)
                    setBody(BackendReviewReplyRequestDto(text))
                }
            response.bodyOrFailure()
        }

    suspend fun deleteReply(reviewId: String): Result<Unit> =
        call(authed = true) { token ->
            delete("reviews/item/$reviewId/reply") {
                header(X_GITHUB_TOKEN_HEADER, token)
            }.emptyOrFailure()
        }

    suspend fun getModerationQueue(
        status: String,
        page: Int,
    ): Result<BackendModerationQueueDto> =
        call(authed = true) { token ->
            val response =
                get("reviews/moderation/queue") {
                    header(X_GITHUB_TOKEN_HEADER, token)
                    parameter("status", status)
                    parameter("page", page)
                }
            response.bodyOrFailure()
        }

    // action is hide, unhide or dismiss. The optional reason isn't offered in the app, so no body is sent.
    suspend fun moderate(
        reviewId: String,
        action: String,
    ): Result<Unit> =
        call(authed = true) { token ->
            post("reviews/moderation/item/$reviewId/$action") {
                header(X_GITHUB_TOKEN_HEADER, token)
            }.emptyOrFailure()
        }

    suspend fun deleteAsModerator(reviewId: String): Result<Unit> =
        call(authed = true) { token ->
            delete("reviews/moderation/item/$reviewId") {
                header(X_GITHUB_TOKEN_HEADER, token)
            }.emptyOrFailure()
        }

    suspend fun setBanned(
        githubId: Long,
        banned: Boolean,
    ): Result<Unit> =
        call(authed = true) { token ->
            val path = "reviews/moderation/users/$githubId/ban"
            val response =
                if (banned) {
                    post(path) { header(X_GITHUB_TOKEN_HEADER, token) }
                } else {
                    delete(path) { header(X_GITHUB_TOKEN_HEADER, token) }
                }
            response.emptyOrFailure()
        }

    // A missing token never reaches the backend; any failure that isn't ours (no network, a timeout) is NETWORK.
    private suspend fun <T> call(
        authed: Boolean,
        block: suspend HttpClient.(token: String) -> Result<T>,
    ): Result<T> {
        val result =
            client.request(authed) { token ->
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

    private suspend inline fun <reified T> HttpResponse.bodyOrFailure(): Result<T> =
        if (status.isSuccess()) Result.success(body()) else Result.failure(toReviewException())

    private suspend fun HttpResponse.emptyOrFailure(): Result<Unit> =
        if (status.isSuccess()) Result.success(Unit) else Result.failure(toReviewException())

    private suspend fun HttpResponse.toReviewException(): ReviewException {
        if (status == HttpStatusCode.TooManyRequests) {
            return ReviewException(ReviewError.RATE_LIMITED, headers["Retry-After"]?.toLongOrNull())
        }
        val kind =
            when (errorCode()) {
                "auth_required" -> ReviewError.AUTH_REQUIRED

                "auth_invalid" -> ReviewError.AUTH_INVALID

                "account_too_new" -> ReviewError.ACCOUNT_TOO_NEW

                "own_repo" -> ReviewError.OWN_REPO

                "own_review" -> ReviewError.OWN_REVIEW

                "banned" -> ReviewError.BANNED

                "not_maintainer" -> ReviewError.NOT_MAINTAINER

                "not_moderator" -> ReviewError.NOT_MODERATOR

                "repo_not_found" -> ReviewError.REPO_NOT_FOUND

                "review_not_found" -> ReviewError.REVIEW_NOT_FOUND

                in INPUT_ERRORS -> ReviewError.INVALID_INPUT

                // A plain 404 is the backend with REVIEWS_ENABLED off; 5xx and upstream_unavailable read the same way.
                else -> if (status == HttpStatusCode.Unauthorized) ReviewError.AUTH_INVALID else ReviewError.UNAVAILABLE
            }
        return ReviewException(kind)
    }

    private suspend fun HttpResponse.errorCode(): String? =
        try {
            val obj = Json.parseToJsonElement(bodyAsText()) as? JsonObject
            (obj?.get("error") as? JsonPrimitive)?.contentOrNull
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private fun repoPath(target: ReviewTarget) = "reviews/repo/${target.owner}/${target.name}"

    private fun HttpRequestBuilder.hostParameter(target: ReviewTarget) {
        if (target.host != null) parameter("host", target.host)
    }

    private companion object {
        const val PAGE_SIZE = 20
        const val X_GITHUB_TOKEN_HEADER = "X-GitHub-Token"
        val INPUT_ERRORS =
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
    }
}
