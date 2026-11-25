package zed.rainxch.home.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.HOME_REPOS
import zed.rainxch.core.data.dto.GithubRepoNetworkModel
import zed.rainxch.core.data.dto.GithubRepoSearchResponse
import zed.rainxch.core.data.mappers.toSummary
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.asNetworkFailure
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.error.NetworkException
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.repository.PaginatedDiscoveryRepositories
import zed.rainxch.core.domain.model.system.Platform
import kotlin.time.Duration.Companion.milliseconds

internal data class InstallerSearchRequest(
    val query: String,
    val sort: String,
    val startPage: Int,
    val category: String,
    val cacheKey: String,
)

internal class InstallerRepoSearch(
    private val clientProvider: GitHubClientProvider,
    devicePlatform: Platform,
    private val logger: KomiStoreLogger,
    private val cacheManager: CacheManager,
) {
    private val httpClient: HttpClient get() = clientProvider.client

    private val installerExtensions =
        when (devicePlatform) {
            Platform.ANDROID -> listOf(".apk")
            Platform.WINDOWS -> listOf(".msi", ".exe")
            Platform.MACOS -> listOf(".dmg", ".pkg")
            Platform.LINUX -> listOf(".appimage", ".deb", ".rpm", ".pkg.tar.zst")
        }

    private class SearchRun(
        var apiPage: Int,
    ) {
        val results = mutableListOf<GithubRepoSummary>()
        val semaphore = Semaphore(MAX_CONCURRENT_CHECKS)
        var pagesFetched = 0
        var lastEmittedCount = 0

        val hasMore: Boolean get() = pagesFetched < MAX_SEARCH_PAGES && results.size >= DESIRED_RESULTS
    }

    fun search(request: InstallerSearchRequest): Flow<PaginatedDiscoveryRepositories> =
        flow {
            val run = SearchRun(apiPage = request.startPage)
            logger.debug("Query: ${request.query} | Sort: ${request.sort} | Page: ${request.startPage}")

            while (run.results.size < DESIRED_RESULTS && run.pagesFetched < MAX_SEARCH_PAGES) {
                currentCoroutineContext().ensureActive()
                if (!searchPage(request, run)) break
                run.apiPage++
                run.pagesFetched++
            }

            emitRemainder(run)
            cacheResults(request, run)
        }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<PaginatedDiscoveryRepositories>.searchPage(
        request: InstallerSearchRequest,
        run: SearchRun,
    ): Boolean {
        val items = fetchSearchPage(request, run.apiPage) ?: return false
        logger.debug("API Page ${run.apiPage}: Got ${items.size} repos")
        if (items.isEmpty()) {
            logger.debug("No more items from API, breaking")
            return false
        }

        val candidates = items.take(MAX_CANDIDATES_PER_PAGE)
        logger.debug("Checking ${candidates.size} candidates for installers")
        collectInstallerRepos(candidates, run)

        val done = run.results.size >= DESIRED_RESULTS || items.size < SEARCH_PAGE_SIZE
        if (done) logger.debug("Breaking: results=${run.results.size}, response size=${items.size}")
        return !done
    }

    private suspend fun fetchSearchPage(
        request: InstallerSearchRequest,
        page: Int,
    ): List<GithubRepoNetworkModel>? =
        try {
            httpClient
                .executeRequest<GithubRepoSearchResponse> {
                    get("/search/repositories") {
                        parameter("q", request.query)
                        parameter("sort", request.sort)
                        parameter("order", "desc")
                        parameter("per_page", SEARCH_PAGE_SIZE)
                        parameter("page", page)
                    }
                }.getOrElse { error ->
                    logger.error("Search request failed: ${error.message}")
                    throw error.asNetworkFailure()
                }.items
        } catch (_: RateLimitException) {
            logger.error("Rate limited during search")
            null
        } catch (e: NetworkException) {
            logger.error("Search failed: ${e.message}", e)
            null
        }

    private suspend fun FlowCollector<PaginatedDiscoveryRepositories>.collectInstallerRepos(
        candidates: List<GithubRepoNetworkModel>,
        run: SearchRun,
    ) = coroutineScope {
        val checks =
            candidates.map { repo ->
                async {
                    run.semaphore.withPermit {
                        withTimeoutOrNull(INSTALLER_CHECK_TIMEOUT) { checkRepoHasInstallers(repo) }
                    }
                }
            }

        for (check in checks) {
            currentCoroutineContext().ensureActive()
            check.await()?.let { found -> addResult(found, run) }
            if (run.results.size >= DESIRED_RESULTS) break
        }
    }

    private suspend fun FlowCollector<PaginatedDiscoveryRepositories>.addResult(
        repo: GithubRepoSummary,
        run: SearchRun,
    ) {
        run.results.add(repo)
        logger.debug("Found installer repo: ${repo.fullName} (${run.results.size}/$DESIRED_RESULTS)")

        val batchFull = run.results.size % EMIT_BATCH_SIZE == 0 || run.results.size >= DESIRED_RESULTS
        val newItems = run.results.subList(run.lastEmittedCount, run.results.size)
        if (batchFull && newItems.isNotEmpty()) {
            emit(
                PaginatedDiscoveryRepositories(
                    repos = newItems.toList(),
                    hasMore = true,
                    nextPageIndex = run.apiPage + 1,
                ),
            )
            logger.debug("Emitted ${newItems.size} repos (total: ${run.results.size})")
            run.lastEmittedCount = run.results.size
        }

        if (run.results.size >= DESIRED_RESULTS) logger.debug("Reached desired count, breaking")
    }

    private suspend fun FlowCollector<PaginatedDiscoveryRepositories>.emitRemainder(run: SearchRun) {
        if (run.results.size > run.lastEmittedCount) {
            val finalBatch = run.results.subList(run.lastEmittedCount, run.results.size)
            emit(
                PaginatedDiscoveryRepositories(
                    repos = finalBatch.toList(),
                    hasMore = run.hasMore,
                    nextPageIndex = if (run.hasMore) run.apiPage + 1 else run.apiPage,
                ),
            )
            logger.debug("Final emit: ${finalBatch.size} repos (total: ${run.results.size})")
        } else if (run.results.isEmpty()) {
            emit(
                PaginatedDiscoveryRepositories(
                    repos = emptyList(),
                    hasMore = false,
                    nextPageIndex = run.apiPage,
                ),
            )
            logger.debug("No results found")
        }
    }

    private suspend fun cacheResults(
        request: InstallerSearchRequest,
        run: SearchRun,
    ) {
        if (run.results.isEmpty()) return
        cacheManager.put(
            key = request.cacheKey,
            value =
                PaginatedDiscoveryRepositories(
                    repos = run.results.toList(),
                    hasMore = run.hasMore,
                    nextPageIndex = run.apiPage + 1,
                ),
            ttlMillis = HOME_REPOS,
        )
        logger.debug("Cached ${run.results.size} repos for ${request.category} page ${request.startPage}")
    }

    private suspend fun checkRepoHasInstallers(repo: GithubRepoNetworkModel): GithubRepoSummary? {
        val releases =
            try {
                httpClient
                    .executeRequest<List<GithubReleaseNetworkModel>> {
                        get("/repos/${repo.owner.login}/${repo.name}/releases") {
                            header("Accept", "application/vnd.github.v3+json")
                            parameter("per_page", RELEASES_TO_CHECK)
                        }
                    }.getOrNull()
            } catch (_: RateLimitException) {
                null
            }

        val stableRelease = releases?.firstOrNull { it.draft != true && it.prerelease != true }
        val hasInstaller =
            stableRelease?.assets.orEmpty().any { asset ->
                val name = asset.name.lowercase()
                installerExtensions.any { name.endsWith(it) }
            }
        return if (hasInstaller) repo.toSummary() else null
    }

    @Serializable
    private data class GithubReleaseNetworkModel(
        val assets: List<AssetNetworkModel>,
        val draft: Boolean? = null,
        val prerelease: Boolean? = null,
        @SerialName("published_at") val publishedAt: String? = null,
    )

    @Serializable
    private data class AssetNetworkModel(
        val name: String,
    )

    private companion object {
        const val DESIRED_RESULTS = 10
        const val SEARCH_PAGE_SIZE = 100
        const val MAX_SEARCH_PAGES = 5
        const val MAX_CANDIDATES_PER_PAGE = 50
        const val MAX_CONCURRENT_CHECKS = 25
        const val EMIT_BATCH_SIZE = 3
        const val RELEASES_TO_CHECK = 10
        val INSTALLER_CHECK_TIMEOUT = 5000.milliseconds
    }
}
