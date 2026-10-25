package zed.rainxch.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import zed.rainxch.core.data.dto.BackendReviewReportDto
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toDto
import zed.rainxch.core.data.mappers.toQuery
import zed.rainxch.core.data.network.BackendReviewsApi
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
import zed.rainxch.core.domain.repository.ReviewsRepository

class ReviewsRepositoryImpl(
    private val api: BackendReviewsApi,
) : ReviewsRepository {
    private val _changes = MutableSharedFlow<ReviewTarget>(extraBufferCapacity = 8)
    override val changes: Flow<ReviewTarget> = _changes.asSharedFlow()

    override suspend fun getReviews(
        target: ReviewTarget,
        sort: ReviewSort,
        rating: Int?,
        page: Int,
    ): Result<ReviewsPage> = api.getReviews(target, sort.toQuery(), rating, page).map { it.toDomain() }

    // The backend takes one host per call, so a mixed list (GitHub and Codeberg rows) is fetched per host.
    override suspend fun getSummaries(targets: List<ReviewTarget>): Result<Map<ReviewTarget, RepoRating>> {
        val ratings = mutableMapOf<ReviewTarget, RepoRating>()
        val calls =
            targets.distinct().groupBy { it.host }.flatMap { (host, group) ->
                group.chunked(SUMMARIES_PER_CALL).map { host to it }
            }
        for ((host, chunk) in calls) {
            val byKey = chunk.associateBy { "${it.owner}/${it.name}".lowercase() }
            val summaries = api.getSummaries(byKey.keys.toList(), host).getOrElse { return Result.failure(it) }.summaries
            summaries.forEach { (key, rating) -> byKey[key.lowercase()]?.let { ratings[it] = rating.toDomain() } }
        }
        return Result.success(ratings)
    }

    override suspend fun getMine(target: ReviewTarget): Result<MyReviewState> = api.getMine(target).map { it.toDomain() }

    override suspend fun submit(
        target: ReviewTarget,
        draft: ReviewDraft,
    ): Result<Review> = api.putReview(target, draft.toDto()).map { it.toDomain() }.onSuccess { _changes.emit(target) }

    override suspend fun deleteMine(target: ReviewTarget): Result<Unit> = api.deleteReview(target).onSuccess { _changes.emit(target) }

    override suspend fun setVote(
        reviewId: String,
        vote: ReviewVote?,
    ): Result<Unit> = api.setVote(reviewId, vote?.value)

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

    override suspend fun getModerationQueue(
        status: ModerationStatus,
        page: Int,
    ): Result<ModerationPage> = api.getModerationQueue(status.toQuery(), page).map { it.toDomain() }

    override suspend fun moderate(
        reviewId: String,
        decision: ModerationDecision,
    ): Result<Unit> =
        when (decision) {
            ModerationDecision.DELETE -> api.deleteAsModerator(reviewId)
            else -> api.moderate(reviewId, decision.name.lowercase())
        }

    override suspend fun setBanned(
        githubId: Long,
        banned: Boolean,
    ): Result<Unit> = api.setBanned(githubId, banned)

    private companion object {
        const val SUMMARIES_PER_CALL = 50
    }
}
