package zed.rainxch.core.data.mappers

import zed.rainxch.core.data.dto.BackendMyReviewDto
import zed.rainxch.core.data.dto.BackendRatingSummaryDto
import zed.rainxch.core.data.dto.BackendRepoRatingDto
import zed.rainxch.core.data.dto.BackendReviewAuthorDto
import zed.rainxch.core.data.dto.BackendReviewDto
import zed.rainxch.core.data.dto.BackendReviewReplyDto
import zed.rainxch.core.data.dto.BackendReviewsPageDto
import zed.rainxch.core.domain.model.reviews.CannotReviewReason
import zed.rainxch.core.domain.model.reviews.MyReviewState
import zed.rainxch.core.domain.model.reviews.REVIEW_RATING_RANGE
import zed.rainxch.core.domain.model.reviews.RatingSummary
import zed.rainxch.core.domain.model.reviews.RepoRating
import zed.rainxch.core.domain.model.reviews.Review
import zed.rainxch.core.domain.model.reviews.ReviewAuthor
import zed.rainxch.core.domain.model.reviews.ReviewPlatform
import zed.rainxch.core.domain.model.reviews.ReviewReply
import zed.rainxch.core.domain.model.reviews.ReviewVote
import zed.rainxch.core.domain.model.reviews.ReviewsPage
import kotlin.time.Instant

fun BackendReviewsPageDto.toDomain(): ReviewsPage =
    ReviewsPage(
        summary = summary.toDomain(),
        reviews = reviews.map { it.toDomain() },
        page = page,
        hasMore = hasMore,
    )

fun BackendRatingSummaryDto.toDomain(): RatingSummary =
    RatingSummary(
        average = average,
        count = count,
        histogram =
            histogram
                .mapNotNull { (stars, count) -> stars.toIntOrNull()?.takeIf { it in REVIEW_RATING_RANGE }?.let { it to count } }
                .toMap(),
    )

fun BackendRepoRatingDto.toDomain(): RepoRating = RepoRating(average = average, count = count)

fun BackendReviewDto.toDomain(): Review =
    Review(
        id = id,
        rating = rating,
        text = text?.takeIf { it.isNotBlank() },
        version = version?.takeIf { it.isNotBlank() },
        platform = platform.toReviewPlatform(),
        appLabel = appLabel?.takeIf { it.isNotBlank() },
        author = author.toDomain(),
        createdAt = createdAt.toInstantOrNull(),
        updatedAt = updatedAt.toInstantOrNull(),
        edited = edited,
        score = score,
        hidden = hidden,
        reply = reply?.toDomain(),
    )

fun BackendReviewAuthorDto.toDomain(): ReviewAuthor = ReviewAuthor(id = id, login = login, avatarUrl = avatarUrl)

fun BackendReviewReplyDto.toDomain(): ReviewReply =
    ReviewReply(
        author = author.toDomain(),
        text = text,
        createdAt = createdAt.toInstantOrNull(),
        updatedAt = updatedAt.toInstantOrNull(),
    )

fun BackendMyReviewDto.toDomain(): MyReviewState =
    MyReviewState(
        review = review?.toDomain(),
        votes = votes.mapNotNull { (id, value) -> ReviewVote.entries.firstOrNull { it.value == value }?.let { id to it } }.toMap(),
        canReview = canReview,
        cannotReviewReason =
            when (cannotReviewReason) {
                "account_too_new" -> CannotReviewReason.ACCOUNT_TOO_NEW
                "own_repo" -> CannotReviewReason.OWN_REPO
                "banned" -> CannotReviewReason.BANNED
                else -> null
            },
        canReply = canReply,
        isModerator = isModerator,
    )

private fun String?.toReviewPlatform(): ReviewPlatform? = ReviewPlatform.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }

internal fun String?.toInstantOrNull(): Instant? =
    this?.let {
        try {
            Instant.parse(it)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
