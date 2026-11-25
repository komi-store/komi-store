package zed.rainxch.reviews.presentation

import zed.rainxch.core.domain.model.reviews.ReportReason
import zed.rainxch.core.domain.model.reviews.ReviewSort
import zed.rainxch.core.domain.model.reviews.ReviewVote

sealed interface ReviewsAction {
    sealed interface ComposerAction : ReviewsAction

    sealed interface ReportAction : ReviewsAction

    sealed interface ReplyAction : ReviewsAction

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

    data object OnOpenComposer : ComposerAction

    data object OnDismissComposer : ComposerAction

    data class OnComposerRatingChange(
        val rating: Int,
    ) : ComposerAction

    data class OnComposerTextChange(
        val text: String,
    ) : ComposerAction

    data object OnSubmitReview : ComposerAction

    data object OnDeleteReview : ComposerAction

    data object OnConfirmDelete : ComposerAction

    data object OnDismissDelete : ComposerAction

    data class OnVote(
        val reviewId: String,
        val vote: ReviewVote,
    ) : ReviewsAction

    data class OnOpenReport(
        val reviewId: String,
    ) : ReportAction

    data class OnReportReasonChange(
        val reason: ReportReason,
    ) : ReportAction

    data class OnReportNoteChange(
        val note: String,
    ) : ReportAction

    data object OnSendReport : ReportAction

    data object OnDismissReport : ReportAction

    data class OnOpenReply(
        val reviewId: String,
    ) : ReplyAction

    data class OnReplyTextChange(
        val text: String,
    ) : ReplyAction

    data object OnSendReply : ReplyAction

    data object OnDismissReply : ReplyAction

    data class OnDeleteReply(
        val reviewId: String,
    ) : ReplyAction
}
