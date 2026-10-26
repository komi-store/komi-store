package zed.rainxch.core.data.mappers

import zed.rainxch.core.data.dto.BackendReviewDraftDto
import zed.rainxch.core.domain.model.reviews.ModerationStatus
import zed.rainxch.core.domain.model.reviews.ReportReason
import zed.rainxch.core.domain.model.reviews.ReviewDraft
import zed.rainxch.core.domain.model.reviews.ReviewSort

fun ReviewDraft.toDto(): BackendReviewDraftDto =
    BackendReviewDraftDto(
        rating = rating,
        text = text?.trim()?.takeIf { it.isNotEmpty() },
        version = version,
        platform = platform?.name?.lowercase(),
        appLabel = appLabel,
    )

fun ModerationStatus.toQuery(): String = name.lowercase()

fun ReviewSort.toQuery(): String = name.lowercase()

fun ReportReason.toQuery(): String = name.lowercase()
