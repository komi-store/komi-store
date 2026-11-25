package zed.rainxch.search.presentation.handlers

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.NetworkException
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.repository.PaginatedDiscoveryRepositories
import zed.rainxch.core.presentation.model.DiscoveryRepositoryUi
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.explore_error
import zed.rainxch.githubstore.core.presentation.res.rate_limit_exceeded
import zed.rainxch.githubstore.core.presentation.res.rate_limit_exceeded_retry_in
import zed.rainxch.githubstore.core.presentation.res.search_failed
import zed.rainxch.search.domain.repository.SearchHistoryRepository
import zed.rainxch.search.domain.repository.SearchRepository
import zed.rainxch.search.presentation.SearchAction
import zed.rainxch.search.presentation.SearchEvent
import zed.rainxch.search.presentation.SearchState
import zed.rainxch.search.presentation.SearchState.ExploreStatus
import zed.rainxch.search.presentation.mappers.toSearchRequest
import zed.rainxch.search.presentation.utils.isEntirelyGithubUrls
import zed.rainxch.search.presentation.utils.parseGithubUrls
import zed.rainxch.search.presentation.withoutResults

// Runs the search and its paging, and the backend's explore pass that fetches more from GitHub.
class SearchResults(
    private val searchRepository: SearchRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val rows: ResultRows,
    private val logger: KomiStoreLogger,
) {
    private var currentSearchJob: Job? = null
    private var currentPage = 1
    private var explorePage = 1
    private var lastExploreQuery = ""
    private val exploreLog = logger.withTag("SearchExplore")

    internal fun observe(store: SearchStore) = rows.observe(store)

    internal fun onAction(
        store: SearchStore,
        action: SearchAction.Results,
    ) {
        when (action) {
            is SearchAction.OnSearchChange -> changeQuery(store, action.query)
            SearchAction.OnSearchImeClick -> submit(store)
            SearchAction.OnClearClick -> store.update { it.withoutResults().copy(query = "", detectedLinks = persistentListOf()) }
            SearchAction.LoadMore -> loadMore(store)
            SearchAction.Retry -> newSearch(store)
            SearchAction.ExploreFromGithub -> explore(store)
            is SearchAction.OnHistoryItemClick -> searchAgain(store, action.query)
        }
    }

    internal fun newSearch(store: SearchStore) {
        currentPage = 1
        performSearch(store, isInitial = true)
    }

    private fun changeQuery(
        store: SearchStore,
        query: String,
    ) {
        val links = parseGithubUrls(query)
        store.update { it.copy(query = query, detectedLinks = links) }
        if (query.isBlank() || isEntirelyGithubUrls(query)) {
            currentSearchJob?.cancel()
            store.update { it.withoutResults() }
        }
    }

    // A query that is only GitHub links opens the first one instead of searching.
    private fun submit(store: SearchStore) {
        val current = store.current
        if (current.detectedLinks.isNotEmpty() && isEntirelyGithubUrls(current.query)) {
            val link = current.detectedLinks.first()
            store.launch { store.send(SearchEvent.NavigateToRepo(link.owner, link.repo)) }
        } else {
            saveToHistory(store, current.query)
            newSearch(store)
        }
    }

    private fun searchAgain(
        store: SearchStore,
        query: String,
    ) {
        store.update { it.copy(query = query, detectedLinks = persistentListOf()) }
        saveToHistory(store, query)
        newSearch(store)
    }

    private fun loadMore(store: SearchStore) {
        val current = store.current
        if (!current.isLoadingMore && !current.isLoading && current.hasMorePages) {
            performSearch(store, isInitial = false)
        }
    }

    private fun saveToHistory(
        store: SearchStore,
        query: String,
    ) {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return
        store.launch { searchHistoryRepository.addSearch(trimmed) }
    }

    private fun performSearch(
        store: SearchStore,
        isInitial: Boolean,
    ) {
        val query = store.current.query.trim()
        if (query.length < MIN_QUERY_LENGTH) {
            if (query.isBlank()) store.update { it.withoutResults() }
            return
        }
        if (isInitial) startOver(store, query)

        currentSearchJob =
            store.launch {
                store.update { it.loading(isInitial) }
                try {
                    collectPages(store)
                    store.update { it.copy(isLoading = false, isLoadingMore = false) }
                } catch (e: CancellationException) {
                    logger.debug("Search cancelled (expected): ${e.message}")
                    throw e
                } catch (e: RateLimitException) {
                    showRateLimit(store, e)
                } catch (e: NetworkException) {
                    showFailure(store, e)
                } catch (e: LocalStoreException) {
                    showFailure(store, e)
                }
            }
    }

    private fun startOver(
        store: SearchStore,
        query: String,
    ) {
        currentSearchJob?.cancel()
        currentPage = 1
        explorePage = 1
        lastExploreQuery = query
        store.update { it.copy(exploreStatus = ExploreStatus.IDLE, passthroughAttempted = null) }
    }

    private suspend fun collectPages(store: SearchStore) {
        searchRepository
            .searchRepositories(request = store.current.toSearchRequest(), page = currentPage)
            .collect { page ->
                currentPage = page.nextPageIndex
                val newRows = rows.rowsFor(page.repos, store.current.seenRepoIds)
                store.update { it.withPage(newRows, page) }
            }
    }

    private suspend fun showRateLimit(
        store: SearchStore,
        e: RateLimitException,
    ) {
        logger.debug("Rate limit exceeded: ${e.message}")
        val seconds = e.rateLimitInfo.timeUntilReset().inWholeSeconds
        val message =
            if (seconds > 0L) {
                getString(Res.string.rate_limit_exceeded_retry_in, seconds.toInt())
            } else {
                getString(Res.string.rate_limit_exceeded)
            }
        store.update { it.copy(isLoading = false, isLoadingMore = false, errorMessage = message) }
    }

    private suspend fun showFailure(
        store: SearchStore,
        e: Exception,
    ) {
        logger.error("Search failed: ${e.message}")
        val message = e.message ?: getString(Res.string.search_failed)
        store.update { it.copy(isLoading = false, isLoadingMore = false, errorMessage = message) }
    }

    private fun explore(store: SearchStore) {
        val current = store.current
        val query = current.query.trim()
        val platform = current.selectedSearchPlatform
        val prevStatus = current.exploreStatus

        exploreLog.debug(
            "click: query='$query' platform=$platform " +
                "page=$explorePage lastQuery='$lastExploreQuery' status=$prevStatus",
        )

        if (query.isBlank()) {
            exploreLog.debug("skipped: query is blank")
            return
        }
        if (prevStatus == ExploreStatus.LOADING) {
            exploreLog.debug("skipped: already LOADING")
            return
        }

        if (query != lastExploreQuery) {
            exploreLog.debug("query changed ('$lastExploreQuery' -> '$query'); resetting page to 1")
            explorePage = 1
            lastExploreQuery = query
        }

        store.launch {
            store.update { it.copy(exploreStatus = ExploreStatus.LOADING) }
            try {
                explorePage(store, query, platform)
            } catch (e: NetworkException) {
                exploreFailed(store, e)
            } catch (e: LocalStoreException) {
                exploreFailed(store, e)
            }
        }
    }

    private suspend fun explorePage(
        store: SearchStore,
        query: String,
        platform: DiscoveryPlatform,
    ) {
        val exploreResult = searchRepository.exploreFromGithub(query = query, platform = platform, page = explorePage)
        val before = store.current.repositories.size
        exploreLog.debug(
            "response: items=${exploreResult.repos.size} " +
                "returnedPage=${exploreResult.page} hasMore=${exploreResult.hasMore} " +
                "existingVisible=$before",
        )

        if (exploreResult.repos.isNotEmpty()) {
            appendExploreResults(store, exploreResult.repos)
        }
        val added = store.current.repositories.size - before
        val dupes = exploreResult.repos.size - added

        if (exploreResult.hasMore) {
            explorePage++
            exploreLog.debug("-> IDLE: appended=$added dupes=$dupes nextPage=$explorePage")
            store.update { it.copy(exploreStatus = ExploreStatus.IDLE) }
        } else {
            exploreLog.debug("-> EXHAUSTED: appended=$added dupes=$dupes rawItems=${exploreResult.repos.size}")
            store.update { it.copy(exploreStatus = ExploreStatus.EXHAUSTED) }
        }
    }

    private suspend fun appendExploreResults(
        store: SearchStore,
        newRepos: List<GithubRepoSummary>,
    ) {
        val existingIds = store.current.repositories.mapTo(HashSet()) { it.repository.id }
        val deduped = rows.rowsFor(newRepos.filter { it.id !in existingIds }, store.current.seenRepoIds)
        if (deduped.isNotEmpty()) {
            store.update { it.copy(repositories = (it.repositories + deduped).toImmutableList()) }
        }
    }

    private suspend fun exploreFailed(
        store: SearchStore,
        e: Exception,
    ) {
        exploreLog.error("failed: ${e::class.simpleName}: ${e.message}", e)
        store.update { it.copy(exploreStatus = ExploreStatus.IDLE) }
        store.send(SearchEvent.OnMessage(getString(Res.string.explore_error)))
    }

    private companion object {
        const val MIN_QUERY_LENGTH = 2
    }
}

