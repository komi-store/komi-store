package zed.rainxch.core.domain.repository

import zed.rainxch.core.domain.model.reviews.MyReviewState
import zed.rainxch.core.domain.model.reviews.RepoRating
import zed.rainxch.core.domain.model.reviews.ReportReason
import zed.rainxch.core.domain.model.reviews.Review
import zed.rainxch.core.domain.model.reviews.ReviewDraft
import zed.rainxch.core.domain.model.reviews.ReviewReply
import zed.rainxch.core.domain.model.reviews.ReviewSort
import zed.rainxch.core.domain.model.reviews.ReviewTarget
import zed.rainxch.core.domain.model.reviews.ReviewsPage

// Every failure is a ReviewException; reads never send the GitHub token, writes always do.
interface ReviewsRepository {
    suspend fun getReviews(
        target: ReviewTarget,
        sort: ReviewSort = ReviewSort.HELPFUL,
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

    suspend fun setHelpful(
        reviewId: String,
        helpful: Boolean,
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
}
