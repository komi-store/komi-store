package zed.rainxch.search.data.repository

import zed.rainxch.core.data.datasource.TokenStore
import zed.rainxch.core.data.dto.BackendSearchResponse
import zed.rainxch.core.data.mappers.toSummary
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.search
import zed.rainxch.core.data.network.shouldFallbackToGithubOrRethrow
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.repository.PaginatedDiscoveryRepositories
import zed.rainxch.search.domain.model.SearchRequest
import zed.rainxch.search.domain.model.SortBy

internal class BackendSearch(
    private val backendApiClient: BackendApiClient,
    private val tokenStore: TokenStore,
) {
    // Null when the backend has no answer for this search and GitHub should take it.
    suspend fun search(
        request: SearchRequest,
        page: Int,
    ): PaginatedDiscoveryRepositories? {
        if (request.query.isBlank() || request.sortBy == SortBy.MostForks || request.platform == DiscoveryPlatform.Ios) return null

        val signedIn = tokenStore.isSignedIn()
        val offset = (page - 1) * PAGE_SIZE
        return backendApiClient
            .search(
                query = request.query,
                platform = request.platform.backendSlug(),
                sort = request.sortBy.backendSort(),
                limit = PAGE_SIZE,
                offset = offset,
            ).fold(
                onSuccess = { it.toPage(request.platform, page, offset) },
                onFailure = { e ->
                    if (!shouldFallbackToGithubOrRethrow(e, signedIn)) throw e
                    null
                },
            )
    }

    private fun BackendSearchResponse.toPage(
        platform: DiscoveryPlatform,
        page: Int,
        offset: Int,
    ): PaginatedDiscoveryRepositories {
        val repos = items.map { it.toSummary(platform) }
        return PaginatedDiscoveryRepositories(
            repos = repos,
            hasMore = offset + repos.size < totalHits,
            nextPageIndex = page + 1,
            totalCount = totalHits,
            passthroughAttempted = passthroughAttempted,
        )
    }

    private fun SortBy.backendSort(): String? =
        when (this) {
            SortBy.MostStars -> "stars"
            SortBy.BestMatch -> "relevance"
            SortBy.RecentlyUpdated -> "updated"
            SortBy.RecentlyReleased -> "releases"
            SortBy.MostForks -> null
        }

    private companion object {
        const val PAGE_SIZE = 20
    }
}

internal fun DiscoveryPlatform.backendSlug(): String? =
    when (this) {
        DiscoveryPlatform.Android -> "android"
        DiscoveryPlatform.Windows -> "windows"
        DiscoveryPlatform.Macos -> "macos"
        DiscoveryPlatform.Linux -> "linux"
        DiscoveryPlatform.Ios -> "ios"
        DiscoveryPlatform.All -> null
    }

internal suspend fun TokenStore.isSignedIn(): Boolean = currentToken()?.accessToken?.isNotBlank() == true
