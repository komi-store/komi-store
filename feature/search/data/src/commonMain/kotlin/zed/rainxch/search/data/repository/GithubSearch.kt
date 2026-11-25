package zed.rainxch.search.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.SEARCH_RESULTS
import zed.rainxch.core.data.datasource.TokenStore
import zed.rainxch.core.data.dto.GithubRepoNetworkModel
import zed.rainxch.core.data.dto.GithubRepoSearchResponse
import zed.rainxch.core.data.mappers.toSummary
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.asNetworkFailure
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.NetworkException
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.repository.PaginatedDiscoveryRepositories
import zed.rainxch.search.domain.model.ProgrammingLanguage
import zed.rainxch.search.domain.model.SearchRequest

// GitHub's own /search/repositories: the fallback when the backend can't serve a search, and a signed-in user's private matches.
internal class GithubSearch(
    private val clientProvider: GitHubClientProvider,
    private val cacheManager: CacheManager,
    private val tokenStore: TokenStore,
    private val installerCheck: InstallerReleaseCheck,
) {
    private val httpClient: HttpClient get() = clientProvider.client

    suspend fun privateMatches(request: SearchRequest): List<GithubRepoSummary> {
        if (!tokenStore.isSignedIn()) return emptyList()

        val privateItems = searchPrivate(privateQuery(request)).filter { it.private }
        return if (request.platform == DiscoveryPlatform.All) {
            privateItems.map { it.toSummary() }
        } else {
            installerCheck.verifyBatch(privateItems, request.platform)
        }
    }

    // A failed page ends the results instead of failing the search; only a rate limit reaches the caller.
    suspend fun fallback(
        request: SearchRequest,
        page: Int,
        cacheKey: String,
    ): PaginatedDiscoveryRepositories =
        try {
            firstPageWithInstallers(request, page, cacheKey)
        } catch (_: NetworkException) {
            PaginatedDiscoveryRepositories(repos = emptyList(), hasMore = false, nextPageIndex = page)
        } catch (_: LocalStoreException) {
            PaginatedDiscoveryRepositories(repos = emptyList(), hasMore = false, nextPageIndex = page)
        }

    private suspend fun searchPrivate(query: String): List<GithubRepoNetworkModel> =
        try {
            httpClient
                .executeRequest<GithubRepoSearchResponse> {
                    get("/search/repositories") {
                        parameter("q", query)
                        parameter("per_page", PRIVATE_MATCHES_PER_PAGE)
                    }
                }.getOrNull()
                ?.items
                .orEmpty()
        } catch (_: RateLimitException) {
            emptyList()
        }

    private suspend fun firstPageWithInstallers(
        request: SearchRequest,
        page: Int,
        cacheKey: String,
    ): PaginatedDiscoveryRepositories {
        val searchQuery = buildSearchQuery(request.query, request.language)
        var currentPage = page
        repeat(MAX_AUTO_SKIP_PAGES + 1) {
            currentCoroutineContext().ensureActive()
            val response = fetchPage(searchQuery, request, currentPage)
            val settled = settledPage(response, request.platform, currentPage)
            if (settled != null) {
                if (settled.repos.isNotEmpty()) cacheManager.put(cacheKey, settled, SEARCH_RESULTS)
                return settled
            }
            currentPage++
        }
        return PaginatedDiscoveryRepositories(
            repos = emptyList(),
            hasMore = true,
            nextPageIndex = currentPage + 1,
            totalCount = null,
        )
    }

    private suspend fun fetchPage(
        searchQuery: String,
        request: SearchRequest,
        page: Int,
    ): GithubRepoSearchResponse {
        val sort = request.sortBy.toGithubSortParam()
        return httpClient
            .executeRequest<GithubRepoSearchResponse> {
                get("/search/repositories") {
                    parameter("q", searchQuery)
                    parameter("per_page", PER_PAGE)
                    parameter("page", page)
                    if (sort != null) {
                        parameter("sort", sort)
                        parameter("order", request.sortOrder.toGithubParam())
                    }
                }
            }.getOrElse { throw it.asNetworkFailure() }
    }

    // The page to show, or null when this page had no installers and more pages follow.
    private suspend fun settledPage(
        response: GithubRepoSearchResponse,
        platform: DiscoveryPlatform,
        page: Int,
    ): PaginatedDiscoveryRepositories? {
        val total = response.totalCount
        val verified = if (response.items.isEmpty()) emptyList() else installerCheck.verifyBatch(response.items, platform)
        val hasMore = response.items.isNotEmpty() && page * PER_PAGE < total
        return when {
            verified.isNotEmpty() -> {
                PaginatedDiscoveryRepositories(repos = verified, hasMore = hasMore, nextPageIndex = page + 1, totalCount = total)
            }

            !hasMore -> {
                PaginatedDiscoveryRepositories(repos = emptyList(), hasMore = false, nextPageIndex = page + 1, totalCount = total)
            }

            else -> {
                null
            }
        }
    }

    private fun privateQuery(request: SearchRequest): String {
        val safeQuery = request.query.trim().replace("\"", "")
        return buildString {
            append("\"$safeQuery\" in:name,description fork:true is:private")
            if (request.language != ProgrammingLanguage.All && request.language.queryValue != null) {
                append(" language:${request.language.queryValue}")
            }
        }
    }

    private fun buildSearchQuery(
        userQuery: String,
        language: ProgrammingLanguage,
    ): String {
        val clean = userQuery.trim()
        val q =
            if (clean.isBlank()) {
                "stars:>100"
            } else {
                "\"$clean\""
            }
        val scope = " in:name,description"
        val common = " archived:false fork:true"

        val languageFilter =
            if (language != ProgrammingLanguage.All && language.queryValue != null) {
                " language:${language.queryValue}"
            } else {
                ""
            }

        return ("$q$scope$common" + languageFilter).trim()
    }

    private companion object {
        const val PER_PAGE = 100
        const val PRIVATE_MATCHES_PER_PAGE = 20
        const val MAX_AUTO_SKIP_PAGES = 3
    }
}
