package zed.rainxch.reviews.presentation.moderation

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import zed.rainxch.core.domain.model.reviews.ModerationStatus
import zed.rainxch.core.domain.model.reviews.ReportReason
import zed.rainxch.core.domain.model.reviews.ReviewTarget
import zed.rainxch.core.presentation.components.reviews.ReviewUi

@Immutable
data class ModerationUiState(
    val status: ModerationStatus = ModerationStatus.REPORTED,
    val items: ImmutableList<ModerationItemUi> = persistentListOf(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    val endReached: Boolean = false,
    val page: Int = 1,
    val errorMessage: String? = null,
    val busyIds: ImmutableSet<String> = persistentSetOf(),
    val confirm: ModerationConfirm? = null,
)

@Immutable
data class ModerationItemUi(
    val review: ReviewUi,
    val repo: ReviewTarget,
    val repoLabel: String,
    val authorId: Long,
    val reportCount: Int,
    val reports: ImmutableList<ModerationReportUi>,
    val hiddenReason: String?,
)

@Immutable
data class ModerationReportUi(
    val reason: ReportReason?,
    val note: String?,
)

@Immutable
sealed interface ModerationConfirm {
    val reviewId: String

    data class Delete(
        override val reviewId: String,
    ) : ModerationConfirm

    data class Ban(
        override val reviewId: String,
        val authorId: Long,
        val authorLogin: String,
    ) : ModerationConfirm
}
