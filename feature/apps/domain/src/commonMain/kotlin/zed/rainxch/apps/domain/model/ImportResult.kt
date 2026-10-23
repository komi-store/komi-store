package zed.rainxch.apps.domain.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class ImportResult(
    val imported: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    val nonGitHubSkipped: Int = 0,
    val notInstalled: Int = 0,
    val importedItems: ImmutableList<String> = persistentListOf(),
    val skippedItems: ImmutableList<String> = persistentListOf(),
    val nonGitHubItems: ImmutableList<String> = persistentListOf(),
    val notInstalledItems: ImmutableList<String> = persistentListOf(),
    val failedItems: ImmutableList<String> = persistentListOf(),
    val sourceFormat: ImportFormat = ImportFormat.UNKNOWN,
    val unknownFormatPreview: String? = null,
)
