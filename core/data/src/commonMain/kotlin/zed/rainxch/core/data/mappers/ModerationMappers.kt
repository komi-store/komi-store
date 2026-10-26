package zed.rainxch.core.data.mappers

import zed.rainxch.core.data.dto.BackendModerationItemDto
import zed.rainxch.core.data.dto.BackendModerationQueueDto
import zed.rainxch.core.data.dto.BackendModerationReportDto
import zed.rainxch.core.domain.model.reviews.ModerationItem
import zed.rainxch.core.domain.model.reviews.ModerationPage
import zed.rainxch.core.domain.model.reviews.ModerationReport
import zed.rainxch.core.domain.model.reviews.ReportReason
import zed.rainxch.core.domain.model.reviews.ReviewTarget

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

private const val GITHUB_HOST = "github.com"
