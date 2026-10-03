package zed.rainxch.details.presentation.utils

internal fun releaseLineLabel(line: String, repoName: String): String =
    if (line.isBlank()) {
        repoName
    } else {
        line.split('-').joinToString(" ").replaceFirstChar { it.titlecase() }
    }
