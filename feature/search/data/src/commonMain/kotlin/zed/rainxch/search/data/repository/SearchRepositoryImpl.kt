package zed.rainxch.search.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.SEARCH_RESULTS
import zed.rainxch.core.data.mappers.toSummary
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.ForgejoClientRegistry
import zed.rainxch.core.data.network.asNetworkFailure
import zed.rainxch.core.data.network.searchExplore
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.repository.PaginatedDiscoveryRepositories
import zed.rainxch.search.data.mappers.toSummary
import zed.rainxch.search.domain.model.ExploreResult
import zed.rainxch.search.domain.model.SearchRequest
import zed.rainxch.search.domain.model.SearchSource
import zed.rainxch.search.domain.repository.SearchRepository

class SearchRepositoryImpl internal constructor(
    private val cacheManager: CacheManager,
    private val forgejoClientRegistry: ForgejoClientRegistry,
    private val backendApiClient: BackendApiClient,
    private val backendSearch: BackendSearch,
    private val githubSearch: GithubSearch,
) : SearchRepository {
    override fun searchRepositories(
        request: SearchRequest,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories> =
        channelFlow {
            val source = request.source
            if (source is SearchSource.Forgejo) {
                send(forgejoSearch(source.host, request.query, page))
                return@channelFlow
            }

            val cacheKey = request.cacheKey(page)

            val privateMatches =
                if (page == 1 && request.query.isNotBlank()) {
                    githubSearch.privateMatches(request)
                } else {
                    emptyList()
                }

            val cached = cacheManager.get<PaginatedDiscoveryRepositories>(cacheKey)
            if (cached != null) {
                send(cached.prepend(privateMatches))
                return@channelFlow
            }

            val backendResult = backendSearch.search(request, page)
            if (backendResult != null) {
                cacheManager.put(cacheKey, backendResult, SEARCH_RESULTS)
                send(backendResult.prepend(privateMatches))
                return@channelFlow
            }

            send(githubSearch.fallback(request, page, cacheKey).prepend(privateMatches))
        }.flowOn(Dispatchers.IO)

    override suspend fun exploreFromGithub(
        query: String,
        platform: DiscoveryPlatform,
        page: Int,
    ): ExploreResult {
        val response =
            backendApiClient
                .searchExplore(
                    query = query,
                    platform = platform.backendSlug(),
                    page = page,
                ).getOrElse { throw it.asNetworkFailure() }

        return ExploreResult(
            repos = response.items.map { it.toSummary() },
            page = response.page,
            hasMore = response.hasMore,
        )
    }

    private suspend fun forgejoSearch(
        host: String,
        query: String,
        page: Int,
    ): PaginatedDiscoveryRepositories {
        val client = forgejoClientRegistry.clientFor(host)
        val result = client.searchRepositories(query = query, page = page, limit = FORGEJO_PAGE_SIZE)
        val repos = result.getOrNull()?.data.orEmpty()
        return PaginatedDiscoveryRepositories(
            repos = repos.map { it.toSummary(host) },
            hasMore = repos.size >= FORGEJO_PAGE_SIZE,
            nextPageIndex = page + 1,
            totalCount = null,
            passthroughAttempted = false,
        )
    }

    private fun SearchRequest.cacheKey(page: Int): String {
        val queryHash =
            query
                .trim()
                .lowercase()
                .hashCode()
                .toUInt()
                .toString(HEX_RADIX)
        return "search:$queryHash:${platform.name}:${language.name}:${sortBy.name}:${sortOrder.name}:page$page"
    }

    private fun PaginatedDiscoveryRepositories.prepend(extra: List<GithubRepoSummary>): PaginatedDiscoveryRepositories {
        val existingIds = repos.mapTo(mutableSetOf()) { it.id }
        val deduped = extra.filter { it.id !in existingIds }
        return if (deduped.isEmpty()) {
            this
        } else {
            copy(
                repos = deduped + repos,
                totalCount = totalCount?.plus(deduped.size),
            )
        }
    }

    private companion object {
        const val FORGEJO_PAGE_SIZE = 100
        const val HEX_RADIX = 16
    }
}
