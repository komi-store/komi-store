package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.reviews.ModerationDecision
import zed.rainxch.core.domain.model.reviews.ModerationPage
import zed.rainxch.core.domain.model.reviews.ModerationStatus
import zed.rainxch.core.domain.model.reviews.MyReviewState
import zed.rainxch.core.domain.model.reviews.RepoRating
import zed.rainxch.core.domain.model.reviews.ReportReason
import zed.rainxch.core.domain.model.reviews.Review
import zed.rainxch.core.domain.model.reviews.ReviewDraft
import zed.rainxch.core.domain.model.reviews.ReviewReply
import zed.rainxch.core.domain.model.reviews.ReviewSort
import zed.rainxch.core.domain.model.reviews.ReviewTarget
import zed.rainxch.core.domain.model.reviews.ReviewVote
import zed.rainxch.core.domain.model.reviews.ReviewsPage

// Every failure is a ReviewException; reads never send the GitHub token, writes always do.
interface ReviewsRepository {
    // Emits the repo after the viewer posts, edits or deletes their review, so other screens can refresh.
    val changes: Flow<ReviewTarget>

    suspend fun getReviews(
        target: ReviewTarget,
        sort: ReviewSort = ReviewSort.TOP,
        rating: Int? = null,
        page: Int = 1,
    ): Result<ReviewsPage>

    suspend fun getSummaries(targets: List<ReviewTarget>): Result<Map<ReviewTarget, RepoRating>>

    suspend fun getMine(target: ReviewTarget): Result<MyReviewState>

    suspend fun submit(
        target: ReviewTarget,
        draft: ReviewDraft,
    ): Result<Review>

    suspend fun deleteMine(target: ReviewTarget): Result<Unit>

    // null takes the viewer's vote back.
    suspend fun setVote(
        reviewId: String,
        vote: ReviewVote?,
    ): Result<Unit>

    suspend fun report(
        reviewId: String,
        reason: ReportReason,
        note: String?,
    ): Result<Unit>

    suspend fun reply(
        reviewId: String,
        text: String,
    ): Result<ReviewReply>

    suspend fun deleteReply(reviewId: String): Result<Unit>

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
