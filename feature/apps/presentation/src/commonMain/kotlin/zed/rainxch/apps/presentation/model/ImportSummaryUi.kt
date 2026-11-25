package zed.rainxch.apps.presentation.model

import kotlinx.collections.immutable.ImmutableList
import zed.rainxch.apps.domain.model.ImportFormat

data class ImportSummaryUi(
    val imported: Int,
    val skipped: Int,
    val failed: Int,
    val nonGitHubSkipped: Int,
    val notInstalled: Int,
    val importedItems: ImmutableList<String>,
    val skippedItems: ImmutableList<String>,
    val nonGitHubItems: ImmutableList<String>,
    val notInstalledItems: ImmutableList<String>,
    val failedItems: ImmutableList<String>,
    val sourceFormat: ImportFormat,
    val unknownFormatPreview: String?,
)
