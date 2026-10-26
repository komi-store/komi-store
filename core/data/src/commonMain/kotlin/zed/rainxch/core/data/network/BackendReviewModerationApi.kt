package zed.rainxch.core.data.network

import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import zed.rainxch.core.data.dto.BackendModerationQueueDto

// The /v1/reviews/moderation routes. Every failure becomes a ReviewException, as in BackendReviewsApi.
class BackendReviewModerationApi(
    private val client: BackendApiClient,
) {
    suspend fun getModerationQueue(
        status: String,
        page: Int,
    ): Result<BackendModerationQueueDto> =
        client.reviewsCall(authed = true) { token ->
            val response =
                get("reviews/moderation/queue") {
                    header(X_GITHUB_TOKEN_HEADER, token)
                    parameter("status", status)
                    parameter("page", page)
                }
            response.reviewBodyOrFailure()
        }

    // action is hide, unhide or dismiss. The optional reason isn't offered in the app, so no body is sent.
    suspend fun moderate(
        reviewId: String,
        action: String,
    ): Result<Unit> =
        client.reviewsCall(authed = true) { token ->
            post("reviews/moderation/item/$reviewId/$action") {
                header(X_GITHUB_TOKEN_HEADER, token)
            }.reviewEmptyOrFailure()
        }

    suspend fun deleteAsModerator(reviewId: String): Result<Unit> =
        client.reviewsCall(authed = true) { token ->
            delete("reviews/moderation/item/$reviewId") {
                header(X_GITHUB_TOKEN_HEADER, token)
            }.reviewEmptyOrFailure()
        }

    suspend fun setBanned(
        githubId: Long,
        banned: Boolean,
    ): Result<Unit> =
        client.reviewsCall(authed = true) { token ->
            val path = "reviews/moderation/users/$githubId/ban"
            val response =
                if (banned) {
                    post(path) { header(X_GITHUB_TOKEN_HEADER, token) }
                } else {
                    delete(path) { header(X_GITHUB_TOKEN_HEADER, token) }
                }
            response.reviewEmptyOrFailure()
        }
}
