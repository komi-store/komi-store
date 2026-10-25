package zed.rainxch.reviews.presentation.moderation

import zed.rainxch.core.domain.model.reviews.ModerationDecision
import zed.rainxch.core.domain.model.reviews.ModerationStatus
import zed.rainxch.core.domain.model.reviews.ReviewTarget

sealed interface ModerationAction {
    data object OnBackClick : ModerationAction

    data object OnRetry : ModerationAction

    data object OnLoadMore : ModerationAction

    data class OnStatusChange(
        val status: ModerationStatus,
    ) : ModerationAction

    data class OnOpenRepo(
        val repo: ReviewTarget,
    ) : ModerationAction

    // DELETE asks first; the other decisions run at once.
    data class OnDecision(
        val reviewId: String,
        val decision: ModerationDecision,
    ) : ModerationAction

    data class OnBanClick(
        val reviewId: String,
    ) : ModerationAction

    data object OnConfirm : ModerationAction

    data object OnDismissConfirm : ModerationAction
}
