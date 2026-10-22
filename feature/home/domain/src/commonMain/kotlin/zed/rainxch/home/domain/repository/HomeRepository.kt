package zed.rainxch.home.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.repository.PaginatedDiscoveryRepositories
import zed.rainxch.home.domain.model.TopicCategory

interface HomeRepository {
    fun getTrendingRepositories(
        platforms: Set<DiscoveryPlatform>,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories>

    fun getHotReleaseRepositories(
        platforms: Set<DiscoveryPlatform>,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories>

    fun getMostPopular(
        platforms: Set<DiscoveryPlatform>,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories>

    fun searchByTopic(
        searchKeywords: String,
        platforms: Set<DiscoveryPlatform>,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories>

    fun getTopicRepositories(
        topic: TopicCategory,
        platforms: Set<DiscoveryPlatform>,
    ): Flow<PaginatedDiscoveryRepositories>

    suspend fun getRepositoryById(id: Long): GithubRepoSummary?
}
