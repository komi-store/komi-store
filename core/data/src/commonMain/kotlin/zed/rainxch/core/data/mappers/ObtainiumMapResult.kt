package zed.rainxch.core.data.mappers

import zed.rainxch.core.domain.model.transfer.ExportedApp

data class ObtainiumMapResult(
    val exported: ExportedApp?,
    val nonGitHubLabel: String?,
    val unsupportedFailureLabel: String?,
)
