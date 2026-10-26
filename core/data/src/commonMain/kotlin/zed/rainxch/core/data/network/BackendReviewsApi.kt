package zed.rainxch.core.data.network

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import zed.rainxch.core.data.dto.BackendMyReviewDto
import zed.rainxch.core.data.dto.BackendReviewDraftDto
import zed.rainxch.core.data.dto.BackendReviewDto
import zed.rainxch.core.data.dto.BackendReviewReplyDto
import zed.rainxch.core.data.dto.BackendReviewReplyRequestDto
import zed.rainxch.core.data.dto.BackendReviewReportDto
import zed.rainxch.core.data.dto.BackendReviewSummariesDto
import zed.rainxch.core.data.dto.BackendReviewVoteDto
import zed.rainxch.core.data.dto.BackendReviewsPageDto
import zed.rainxch.core.domain.model.reviews.ReviewTarget

// The /v1/reviews routes (docs/agents/reviews.md) apart from moderation. Every failure becomes a ReviewException.
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
        client.reviewsCall(authed = false) {
            val response =
                get(repoPath(target)) {
                    hostParameter(target)
                    parameter("sort", sort)
                    if (rating != null) parameter("rating", rating)
                    parameter("page", page)
                    parameter("limit", PAGE_SIZE)
                    if (fresh != null) parameter("fresh", fresh)
                }
            response.reviewBodyOrFailure()
        }

    suspend fun getSummaries(
        repos: List<String>,
        host: String?,
    ): Result<BackendReviewSummariesDto> =
        client.reviewsCall(authed = false) {
            val response =
                get("reviews/summaries") {
                    repos.forEach { parameter("r", it) }
                    if (host != null) parameter("host", host)
                }
            response.reviewBodyOrFailure()
        }

    suspend fun getMine(target: ReviewTarget): Result<BackendMyReviewDto> =
        client.reviewsCall(authed = true) { token ->
            val response =
                get("${repoPath(target)}/mine") {
                    hostParameter(target)
                    header(X_GITHUB_TOKEN_HEADER, token)
                }
            response.reviewBodyOrFailure()
        }

    suspend fun putReview(
        target: ReviewTarget,
        draft: BackendReviewDraftDto,
    ): Result<BackendReviewDto> =
        client.reviewsCall(authed = true) { token ->
            val response =
                put(repoPath(target)) {
                    hostParameter(target)
                    header(X_GITHUB_TOKEN_HEADER, token)
                    contentType(ContentType.Application.Json)
                    setBody(draft)
                }
            response.reviewBodyOrFailure()
        }

    suspend fun deleteReview(target: ReviewTarget): Result<Unit> =
        client.reviewsCall(authed = true) { token ->
            delete(repoPath(target)) {
                hostParameter(target)
                header(X_GITHUB_TOKEN_HEADER, token)
            }.reviewEmptyOrFailure()
        }

    // value is 1 or -1; null takes the vote back.
    suspend fun setVote(
        reviewId: String,
        value: Int?,
    ): Result<Unit> =
        client.reviewsCall(authed = true) { token ->
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
            response.reviewEmptyOrFailure()
        }

    suspend fun report(
        reviewId: String,
        body: BackendReviewReportDto,
    ): Result<Unit> =
        client.reviewsCall(authed = true) { token ->
            post("reviews/item/$reviewId/report") {
                header(X_GITHUB_TOKEN_HEADER, token)
                contentType(ContentType.Application.Json)
                setBody(body)
            }.reviewEmptyOrFailure()
        }

    suspend fun putReply(
        reviewId: String,
        text: String,
    ): Result<BackendReviewReplyDto> =
        client.reviewsCall(authed = true) { token ->
            val response =
                put("reviews/item/$reviewId/reply") {
                    header(X_GITHUB_TOKEN_HEADER, token)
                    contentType(ContentType.Application.Json)
                    setBody(BackendReviewReplyRequestDto(text))
                }
            response.reviewBodyOrFailure()
        }

    suspend fun deleteReply(reviewId: String): Result<Unit> =
        client.reviewsCall(authed = true) { token ->
            delete("reviews/item/$reviewId/reply") {
                header(X_GITHUB_TOKEN_HEADER, token)
            }.reviewEmptyOrFailure()
        }

    private fun repoPath(target: ReviewTarget) = "reviews/repo/${target.owner}/${target.name}"

    private fun HttpRequestBuilder.hostParameter(target: ReviewTarget) {
        if (target.host != null) parameter("host", target.host)
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