private fun SearchState.loading(isInitial: Boolean): SearchState =
    copy(
        isLoading = isInitial,
        isLoadingMore = !isInitial,
        errorMessage = null,
        repositories = if (isInitial) persistentListOf() else repositories,
        totalCount = if (isInitial) null else totalCount,
        passthroughAttempted = if (isInitial) null else passthroughAttempted,
    )

// A re-fetched repository keeps its row and seen and owner flags, taking the fresh install, favourite and star state.
private fun SearchState.withPage(
    newRows: List<DiscoveryRepositoryUi>,
    page: PaginatedDiscoveryRepositories,
): SearchState {
    val merged = LinkedHashMap<Long, DiscoveryRepositoryUi>()
    repositories.forEach { merged[it.repository.id] = it }
    newRows.forEach { row ->
        merged[row.repository.id] =
            merged[row.repository.id]?.copy(
                isInstalled = row.isInstalled,
                isUpdateAvailable = row.isUpdateAvailable,
                isFavourite = row.isFavourite,
                isStarred = row.isStarred,
                repository = row.repository,
            ) ?: row
    }
    val allRepos = merged.values.toImmutableList()
    return copy(
        repositories = allRepos,
        hasMorePages = page.hasMore,
        totalCount = allRepos.size,
        errorMessage = null,
        passthroughAttempted = page.passthroughAttempted,
    )
}
