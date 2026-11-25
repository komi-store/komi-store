package zed.rainxch.core.data.repository

import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toQuery
import zed.rainxch.core.data.network.BackendReviewModerationApi
import zed.rainxch.core.domain.model.reviews.ModerationDecision
import zed.rainxch.core.domain.model.reviews.ModerationPage
import zed.rainxch.core.domain.model.reviews.ModerationStatus
import zed.rainxch.core.domain.repository.ReviewModeration

internal class ReviewModerationImpl(
    private val moderationApi: BackendReviewModerationApi,
) : ReviewModeration {
    override suspend fun getModerationQueue(
        status: ModerationStatus,
        page: Int,
    ): Result<ModerationPage> = moderationApi.getModerationQueue(status.toQuery(), page).map { it.toDomain() }

    override suspend fun moderate(
        reviewId: String,
        decision: ModerationDecision,
    ): Result<Unit> =
        when (decision) {
            ModerationDecision.DELETE -> moderationApi.deleteAsModerator(reviewId)
            else -> moderationApi.moderate(reviewId, decision.name.lowercase())
        }

    override suspend fun setBanned(
        githubId: Long,
        banned: Boolean,
    ): Result<Unit> = moderationApi.setBanned(githubId, banned)
}
