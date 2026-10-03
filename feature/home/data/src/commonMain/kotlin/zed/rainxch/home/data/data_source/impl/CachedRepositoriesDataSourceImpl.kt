package zed.rainxch.home.data.data_source.impl

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.OFFLINE_MIRROR_BASE
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.home.data.data_source.CachedRepositoriesDataSource
import zed.rainxch.home.data.dto.CachedGithubRepoSummary
import zed.rainxch.home.data.dto.CachedRepoResponse
import zed.rainxch.home.data.mappers.toCachedGithubRepoSummary
import zed.rainxch.home.domain.model.HomeCategory
import zed.rainxch.home.domain.model.TopicCategory
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class CachedRepositoriesDataSourceImpl(
    private val backendApiClient: BackendApiClient,
    private val logger: KomiStoreLogger,
) : CachedRepositoriesDataSource {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    private val fallbackHttpClient =
        HttpClient {
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 10_000
            }
            expectSuccess = false
        }

    private val cacheMutex = Mutex()
    private val memoryCache = mutableMapOf<CacheKey, CacheEntry>()
    private val topicMemoryCache = mutableMapOf<TopicCacheKey, CacheEntry>()

    private data class CacheEntry(
        val data: CachedRepoResponse,
        val fetchedAt: Instant,
    )

    override suspend fun getCachedTrendingRepos(platform: DiscoveryPlatform): CachedRepoResponse? =
        fetchCachedReposForCategory(platform, HomeCategory.TRENDING)

    override suspend fun getCachedHotReleaseRepos(platform: DiscoveryPlatform): CachedRepoResponse? =
        fetchCachedReposForCategory(platform, HomeCategory.HOT_RELEASE)

    override suspend fun getCachedMostPopularRepos(platform: DiscoveryPlatform): CachedRepoResponse? =
        fetchCachedReposForCategory(platform, HomeCategory.MOST_POPULAR)

    override suspend fun getCachedTopicRepos(
        topic: TopicCategory,
        platform: DiscoveryPlatform,
    ): CachedRepoResponse? {
        val topicCacheKey = TopicCacheKey(topic, platform)
        val cached = cacheMutex.withLock { topicMemoryCache[topicCacheKey] }
        if (cached != null) {
            val age = Clock.System.now() - cached.fetchedAt
            if (age < CACHE_TTL) {
                logger.debug("Topic memory cache hit for $topicCacheKey (age: ${age.inWholeSeconds}s)")
                return cached.data
            }
        }

        val backendResult = fetchTopicFromBackend(topic, platform)
        if (backendResult != null) {
            cacheMutex.withLock {
                topicMemoryCache[topicCacheKey] =
                    CacheEntry(data = backendResult, fetchedAt = Clock.System.now())
            }
            return backendResult
        }

        logger.debug("Backend failed for topic $topicCacheKey, falling back to GitHub raw JSON")
        return fetchTopicFromFallback(topic, platform, topicCacheKey)
    }

    private suspend fun fetchCachedReposForCategory(
        platform: DiscoveryPlatform,
        category: HomeCategory,
    ): CachedRepoResponse? {
        val cacheKey = CacheKey(platform, category)

        val cached = cacheMutex.withLock { memoryCache[cacheKey] }
        if (cached != null) {
            val age = Clock.System.now() - cached.fetchedAt
            if (age < CACHE_TTL) {
                logger.debug("Memory cache hit for $cacheKey (age: ${age.inWholeSeconds}s)")
                return cached.data
            } else {
                logger.debug("Memory cache expired for $cacheKey (age: ${age.inWholeSeconds}s)")
            }
        }

        val backendResult = fetchCategoryFromBackend(category, platform)
        if (backendResult != null) {
            cacheMutex.withLock {
                memoryCache[cacheKey] =
                    CacheEntry(data = backendResult, fetchedAt = Clock.System.now())
            }
            return backendResult
        }

        logger.debug("Backend failed for $cacheKey, falling back to GitHub raw JSON")
        return fetchCategoryFromFallback(category, platform, cacheKey)
    }

    private suspend fun fetchCategoryFromBackend(
        category: HomeCategory,
        platform: DiscoveryPlatform,
    ): CachedRepoResponse? {
        val categorySlug = when (category) {
            HomeCategory.TRENDING -> "trending"
            HomeCategory.HOT_RELEASE -> "new-releases"
            HomeCategory.MOST_POPULAR -> "most-popular"
        }

        if (platform == DiscoveryPlatform.All) {
            return fetchAllPlatformsFromBackend(categorySlug, category)
        }

        val platformSlug = platform.toApiSlug() ?: return null
        val result = backendApiClient.getCategory(categorySlug, platformSlug)
        return result.getOrNull()?.let { repos ->
            if (repos.isEmpty()) return null
            logger.debug("Backend returned ${repos.size} repos for $categorySlug/$platformSlug")
            CachedRepoResponse(
                category = categorySlug,
                platform = platformSlug,
                lastUpdated = "",
                totalCount = repos.size,
                repositories = repos.map { it.toCachedGithubRepoSummary(platform) },
            )
        }
    }

    private suspend fun fetchAllPlatformsFromBackend(
        categorySlug: String,
        category: HomeCategory,
    ): CachedRepoResponse? = withContext(Dispatchers.IO) {
        val platforms = listOf("android", "windows", "macos", "linux")
        val responses = coroutineScope {
            platforms.map { plat ->
                async {
                    val r = backendApiClient.getCategory(categorySlug, plat)
                    r.getOrNull()?.map { it.toCachedGithubRepoSummary() }
                }
            }.awaitAll().filterNotNull()
        }

        if (responses.isEmpty() || responses.size < platforms.size) return@withContext null

        val merged = responses
            .asSequence()
            .flatten()
            .groupBy { it.id }
            .values
            .map { duplicates ->
                duplicates.reduce { acc, repo ->
                    acc.copy(
                        availablePlatforms = (acc.availablePlatforms + repo.availablePlatforms).distinct(),
                        trendingScore = listOfNotNull(acc.trendingScore, repo.trendingScore).maxOrNull(),
                        popularityScore = listOfNotNull(acc.popularityScore, repo.popularityScore).maxOrNull(),
                        latestReleaseDate = listOfNotNull(acc.latestReleaseDate, repo.latestReleaseDate).maxOrNull(),
                    )
                }
            }
            .sortedWith(
                compareByDescending<CachedGithubRepoSummary> { it.trendingScore }
                    .thenByDescending { it.popularityScore }
                    .thenByDescending { it.latestReleaseDate },
            )
            .toList()

        logger.debug("Backend returned ${merged.size} merged repos for $categorySlug/all")
        CachedRepoResponse(
            category = categorySlug,
            platform = "all",
            lastUpdated = "",
            totalCount = merged.size,
            repositories = merged,
        )
    }

    private suspend fun fetchTopicFromBackend(
        topic: TopicCategory,
        platform: DiscoveryPlatform,
    ): CachedRepoResponse? {
        val topicSlug = topic.toApiSlug()

        if (platform == DiscoveryPlatform.All) {
            return fetchAllPlatformsTopicFromBackend(topicSlug)
        }

        val platformSlug = platform.toApiSlug() ?: return null
        val result = backendApiClient.getTopic(topicSlug, platformSlug)
        return result.getOrNull()?.let { repos ->
            if (repos.isEmpty()) return null
            logger.debug("Backend returned ${repos.size} repos for topic $topicSlug/$platformSlug")
            CachedRepoResponse(
                category = "topic",
                platform = platformSlug,
                lastUpdated = "",
                totalCount = repos.size,
                repositories = repos.map { it.toCachedGithubRepoSummary(platform) },
            )
        }
    }

    private suspend fun fetchAllPlatformsTopicFromBackend(
        topicSlug: String,
    ): CachedRepoResponse? = withContext(Dispatchers.IO) {
        val platforms = listOf("android", "windows", "macos", "linux")
        val responses = coroutineScope {
            platforms.map { plat ->
                async {
                    backendApiClient.getTopic(topicSlug, plat).getOrNull()?.map {
                        it.toCachedGithubRepoSummary()
                    }
                }
            }.awaitAll().filterNotNull()
        }

        if (responses.isEmpty() || responses.size < platforms.size) return@withContext null

        val merged = responses
            .asSequence()
            .flatten()
            .groupBy { it.id }
            .values
            .map { duplicates ->
                duplicates.reduce { acc, repo ->
                    acc.copy(
                        availablePlatforms = (acc.availablePlatforms + repo.availablePlatforms).distinct(),
                        latestReleaseDate = listOfNotNull(acc.latestReleaseDate, repo.latestReleaseDate).maxOrNull(),
                    )
                }
            }
            .sortedByDescending { it.stargazersCount }
            .toList()

        logger.debug("Backend returned ${merged.size} merged repos for topic $topicSlug/all")
        CachedRepoResponse(
            category = "topic",
            platform = "all",
            lastUpdated = "",
            totalCount = merged.size,
            repositories = merged,
        )
    }

    private suspend fun fetchCategoryFromFallback(
        category: HomeCategory,
        platform: DiscoveryPlatform,
        cacheKey: CacheKey,
    ): CachedRepoResponse? = withContext(Dispatchers.IO) {
        val paths = when (category) {
            HomeCategory.TRENDING -> listOf(
                "cached-data/trending/android.json",
                "cached-data/trending/windows.json",
                "cached-data/trending/macos.json",
                "cached-data/trending/linux.json",
            )
            HomeCategory.HOT_RELEASE -> listOf(
                "cached-data/new-releases/android.json",
                "cached-data/new-releases/windows.json",
                "cached-data/new-releases/macos.json",
                "cached-data/new-releases/linux.json",
            )
            HomeCategory.MOST_POPULAR -> listOf(
                "cached-data/most-popular/android.json",
                "cached-data/most-popular/windows.json",
                "cached-data/most-popular/macos.json",
                "cached-data/most-popular/linux.json",
            )
        }

        val responses = coroutineScope {
            paths.map { path ->
                async {
                    fetchFallbackFile(path)
                }
            }.awaitAll().filterNotNull()
        }

        if (responses.isEmpty()) {
            logger.error("All fallback mirrors failed for $cacheKey")
            return@withContext null
        }

        val allMergedRepos = responses
            .asSequence()
            .flatMap { it.repositories.asSequence() }
            .groupBy { it.id }
            .values
            .map { duplicates ->
                duplicates.reduce { acc, repo ->
                    acc.copy(
                        availablePlatforms = (acc.availablePlatforms + repo.availablePlatforms).distinct(),
                        trendingScore = listOfNotNull(acc.trendingScore, repo.trendingScore).maxOrNull(),
                        popularityScore = listOfNotNull(acc.popularityScore, repo.popularityScore).maxOrNull(),
                        latestReleaseDate = listOfNotNull(acc.latestReleaseDate, repo.latestReleaseDate).maxOrNull(),
                    )
                }
            }
            .sortedWith(
                compareByDescending<CachedGithubRepoSummary> { it.trendingScore }
                    .thenByDescending { it.popularityScore }
                    .thenByDescending { it.latestReleaseDate },
            )

        val filteredRepos = when (platform) {
            DiscoveryPlatform.All -> allMergedRepos
            else -> allMergedRepos.filter { platform in it.availablePlatforms }
        }.toList()

        val merged = CachedRepoResponse(
            category = responses.first().category,
            platform = platform.name.lowercase(),
            lastUpdated = responses.maxOf { it.lastUpdated },
            totalCount = filteredRepos.size,
            repositories = filteredRepos,
        )

        if (responses.size == paths.size) {
            cacheMutex.withLock {
                memoryCache[cacheKey] =
                    CacheEntry(data = merged, fetchedAt = Clock.System.now())
            }
        }

        merged
    }

    private suspend fun fetchTopicFromFallback(
        topic: TopicCategory,
        platform: DiscoveryPlatform,
        topicCacheKey: TopicCacheKey,
    ): CachedRepoResponse? = withContext(Dispatchers.IO) {
        val topicFolder = topic.toApiSlug()

        val paths = listOf(
            "cached-data/topics/$topicFolder/android.json",
            "cached-data/topics/$topicFolder/windows.json",
            "cached-data/topics/$topicFolder/macos.json",
            "cached-data/topics/$topicFolder/linux.json",
        )

        val responses = coroutineScope {
            paths.map { path ->
                async {
                    fetchFallbackFile(path)
                }
            }.awaitAll().filterNotNull()
        }

        if (responses.isEmpty()) {
            logger.error("All fallback topic mirrors failed for $topicCacheKey")
            return@withContext null
        }

        val allMergedRepos = responses
            .asSequence()
            .flatMap { it.repositories.asSequence() }
            .groupBy { it.id }
            .values
            .map { duplicates ->
                duplicates.reduce { acc, repo ->
                    acc.copy(
                        availablePlatforms = (acc.availablePlatforms + repo.availablePlatforms).distinct(),
                        latestReleaseDate = listOfNotNull(acc.latestReleaseDate, repo.latestReleaseDate).maxOrNull(),
                    )
                }
            }
            .sortedByDescending { it.stargazersCount }

        val filteredRepos = when (platform) {
            DiscoveryPlatform.All -> allMergedRepos
            else -> allMergedRepos.filter { platform in it.availablePlatforms }
        }.toList()

        val merged = CachedRepoResponse(
            category = "topic",
            platform = platform.name.lowercase(),
            lastUpdated = responses.maxOf { it.lastUpdated },
            totalCount = filteredRepos.size,
            repositories = filteredRepos,
        )

        if (responses.size == paths.size) {
            cacheMutex.withLock {
                topicMemoryCache[topicCacheKey] =
                    CacheEntry(data = merged, fetchedAt = Clock.System.now())
            }
        }

        merged
    }

    private suspend fun fetchFallbackFile(path: String): CachedRepoResponse? {
        val url = "$OFFLINE_MIRROR_BASE/$path"
        val filePlatform = when {
            path.contains("/android") -> DiscoveryPlatform.Android
            path.contains("/windows") -> DiscoveryPlatform.Windows
            path.contains("/macos") -> DiscoveryPlatform.Macos
            path.contains("/linux") -> DiscoveryPlatform.Linux
            else -> error("Unknown platform in path: $path")
        }
        return try {
            logger.debug("Fetching fallback: $url")
            val response: HttpResponse = fallbackHttpClient.get(url)
            if (response.status.isSuccess()) {
                json.decodeFromString<CachedRepoResponse>(response.bodyAsText())
                    .let { repoResponse ->
                        repoResponse.copy(
                            repositories = repoResponse.repositories.map { repo ->
                                if (repo.availablePlatforms.isEmpty()) {
                                    repo.copy(availablePlatforms = listOf(filePlatform))
                                } else {
                                    repo
                                }
                            },
                        )
                    }
            } else {
                logger.error("HTTP ${response.status.value} from $url")
                null
            }
        } catch (e: SerializationException) {
            logger.error("Parse error from $url: ${e.message}")
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Error with $url: ${e.message}")
            null
        }
    }

    private fun DiscoveryPlatform.toApiSlug(): String? = when (this) {
        DiscoveryPlatform.Android -> "android"
        DiscoveryPlatform.Windows -> "windows"
        DiscoveryPlatform.Macos -> "macos"
        DiscoveryPlatform.Linux -> "linux"
        DiscoveryPlatform.Ios -> null 
        DiscoveryPlatform.All -> null
    }

    private fun TopicCategory.toApiSlug(): String = when (this) {
        TopicCategory.PRIVACY -> "privacy"
        TopicCategory.MEDIA -> "media"
        TopicCategory.PRODUCTIVITY -> "productivity"
        TopicCategory.NETWORKING -> "networking"
        TopicCategory.DEV_TOOLS -> "dev-tools"
    }

    private companion object {
        private val CACHE_TTL = 1.hours
    }

    private data class CacheKey(
        val platform: DiscoveryPlatform,
        val category: HomeCategory,
    )

    private data class TopicCacheKey(
        val topic: TopicCategory,
        val platform: DiscoveryPlatform,
    )
}
