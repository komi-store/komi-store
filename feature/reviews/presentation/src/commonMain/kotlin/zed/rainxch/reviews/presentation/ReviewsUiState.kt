package zed.rainxch.reviews.presentation

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.core.domain.model.reviews.CannotReviewReason
import zed.rainxch.core.domain.model.reviews.ReportReason
import zed.rainxch.core.domain.model.reviews.ReviewSort
import zed.rainxch.core.presentation.components.reviews.RatingSummaryUi
import zed.rainxch.core.presentation.components.reviews.ReviewUi

@Immutable
data class ReviewsUiState(
    val summary: RatingSummaryUi? = null,
    val reviews: ImmutableList<ReviewUi> = persistentListOf(),
    val sort: ReviewSort = ReviewSort.TOP,
    val ratingFilter: Int? = null,
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val endReached: Boolean = false,
    val page: Int = 1,
    val errorMessage: String? = null,
    val isUnavailable: Boolean = false,
    val isLoggedIn: Boolean = false,
    val mine: ReviewUi? = null,
    val canReview: Boolean = false,
    val cannotReviewReason: CannotReviewReason? = null,
    val canReply: Boolean = false,
    val isModerator: Boolean = false,
    val composer: ComposerState? = null,
    val report: ReportState? = null,
    val reply: ReplyState? = null,
    val isConfirmingDelete: Boolean = false,
) {
    // The viewer's own review is pinned above the list only while it matches the star filter.
    val pinnedMine: ReviewUi? get() = mine?.takeIf { ratingFilter == null || it.rating == ratingFilter }

    val isEmpty: Boolean get() = reviews.isEmpty() && pinnedMine == null && !isLoading
}

@Immutable
data class ComposerState(
    val rating: Int,
    val text: String,
    val version: String?,
    val isEditing: Boolean,
    val isSubmitting: Boolean = false,
)

@Immutable
data class ReportState(
    val reviewId: String,
    val reason: ReportReason? = null,
    val note: String = "",
    val isSending: Boolean = false,
)

@Immutable
data class ReplyState(
    val reviewId: String,
    val text: String,
    val isSending: Boolean = false,
)
