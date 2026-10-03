package zed.rainxch.feed.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.dto.BackendFeedResponse
import zed.rainxch.core.data.mappers.toSummary
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.OFFLINE_MIRROR_BASE
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.feed.domain.model.FeedPage
import zed.rainxch.feed.domain.repository.FeedRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class FeedRepositoryImpl(
    private val backendApiClient: BackendApiClient,
    private val cacheManager: CacheManager,
    private val logger: KomiStoreLogger,
) : FeedRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val offlineClient = HttpClient {
        install(HttpTimeout) {
            requestTimeoutMillis = 10_000
            connectTimeoutMillis = 5_000
            socketTimeoutMillis = 10_000
        }
        expectSuccess = false
    }

    override suspend fun getFeed(
        platform: DiscoveryPlatform,
        page: Int,
        forceRefresh: Boolean,
    ): Result<FeedPage> = withContext(Dispatchers.IO) {
        val slug = platform.toApiSlug()
        val token = slug ?: ALL_TOKEN
        val today = todayUtc()
        val readKey = cacheKey(token, page, today)

        if (!forceRefresh) {
            runCatching { cacheManager.get<BackendFeedResponse>(readKey) }
                .getOrNull()
                ?.let { return@withContext Result.success(it.toFeedPage(fromCache = true, platform = platform)) }
        }

        backendApiClient.getFeed(slug, page).fold(
            onSuccess = { response ->
                val writeKey = cacheKey(token, page, response.rotation.ifBlank { today })
                runCatching { cacheManager.put(writeKey, response, CacheManager.FEED) }
                Result.success(response.toFeedPage(fromCache = false, platform = platform))
            },
            onFailure = { error ->
                runCatching { cacheManager.getStale<BackendFeedResponse>(readKey) }
                    .getOrNull()
                    ?.let { return@withContext Result.success(it.toFeedPage(fromCache = true, platform = platform)) }

                if (page == 1) {
                    fetchOffline(platform)?.let { return@withContext Result.success(it) }
                }
                Result.failure(error)
            },
        )
    }

    private suspend fun fetchOffline(platform: DiscoveryPlatform): FeedPage? {
        val slugs = when (platform) {
            DiscoveryPlatform.All -> listOf("android", "windows", "macos", "linux")
            else -> listOfNotNull(platform.toApiSlug())
        }
        if (slugs.isEmpty()) return null

        val responses = coroutineScope {
            slugs.map { slug -> async { fetchOfflineFile(slug) } }.awaitAll().filterNotNull()
        }
        if (responses.isEmpty()) return null

        val merged = responses.flatMap { it.items }.distinctBy { it.id }
        return FeedPage(
            items = merged.map { it.toSummary(platform) },
            page = 1,
            hasMore = false,
            rotation = responses.firstOrNull()?.rotation.orEmpty(),
            fromCache = true,
            isOffline = true,
        )
    }

    private suspend fun fetchOfflineFile(slug: String): BackendFeedResponse? =
        try {
            val url = "$OFFLINE_MIRROR_BASE/cached-data/feed/$slug.json"
            val response = offlineClient.get(url)
            if (response.status.isSuccess()) {
                json.decodeFromString<BackendFeedResponse>(response.bodyAsText())
            } else {
                logger.error("Feed offline fetch HTTP ${response.status.value} from $url")
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Feed offline fetch failed for $slug: ${e.message}")
            null
        }

    private fun BackendFeedResponse.toFeedPage(
        fromCache: Boolean,
        platform: DiscoveryPlatform,
    ): FeedPage =
        FeedPage(
            items = items.map { it.toSummary(platform) },
            page = page,
            hasMore = hasMore,
            rotation = rotation,
            fromCache = fromCache,
        )

    private fun cacheKey(token: String, page: Int, rotation: String): String =
        "feed:$token:$page:$rotation"

    private fun todayUtc(): String =
        Clock.System.now().toLocalDateTime(TimeZone.UTC).date.toString()

    private fun DiscoveryPlatform.toApiSlug(): String? = when (this) {
        DiscoveryPlatform.Android -> "android"
        DiscoveryPlatform.Windows -> "windows"
        DiscoveryPlatform.Macos -> "macos"
        DiscoveryPlatform.Linux -> "linux"
        DiscoveryPlatform.Ios -> "ios"
        DiscoveryPlatform.All -> null
    }

    companion object {
        private const val ALL_TOKEN = "all"
    }
}
