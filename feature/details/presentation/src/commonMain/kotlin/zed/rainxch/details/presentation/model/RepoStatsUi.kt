package zed.rainxch.details.presentation.model

data class RepoStatsUi(
    val stars: Int,
    val forks: Int,
    val openIssues: Int,
    val license: String?,
    val totalDownloads: Long,
)
