package zed.rainxch.core.domain.repository

import zed.rainxch.core.domain.model.reviews.ModerationDecision
import zed.rainxch.core.domain.model.reviews.ModerationPage
import zed.rainxch.core.domain.model.reviews.ModerationStatus

interface ReviewModeration {
    // Moderators only (MyReviewState.isModerator); anyone else gets NOT_MODERATOR.
    suspend fun getModerationQueue(
        status: ModerationStatus,
        page: Int = 1,
    ): Result<ModerationPage>

    suspend fun moderate(
        reviewId: String,
        decision: ModerationDecision,
    ): Result<Unit>

    suspend fun setBanned(
        githubId: Long,
        banned: Boolean,
    ): Result<Unit>
}
