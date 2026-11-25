package zed.rainxch.search.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.repository.PaginatedDiscoveryRepositories
import zed.rainxch.search.domain.model.ExploreResult
import zed.rainxch.search.domain.model.SearchRequest

interface SearchRepository {
    fun searchRepositories(
        request: SearchRequest,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories>

    suspend fun exploreFromGithub(
        query: String,
        platform: DiscoveryPlatform,
        page: Int,
    ): ExploreResult
}
