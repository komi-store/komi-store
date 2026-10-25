package zed.rainxch.core.data.mappers

import zed.rainxch.core.data.dto.BackendModerationItemDto
import zed.rainxch.core.data.dto.BackendModerationQueueDto
import zed.rainxch.core.data.dto.BackendModerationReportDto
import zed.rainxch.core.data.dto.BackendMyReviewDto
import zed.rainxch.core.data.dto.BackendRatingSummaryDto
import zed.rainxch.core.data.dto.BackendRepoRatingDto
import zed.rainxch.core.data.dto.BackendReviewAuthorDto
import zed.rainxch.core.data.dto.BackendReviewDraftDto
import zed.rainxch.core.data.dto.BackendReviewDto
import zed.rainxch.core.data.dto.BackendReviewReplyDto
import zed.rainxch.core.data.dto.BackendReviewsPageDto
import zed.rainxch.core.domain.model.reviews.CannotReviewReason
import zed.rainxch.core.domain.model.reviews.ModerationItem
import zed.rainxch.core.domain.model.reviews.ModerationPage
import zed.rainxch.core.domain.model.reviews.ModerationReport
import zed.rainxch.core.domain.model.reviews.ModerationStatus
import zed.rainxch.core.domain.model.reviews.MyReviewState
import zed.rainxch.core.domain.model.reviews.REVIEW_RATING_RANGE
import zed.rainxch.core.domain.model.reviews.RatingSummary
import zed.rainxch.core.domain.model.reviews.RepoRating
import zed.rainxch.core.domain.model.reviews.ReportReason
import zed.rainxch.core.domain.model.reviews.Review
import zed.rainxch.core.domain.model.reviews.ReviewAuthor
import zed.rainxch.core.domain.model.reviews.ReviewDraft
import zed.rainxch.core.domain.model.reviews.ReviewPlatform
import zed.rainxch.core.domain.model.reviews.ReviewReply
import zed.rainxch.core.domain.model.reviews.ReviewSort
import zed.rainxch.core.domain.model.reviews.ReviewTarget
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

fun ReviewDraft.toDto(): BackendReviewDraftDto =
    BackendReviewDraftDto(
        rating = rating,
        text = text?.trim()?.takeIf { it.isNotEmpty() },
        version = version,
        platform = platform?.name?.lowercase(),
        appLabel = appLabel,
    )

fun BackendModerationQueueDto.toDomain(): ModerationPage =
    ModerationPage(items = items.map { it.toDomain() }, page = page, hasMore = hasMore)

// The queue names GitHub as "github.com"; a ReviewTarget leaves the host null for GitHub.
fun BackendModerationItemDto.toDomain(): ModerationItem =
    ModerationItem(
        review = review.toDomain(),
        repo =
            ReviewTarget(
                owner = repo.owner,
                name = repo.name,
                host = repo.host?.takeUnless { it.equals(GITHUB_HOST, ignoreCase = true) },
            ),
        reportCount = reportCount,
        reports = reports.map { it.toDomain() },
        lastReportedAt = lastReportedAt.toInstantOrNull(),
        hidden = hidden,
        hiddenReason = hiddenReason?.takeIf { it.isNotBlank() },
    )

fun BackendModerationReportDto.toDomain(): ModerationReport =
    ModerationReport(
        reason = ReportReason.entries.firstOrNull { it.name.equals(reason, ignoreCase = true) },
        note = note?.takeIf { it.isNotBlank() },
        createdAt = createdAt.toInstantOrNull(),
    )

fun ModerationStatus.toQuery(): String = name.lowercase()

fun ReviewSort.toQuery(): String = name.lowercase()

fun ReportReason.toQuery(): String = name.lowercase()

private fun String?.toReviewPlatform(): ReviewPlatform? = ReviewPlatform.entries.firstOrNull { it.name.equals(this, ignoreCase = true) }

private fun String?.toInstantOrNull(): Instant? =
    this?.let {
        try {
            Instant.parse(it)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

private const val GITHUB_HOST = "github.com"
