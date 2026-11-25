package zed.rainxch.apps.presentation.mappers

import zed.rainxch.apps.domain.model.ImportResult
import zed.rainxch.apps.presentation.model.ImportSummaryUi

fun ImportResult.toUi(): ImportSummaryUi =
    ImportSummaryUi(
        imported = imported,
        skipped = skipped,
        failed = failed,
        nonGitHubSkipped = nonGitHubSkipped,
        notInstalled = notInstalled,
        importedItems = importedItems,
        skippedItems = skippedItems,
        nonGitHubItems = nonGitHubItems,
        notInstalledItems = notInstalledItems,
        failedItems = failedItems,
        sourceFormat = sourceFormat,
        unknownFormatPreview = unknownFormatPreview,
    )
