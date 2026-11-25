package zed.rainxch.apps.presentation.model

import zed.rainxch.core.domain.system.RepoMatchSource

data class LinkSuggestionUi(
    val owner: String,
    val repo: String,
    val confidencePercent: Int,
    val source: RepoMatchSource,
    val stars: Int?,
    val description: String?,
    val sourceHost: String?,
)
