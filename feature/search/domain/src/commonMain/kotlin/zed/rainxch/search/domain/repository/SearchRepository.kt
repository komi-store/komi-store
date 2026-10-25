package zed.rainxch.search.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.repository.PaginatedDiscoveryRepositories
import zed.rainxch.search.domain.model.ExploreResult
import zed.rainxch.search.domain.model.ProgrammingLanguage
import zed.rainxch.search.domain.model.SearchSource
import zed.rainxch.search.domain.model.SortBy
import zed.rainxch.search.domain.model.SortOrder

interface SearchRepository {
    fun searchRepositories(
        query: String,
        platform: DiscoveryPlatform,
        language: ProgrammingLanguage,
        sortBy: SortBy,
        sortOrder: SortOrder,
        page: Int,
        source: SearchSource = SearchSource.GitHub,
    ): Flow<PaginatedDiscoveryRepositories>

    suspend fun exploreFromGithub(
        query: String,
        platform: DiscoveryPlatform,
        page: Int,
    ): ExploreResult
}
