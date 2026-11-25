package zed.rainxch.home.data.datasource.impl

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
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
import zed.rainxch.core.data.network.networkFailures
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.NetworkException
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.home.data.datasource.CachedRepositoriesDataSource
import zed.rainxch.home.data.dto.CachedRepoResponse
import zed.rainxch.home.data.mappers.toCachedGithubRepoSummary
import zed.rainxch.home.domain.model.HomeCategory
import zed.rainxch.home.domain.model.TopicCategory
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

    private data class CacheEntry(
        val data: CachedRepoResponse,
        val fetchedAt: Instant,
    )

    private data class CacheKey(
        val listing: DiscoveryListing,
        val platform: DiscoveryPlatform,
    )

    override suspend fun getCachedTrendingRepos(platform: DiscoveryPlatform): CachedRepoResponse? =
        cachedListing(DiscoveryListing.Chart(HomeCategory.TRENDING), platform)

    override suspend fun getCachedHotReleaseRepos(platform: DiscoveryPlatform): CachedRepoResponse? =
        cachedListing(DiscoveryListing.Chart(HomeCategory.HOT_RELEASE), platform)

    override suspend fun getCachedMostPopularRepos(platform: DiscoveryPlatform): CachedRepoResponse? =
        cachedListing(DiscoveryListing.Chart(HomeCategory.MOST_POPULAR), platform)

    override suspend fun getCachedTopicRepos(
        topic: TopicCategory,
        platform: DiscoveryPlatform,
    ): CachedRepoResponse? = cachedListing(DiscoveryListing.Topic(topic), platform)

    private suspend fun cachedListing(
        listing: DiscoveryListing,
        platform: DiscoveryPlatform,
    ): CachedRepoResponse? {
        val cacheKey = CacheKey(listing, platform)
        return freshFromMemory(cacheKey)
            ?: fromBackend(listing, platform)?.also { remember(cacheKey, it) }
            ?: run {
                logger.debug("Backend failed for $cacheKey, falling back to GitHub raw JSON")
                fromMirror(listing, platform, cacheKey)
            }
    }

    private suspend fun freshFromMemory(cacheKey: CacheKey): CachedRepoResponse? {
        val cached = cacheMutex.withLock { memoryCache[cacheKey] } ?: return null
        val age = Clock.System.now() - cached.fetchedAt
        if (age >= CACHE_TTL) {
            logger.debug("Memory cache expired for $cacheKey (age: ${age.inWholeSeconds}s)")
            return null
        }
        logger.debug("Memory cache hit for $cacheKey (age: ${age.inWholeSeconds}s)")
        return cached.data
    }

    private suspend fun remember(
        cacheKey: CacheKey,
        data: CachedRepoResponse,
    ) {
        cacheMutex.withLock {
            memoryCache[cacheKey] = CacheEntry(data = data, fetchedAt = Clock.System.now())
        }
    }

    private suspend fun fromBackend(
        listing: DiscoveryListing,
        platform: DiscoveryPlatform,
    ): CachedRepoResponse? {
        if (platform == DiscoveryPlatform.All) return allPlatformsFromBackend(listing)
        val platformSlug = MIRROR_PLATFORMS[platform] ?: return null
        val repos = listing.fetch(backendApiClient, platformSlug).getOrNull()?.takeIf { it.isNotEmpty() } ?: return null

        logger.debug("Backend returned ${repos.size} repos for ${listing.logName}/$platformSlug")
        return CachedRepoResponse(
            category = listing.responseCategory,
            platform = platformSlug,
            lastUpdated = "",
            totalCount = repos.size,
            repositories = repos.map { it.toCachedGithubRepoSummary(platform) },
        )
    }

    private suspend fun allPlatformsFromBackend(listing: DiscoveryListing): CachedRepoResponse? =
        withContext(Dispatchers.IO) {
            val responses =
                coroutineScope {
                    MIRROR_PLATFORMS.values
                        .map { platformSlug ->
                            async {
                                listing
                                    .fetch(backendApiClient, platformSlug)
                                    .getOrNull()
                                    ?.map { it.toCachedGithubRepoSummary() }
                            }
                        }.awaitAll()
                        .filterNotNull()
                }

            if (responses.size < MIRROR_PLATFORMS.size) return@withContext null

            val merged = listing.merge(responses.flatten())
            logger.debug("Backend returned ${merged.size} merged repos for ${listing.logName}/all")
            CachedRepoResponse(
                category = listing.responseCategory,
                platform = "all",
                lastUpdated = "",
                totalCount = merged.size,
                repositories = merged,
            )
        }

    private suspend fun fromMirror(
        listing: DiscoveryListing,
        platform: DiscoveryPlatform,
        cacheKey: CacheKey,
    ): CachedRepoResponse? =
        withContext(Dispatchers.IO) {
            val responses =
                coroutineScope {
                    MIRROR_PLATFORMS
                        .map { (filePlatform, slug) -> async { fetchMirrorFile("${listing.mirrorFolder}/$slug.json", filePlatform) } }
                        .awaitAll()
                        .filterNotNull()
                }

            if (responses.isEmpty()) {
                logger.error("All fallback mirrors failed for $cacheKey")
                return@withContext null
            }

            val repos =
                listing
                    .merge(responses.flatMap { it.repositories })
                    .filter { platform == DiscoveryPlatform.All || platform in it.availablePlatforms }
            val merged =
                CachedRepoResponse(
                    category = listing.mirrorCategory(responses.first()),
                    platform = platform.name.lowercase(),
                    lastUpdated = responses.maxOf { it.lastUpdated },
                    totalCount = repos.size,
                    repositories = repos,
                )

            if (responses.size == MIRROR_PLATFORMS.size) remember(cacheKey, merged)
            merged
        }

    private suspend fun fetchMirrorFile(
        path: String,
        filePlatform: DiscoveryPlatform,
    ): CachedRepoResponse? {
        val url = "$OFFLINE_MIRROR_BASE/cached-data/$path"
        return try {
            logger.debug("Fetching fallback: $url")
            networkFailures { mirrorBody(url) }
                ?.let { json.decodeFromString<CachedRepoResponse>(it) }
                ?.let { response ->
                    response.copy(
                        repositories =
                            response.repositories.map { repo ->
                                if (repo.availablePlatforms.isEmpty()) repo.copy(availablePlatforms = listOf(filePlatform)) else repo
                            },
                    )
                }
        } catch (e: SerializationException) {
            logger.error("Parse error from $url: ${e.message}")
            null
        } catch (e: NetworkException) {
            logger.error("Error with $url: ${e.message}")
            null
        }
    }

    private suspend fun mirrorBody(url: String): String? {
        val response = fallbackHttpClient.get(url)
        if (!response.status.isSuccess()) {
            logger.error("HTTP ${response.status.value} from $url")
            return null
        }
        return response.bodyAsText()
    }

    private companion object {
        val CACHE_TTL = 1.hours
        val MIRROR_PLATFORMS =
            mapOf(
                DiscoveryPlatform.Android to "android",
                DiscoveryPlatform.Windows to "windows",
                DiscoveryPlatform.Macos to "macos",
                DiscoveryPlatform.Linux to "linux",
            )
    }
}
