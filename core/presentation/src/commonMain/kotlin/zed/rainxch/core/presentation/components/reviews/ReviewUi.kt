package zed.rainxch.core.presentation.components.reviews

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.core.domain.model.reviews.RatingSummary
import zed.rainxch.core.domain.model.reviews.Review
import zed.rainxch.core.domain.model.reviews.ReviewPlatform
import zed.rainxch.core.domain.model.reviews.ReviewVote

@Immutable
data class ReviewUi(
    val id: String,
    val rating: Int,
    val text: String?,
    val version: String?,
    val platform: ReviewPlatform?,
    val appLabel: String?,
    val authorLogin: String,
    val authorAvatarUrl: String?,
    val createdAt: String?,
    val edited: Boolean,
    val score: Int,
    val reply: ReviewReplyUi?,
    val isOwn: Boolean,
    val vote: ReviewVote?,
    val isHidden: Boolean,
)

@Immutable
data class ReviewReplyUi(
    val authorLogin: String,
    val authorAvatarUrl: String?,
    val text: String,
    val createdAt: String?,
)

// starCounts runs from five stars down to one, the order the bars are drawn in.
@Immutable
data class RatingSummaryUi(
    val average: Double?,
    val count: Int,
    val starCounts: ImmutableList<Int>,
)

fun Review.toUi(
    viewerId: Long?,
    votes: Map<String, ReviewVote>,
): ReviewUi =
    ReviewUi(
        id = id,
        rating = rating,
        text = text,
        version = version,
        platform = platform,
        appLabel = appLabel,
        authorLogin = author.login,
        authorAvatarUrl = author.avatarUrl,
        createdAt = (updatedAt ?: createdAt)?.toString(),
        edited = edited,
        score = score,
        reply =
            reply?.let {
                ReviewReplyUi(
                    authorLogin = it.author.login,
                    authorAvatarUrl = it.author.avatarUrl,
                    text = it.text,
                    createdAt = (it.updatedAt ?: it.createdAt)?.toString(),
                )
            },
        isOwn = viewerId != null && author.id == viewerId,
        vote = votes[id],
        isHidden = hidden,
    )

fun RatingSummary.toUi(): RatingSummaryUi =
    RatingSummaryUi(
        average = average,
        count = count,
        starCounts = (5 downTo 1).map { histogram[it] ?: 0 }.toImmutableList(),
    )
