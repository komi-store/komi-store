package zed.rainxch.core.data.repository

import zed.rainxch.core.data.dto.BackendReviewReportDto
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toDto
import zed.rainxch.core.data.mappers.toQuery
import zed.rainxch.core.data.network.BackendReviewsApi
import zed.rainxch.core.domain.model.reviews.MyReviewState
import zed.rainxch.core.domain.model.reviews.RepoRating
import zed.rainxch.core.domain.model.reviews.ReportReason
import zed.rainxch.core.domain.model.reviews.Review
import zed.rainxch.core.domain.model.reviews.ReviewDraft
import zed.rainxch.core.domain.model.reviews.ReviewReply
import zed.rainxch.core.domain.model.reviews.ReviewSort
import zed.rainxch.core.domain.model.reviews.ReviewTarget
import zed.rainxch.core.domain.model.reviews.ReviewsPage
import zed.rainxch.core.domain.repository.ReviewsRepository

class ReviewsRepositoryImpl(
    private val api: BackendReviewsApi,
) : ReviewsRepository {
    override suspend fun getReviews(
        target: ReviewTarget,
        sort: ReviewSort,
        rating: Int?,
        page: Int,
    ): Result<ReviewsPage> = api.getReviews(target, sort.toQuery(), rating, page).map { it.toDomain() }

    // The backend takes one host per call, so a mixed list (GitHub and Codeberg rows) is fetched per host.
    override suspend fun getSummaries(targets: List<ReviewTarget>): Result<Map<ReviewTarget, RepoRating>> {
        val ratings = mutableMapOf<ReviewTarget, RepoRating>()
        for ((host, group) in targets.distinct().groupBy { it.host }) {
            for (chunk in group.chunked(SUMMARIES_PER_CALL)) {
                val byKey = chunk.associateBy { "${it.owner}/${it.name}".lowercase() }
                val summaries =
                    api.getSummaries(byKey.keys.toList(), host).getOrElse { return Result.failure(it) }.summaries
                summaries.forEach { (key, rating) -> byKey[key.lowercase()]?.let { ratings[it] = rating.toDomain() } }
            }
        }
        return Result.success(ratings)
    }

    override suspend fun getMine(target: ReviewTarget): Result<MyReviewState> = api.getMine(target).map { it.toDomain() }

    override suspend fun submit(
        target: ReviewTarget,
        draft: ReviewDraft,
    ): Result<Review> = api.putReview(target, draft.toDto()).map { it.toDomain() }

    override suspend fun deleteMine(target: ReviewTarget): Result<Unit> = api.deleteReview(target)

    override suspend fun setHelpful(
        reviewId: String,
        helpful: Boolean,
    ): Result<Unit> = api.setHelpful(reviewId, helpful)

    override suspend fun report(
        reviewId: String,
        reason: ReportReason,
        note: String?,
    ): Result<Unit> = api.report(reviewId, BackendReviewReportDto(reason.toQuery(), note?.trim()?.takeIf { it.isNotEmpty() }))

    override suspend fun reply(
        reviewId: String,
        text: String,
    ): Result<ReviewReply> = api.putReply(reviewId, text.trim()).map { it.toDomain() }

    override suspend fun deleteReply(reviewId: String): Result<Unit> = api.deleteReply(reviewId)

    private companion object {
        const val SUMMARIES_PER_CALL = 50
    }
}
