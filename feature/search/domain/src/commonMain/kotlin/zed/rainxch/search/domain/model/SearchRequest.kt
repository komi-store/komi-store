package zed.rainxch.search.domain.model

import zed.rainxch.core.domain.model.repository.DiscoveryPlatform

data class SearchRequest(
    val query: String,
    val platform: DiscoveryPlatform,
    val language: ProgrammingLanguage,
    val sortBy: SortBy,
    val sortOrder: SortOrder,
    val source: SearchSource = SearchSource.GitHub,
)
