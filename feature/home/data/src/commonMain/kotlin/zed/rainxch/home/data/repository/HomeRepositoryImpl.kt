@file:OptIn(ExperimentalTime::class)

package zed.rainxch.home.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.HOME_REPOS
import zed.rainxch.core.data.dto.RepoByIdNetwork
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.asNetworkFailure
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.account.github.GithubUser
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.NetworkException
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.repository.PaginatedDiscoveryRepositories
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.home.data.datasource.CachedRepositoriesDataSource
import zed.rainxch.home.data.dto.CachedRepoResponse
import zed.rainxch.home.data.mappers.toGithubRepoSummary
import zed.rainxch.home.domain.model.TopicCategory
import zed.rainxch.home.domain.repository.HomeRepository
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

class HomeRepositoryImpl(
    private val clientProvider: GitHubClientProvider,
    devicePlatform: Platform,
    private val cachedDataSource: CachedRepositoriesDataSource,
    private val logger: KomiStoreLogger,
    private val cacheManager: CacheManager,
) : HomeRepository {
    private val httpClient: HttpClient get() = clientProvider.client

    private val installerSearch = InstallerRepoSearch(clientProvider, devicePlatform, logger, cacheManager)

    private enum class Chart(
        val cacheCategory: String,
        val label: String,
    ) {
        Trending("trending", "trending"),
        HotRelease("hot_release", "hot release"),
        MostPopular("most_popular", "most popular"),
    }

    override fun getTrendingRepositories(
        platforms: Set<DiscoveryPlatform>,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories> = chartFlow(Chart.Trending, platforms, page)

    override fun getHotReleaseRepositories(
        platforms: Set<DiscoveryPlatform>,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories> = chartFlow(Chart.HotRelease, platforms, page)

    override fun getMostPopular(
        platforms: Set<DiscoveryPlatform>,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories> = chartFlow(Chart.MostPopular, platforms, page)

    override fun getTopicRepositories(
        topic: TopicCategory,
        platforms: Set<DiscoveryPlatform>,
    ): Flow<PaginatedDiscoveryRepositories> =
        flow {
            val cachedData =
                loadCachedReposForSet(platforms) {
                    cachedDataSource.getCachedTopicRepos(topic, it)
                }

            if (cachedData != null && cachedData.repositories.isNotEmpty()) {
                logger.debug("Using cached topic data for ${topic.name}: ${cachedData.repositories.size} repos")

                val repos = cachedData.repositories.map { it.toGithubRepoSummary() }
                emit(
                    PaginatedDiscoveryRepositories(
                        repos = repos,
                        hasMore = false,
                        nextPageIndex = 2,
                    ),
                )
            }
        }.flowOn(Dispatchers.IO)

    override fun searchByTopic(
        searchKeywords: String,
        platforms: Set<DiscoveryPlatform>,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories> =
        flow {
            val cacheCategory = "topic_${searchKeywords.hashCode()}"
            val key = cacheKey(cacheCategory, platforms, page)
            val localCached = localPage(key, "cached topic repos")
            if (localCached != null) {
                emit(localCached)
            } else {
                emitAll(
                    installerSearch.search(
                        InstallerSearchRequest(
                            query = buildSimplifiedQuery("$searchKeywords in:name,description,topics stars:>10 archived:false", platforms),
                            sort = "stars",
                            startPage = page,
                            category = cacheCategory,
                            cacheKey = key,
                        ),
                    ),
                )
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun getRepositoryById(id: Long): GithubRepoSummary? {
        val cacheKey = "home:repo_id:$id"
        return cacheManager.get<GithubRepoSummary>(cacheKey) ?: fetchRepositoryById(id, cacheKey)
    }

    private fun chartFlow(
        chart: Chart,
        platforms: Set<DiscoveryPlatform>,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories> =
        flow {
            val key = cacheKey(chart.cacheCategory, platforms, page)
            val ready =
                (if (page == 1) mirrorPage(chart, platforms, key) else null)
                    ?: localPage(key, "locally cached ${chart.label} repos")
            if (ready != null) {
                emit(ready)
            } else {
                emitAll(installerSearch.search(chart.searchRequest(platforms, page, key)))
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun mirrorPage(
        chart: Chart,
        platforms: Set<DiscoveryPlatform>,
        key: String,
    ): PaginatedDiscoveryRepositories? {
        logger.debug("Attempting to load cached ${chart.label} repositories...")
        val cachedRepos =
            loadCachedReposForSet(platforms) { platform -> chart.mirrorList(platform) }
                ?.repositories
                ?.takeIf { it.isNotEmpty() }
        if (cachedRepos == null) {
            logger.debug("No mirror data, checking local cache...")
            return null
        }

        logger.debug("Using mirror cached data: ${cachedRepos.size} repos")
        val result =
            PaginatedDiscoveryRepositories(
                repos = cachedRepos.map { it.toGithubRepoSummary() },
                hasMore = false,
                nextPageIndex = 2,
            )
        cacheManager.put(key = key, value = result, ttlMillis = HOME_REPOS)
        return result
    }

    private suspend fun localPage(
        key: String,
        description: String,
    ): PaginatedDiscoveryRepositories? =
        cacheManager
            .get<PaginatedDiscoveryRepositories>(key)
            ?.takeIf { it.repos.isNotEmpty() }
            ?.also { logger.debug("Using $description: ${it.repos.size}") }

    private suspend fun Chart.mirrorList(platform: DiscoveryPlatform): CachedRepoResponse? =
        when (this) {
            Chart.Trending -> cachedDataSource.getCachedTrendingRepos(platform)
            Chart.HotRelease -> cachedDataSource.getCachedHotReleaseRepos(platform)
            Chart.MostPopular -> cachedDataSource.getCachedMostPopularRepos(platform)
        }

    private fun Chart.searchRequest(
        platforms: Set<DiscoveryPlatform>,
        page: Int,
        key: String,
    ): InstallerSearchRequest {
        val (baseQuery, sort) =
            when (this) {
                Chart.Trending -> {
                    "stars:>50 archived:false pushed:>=${utcDateBefore(30.days)}" to "stars"
                }

                Chart.HotRelease -> {
                    "stars:>10 archived:false pushed:>=${utcDateBefore(14.days)}" to "updated"
                }

                Chart.MostPopular -> {
                    "stars:>1000 archived:false created:<${utcDateBefore(180.days)} pushed:>=${utcDateBefore(365.days)}" to "stars"
                }
            }
        return InstallerSearchRequest(
            query = buildSimplifiedQuery(baseQuery, platforms),
            sort = sort,
            startPage = page,
            category = cacheCategory,
            cacheKey = key,
        )
    }

    private fun utcDateBefore(span: Duration): LocalDate =
        Clock.System
            .now()
            .minus(span)
            .toLocalDateTime(TimeZone.UTC)
            .date

    private suspend fun fetchRepositoryById(
        id: Long,
        cacheKey: String,
    ): GithubRepoSummary? =
        try {
            val result =
                httpClient
                    .executeRequest<RepoByIdNetwork> {
                        get("/repositories/$id") {
                            header("Accept", "application/vnd.github+json")
                        }
                    }.getOrElse { throw it.asNetworkFailure() }
                    .toSummary()
            cacheManager.put(cacheKey, result, HOME_REPOS)
            result
        } catch (e: NetworkException) {
            staleRepositoryById(id, cacheKey, e)
        } catch (e: RateLimitException) {
            staleRepositoryById(id, cacheKey, e)
        } catch (e: LocalStoreException) {
            staleRepositoryById(id, cacheKey, e)
        }

    private suspend fun staleRepositoryById(
        id: Long,
        cacheKey: String,
        failure: Exception,
    ): GithubRepoSummary? {
        val stale = cacheManager.getStale<GithubRepoSummary>(cacheKey)
        if (stale == null) logger.warn("getRepositoryById($id) failed: ${failure.message}")
        return stale
    }

    private fun cacheKey(
        category: String,
        requestedPlatforms: Set<DiscoveryPlatform>,
        page: Int,
    ): String {
        val effective = requestedPlatforms.normalize()
        val token =
            if (effective.isEmpty()) {
                "all"
            } else {
                effective.map { it.name }.sorted().joinToString(separator = "+")
            }
        return "home:$category:$token:page$page"
    }

    private suspend fun loadCachedReposForSet(
        platforms: Set<DiscoveryPlatform>,
        fetchSingle: suspend (DiscoveryPlatform) -> CachedRepoResponse?,
    ): CachedRepoResponse? {
        val effective = platforms.normalize()
        return when (effective.size) {
            0 -> {
                fetchSingle(DiscoveryPlatform.All)
            }

            1 -> {
                fetchSingle(effective.first())
            }

            else -> {
                fetchSingle(DiscoveryPlatform.All)?.let { response ->
                    response.copy(
                        repositories =
                            response.repositories.filter { repo ->
                                repo.availablePlatforms.any { it in effective }
                            },
                    )
                }
            }
        }
    }

    private fun buildSimplifiedQuery(
        baseQuery: String,
        requestedPlatforms: Set<DiscoveryPlatform>,
    ): String {
        val topics =
            requestedPlatforms
                .normalize()
                .mapNotNull { it.toQueryTopic() }
                .distinct()

        return when {
            topics.isEmpty() -> baseQuery
            topics.size == 1 -> "$baseQuery topic:${topics.first()}"
            else -> topics.joinToString(separator = " OR ") { "($baseQuery topic:$it)" }
        }
    }

    private fun DiscoveryPlatform.toQueryTopic(): String? =
        when (this) {
            DiscoveryPlatform.All -> null
            DiscoveryPlatform.Android -> "android"
            DiscoveryPlatform.Windows -> "desktop"
            DiscoveryPlatform.Macos -> "macos"
            DiscoveryPlatform.Ios -> "ios"
            DiscoveryPlatform.Linux -> "linux"
        }

    private fun Set<DiscoveryPlatform>.normalize(): Set<DiscoveryPlatform> {
        if (contains(DiscoveryPlatform.All)) return emptySet()
        val real = filter { it != DiscoveryPlatform.All }.toSet()
        return if (real.size == DiscoveryPlatform.entries.size) emptySet() else real
    }

    private fun RepoByIdNetwork.toSummary(): GithubRepoSummary =
        GithubRepoSummary(
            id = id,
            name = name,
            fullName = fullName,
            owner =
                GithubUser(
                    id = owner.id,
                    login = owner.login,
                    avatarUrl = owner.avatarUrl,
                    htmlUrl = owner.htmlUrl,
                ),
            description = description,
            defaultBranch = defaultBranch,
            htmlUrl = htmlUrl,
            stargazersCount = stars,
            forksCount = forks,
            language = language,
            topics = topics.orEmpty(),
            releasesUrl = "https://api.github.com/repos/$fullName/releases{/id}",
            updatedAt = updatedAt,
        )
}
