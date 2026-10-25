package zed.rainxch.core.domain.model.reviews

import kotlin.time.Instant

enum class ModerationStatus {
    REPORTED,
    HIDDEN,
}

enum class ModerationDecision {
    HIDE,
    UNHIDE,
    DISMISS,
    DELETE,
}

data class ModerationReport(
    val reason: ReportReason?,
    val note: String?,
    val createdAt: Instant?,
)

data class ModerationItem(
    val review: Review,
    val repo: ReviewTarget,
    val reportCount: Int,
    val reports: List<ModerationReport>,
    val lastReportedAt: Instant?,
    val hidden: Boolean,
    val hiddenReason: String?,
)

data class ModerationPage(
    val items: List<ModerationItem>,
    val page: Int,
    val hasMore: Boolean,
)
