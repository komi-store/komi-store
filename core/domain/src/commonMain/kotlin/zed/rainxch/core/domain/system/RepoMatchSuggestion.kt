package zed.rainxch.core.domain.system

data class RepoMatchSuggestion(
    val owner: String,
    val repo: String,
    val confidence: Double,
    val source: RepoMatchSource,
    val stars: Int? = null,
    val description: String? = null,
    val sourceHost: String? = null,
)
