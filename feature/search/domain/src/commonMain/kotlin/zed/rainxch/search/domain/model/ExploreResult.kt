package zed.rainxch.search.domain.model

import zed.rainxch.core.domain.model.account.github.GithubRepoSummary

data class ExploreResult(
    val repos: List<GithubRepoSummary>,
    val page: Int,
    val hasMore: Boolean,
)
