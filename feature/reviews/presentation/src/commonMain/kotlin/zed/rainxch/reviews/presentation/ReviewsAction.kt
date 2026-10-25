package zed.rainxch.reviews.presentation

import zed.rainxch.core.domain.model.reviews.ReportReason
import zed.rainxch.core.domain.model.reviews.ReviewSort
import zed.rainxch.core.domain.model.reviews.ReviewVote

sealed interface ReviewsAction {
    data object OnBackClick : ReviewsAction

    data object OnRetry : ReviewsAction

    data object OnLoadMore : ReviewsAction

    data class OnSortChange(
        val sort: ReviewSort,
    ) : ReviewsAction

    data class OnRatingFilterChange(
        val rating: Int?,
    ) : ReviewsAction

    data object OnSignInClick : ReviewsAction

    data object OnOpenModeration : ReviewsAction

    data object OnOpenComposer : ReviewsAction

    data object OnDismissComposer : ReviewsAction

    data class OnComposerRatingChange(
        val rating: Int,
    ) : ReviewsAction

    data class OnComposerTextChange(
        val text: String,
    ) : ReviewsAction

    data object OnSubmitReview : ReviewsAction

    data object OnDeleteReview : ReviewsAction

    data object OnConfirmDelete : ReviewsAction

    data object OnDismissDelete : ReviewsAction

    data class OnVote(
        val reviewId: String,
        val vote: ReviewVote,
    ) : ReviewsAction

    data class OnOpenReport(
        val reviewId: String,
    ) : ReviewsAction

    data class OnReportReasonChange(
        val reason: ReportReason,
    ) : ReviewsAction

    data class OnReportNoteChange(
        val note: String,
    ) : ReviewsAction

    data object OnSendReport : ReviewsAction

    data object OnDismissReport : ReviewsAction

    data class OnOpenReply(
        val reviewId: String,
    ) : ReviewsAction

    data class OnReplyTextChange(
        val text: String,
    ) : ReviewsAction

    data object OnSendReply : ReviewsAction

    data object OnDismissReply : ReviewsAction

    data class OnDeleteReply(
        val reviewId: String,
    ) : ReviewsAction
}
