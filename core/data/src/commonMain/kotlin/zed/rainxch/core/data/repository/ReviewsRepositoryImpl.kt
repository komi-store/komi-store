package zed.rainxch.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import zed.rainxch.core.data.dto.BackendReviewReportDto
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toDto
import zed.rainxch.core.data.mappers.toQuery
import zed.rainxch.core.data.network.BackendReviewModerationApi
import zed.rainxch.core.data.network.BackendReviewsApi
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
import zed.rainxch.core.domain.repository.ReviewModeration
import zed.rainxch.core.domain.repository.ReviewsRepository
import kotlin.time.Clock

class ReviewsRepositoryImpl(
    private val api: BackendReviewsApi,
    moderationApi: BackendReviewModerationApi,
) : ReviewsRepository,
    ReviewModeration by ReviewModerationImpl(moderationApi) {
    private val _changes = MutableSharedFlow<ReviewTarget>(extraBufferCapacity = 8)
    override val changes: Flow<ReviewTarget> = _changes.asSharedFlow()

    // The public list is a CDN copy up to five minutes old, so reads skip it that long after the viewer's own write.
    private var freshUntil: Map<ReviewTarget, Long> = emptyMap()

    override suspend fun getReviews(
        target: ReviewTarget,
        sort: ReviewSort,
        rating: Int?,
        page: Int,
    ): Result<ReviewsPage> {
        val now = Clock.System.now().toEpochMilliseconds()
        val fresh = now.takeIf { (freshUntil[target] ?: 0L) > it }
        return api.getReviews(target, sort.toQuery(), rating, page, fresh).map { it.toDomain() }
    }

    // One host per call; a failing host loses only its own rows, and the call fails only when every host does.
    override suspend fun getSummaries(targets: List<ReviewTarget>): Result<Map<ReviewTarget, RepoRating>> {
        val ratings = mutableMapOf<ReviewTarget, RepoRating>()
        val calls =
            targets.distinct().groupBy { it.host }.flatMap { (host, group) ->
                group.chunked(SUMMARIES_PER_CALL).map { host to it }
            }
        var lastFailure: Throwable? = null
        var anySucceeded = false
        for ((host, chunk) in calls) {
            val byKey = chunk.associateBy { "${it.owner}/${it.name}" }
            api
                .getSummaries(byKey.keys.toList(), host)
                .onSuccess { answer ->
                    anySucceeded = true
                    answer.summaries.forEach { (key, rating) -> byKey[key]?.let { ratings[it] = rating.toDomain() } }
                }.onFailure { lastFailure = it }
        }
        val failure = lastFailure
        return if (failure != null && !anySucceeded) Result.failure(failure) else Result.success(ratings)
    }

    override suspend fun getMine(target: ReviewTarget): Result<MyReviewState> = api.getMine(target).map { it.toDomain() }

    override suspend fun submit(
        target: ReviewTarget,
        draft: ReviewDraft,
    ): Result<Review> = api.putReview(target, draft.toDto()).map { it.toDomain() }.onSuccess { wrote(target) }

    override suspend fun deleteMine(target: ReviewTarget): Result<Unit> = api.deleteReview(target).onSuccess { wrote(target) }

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

    private suspend fun wrote(target: ReviewTarget) {
        freshUntil = freshUntil + (target to Clock.System.now().toEpochMilliseconds() + CDN_LAG_MILLIS)
        _changes.emit(target)
    }

    private companion object {
        const val SUMMARIES_PER_CALL = 50

        // The list's s-maxage.
        const val CDN_LAG_MILLIS = 5 * 60 * 1000L
    }
}
