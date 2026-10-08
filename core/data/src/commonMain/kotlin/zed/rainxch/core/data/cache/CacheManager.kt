package zed.rainxch.core.data.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import zed.rainxch.core.data.local.db.dao.CacheDao
import zed.rainxch.core.data.local.db.entities.CacheEntryEntity
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours

class CacheManager(
    @PublishedApi internal val cacheDao: CacheDao,
    appScope: CoroutineScope? = null,
) {
    @PublishedApi
    internal val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
        }

    @PublishedApi
    internal val memoryCache = HashMap<String, Pair<Long, String>>()

    @PublishedApi
    internal val memoryCacheMutex = Mutex()

    init {
        appScope?.launch {
            while (true) {
                delay(CLEANUP_INTERVAL_MS)
                runCatching { cleanupExpired() }
            }
        }
    }

    fun now(): Long = Clock.System.now().toEpochMilliseconds()

    suspend inline fun <reified T> get(key: String): T? {
        val currentTime = now()

        val cached = memoryCacheMutex.withLock { memoryCache[key] }
        if (cached != null) {
            val (expiresAt, jsonData) = cached
            if (expiresAt > currentTime) {
                return try {
                    json.decodeFromString(serializer<T>(), jsonData)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    memoryCacheMutex.withLock {
                        if (memoryCache[key] == cached) memoryCache.remove(key)
                    }
                    null
                }
            } else {
                memoryCacheMutex.withLock {
                    if (memoryCache[key] == cached) memoryCache.remove(key)
                }
            }
        }

        val entry = cacheDao.getValid(key, currentTime) ?: return null
        val snapshot = entry.expiresAt to entry.jsonData
        memoryCacheMutex.withLock {
            memoryCache[key] = snapshot
        }

        return try {
            json.decodeFromString(serializer<T>(), entry.jsonData)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            cacheDao.deleteIfMatches(key, entry.cachedAt)
            memoryCacheMutex.withLock {
                if (memoryCache[key] == snapshot) memoryCache.remove(key)
            }
            null
        }
    }

    suspend inline fun <reified T> getStale(key: String): T? {
        val entry = cacheDao.getAny(key) ?: return null
        return try {
            json.decodeFromString(serializer<T>(), entry.jsonData)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    suspend inline fun <reified T> put(
        key: String,
        value: T,
        ttlMillis: Long,
    ) {
        val currentTime = now()
        val jsonData = json.encodeToString(serializer<T>(), value)
        val expiresAt = currentTime + ttlMillis

        memoryCacheMutex.withLock {
            memoryCache[key] = expiresAt to jsonData
        }

        cacheDao.put(
            CacheEntryEntity(
                key = key,
                jsonData = jsonData,
                cachedAt = currentTime,
                expiresAt = expiresAt,
            ),
        )
    }

    // When the still-valid entry was written — what a caller needs to decide whether reading
    // it again is worth another request.
    suspend fun getCachedAt(key: String): Long? = cacheDao.getValid(key, now())?.cachedAt

    suspend fun invalidate(key: String) {
        memoryCacheMutex.withLock { memoryCache.remove(key) }
        cacheDao.delete(key)
    }

    suspend fun clearAll() {
        memoryCacheMutex.withLock { memoryCache.clear() }
        cacheDao.deleteAll()
    }

    suspend fun cleanupExpired() {
        val currentTime = now()
        memoryCacheMutex.withLock {
            val expiredKeys =
                memoryCache.entries
                    .filter { it.value.first <= currentTime }
                    .map { it.key }
            expiredKeys.forEach { memoryCache.remove(it) }
        }
        cacheDao.deleteExpired(currentTime)
    }

    companion object CacheTtl {
        val HOME_REPOS = 24.hours.inWholeMilliseconds
        val FEED = 3.hours.inWholeMilliseconds
        val REPO_DETAILS = 6.hours.inWholeMilliseconds
        val RELEASES = 6.hours.inWholeMilliseconds
        val README = 12.hours.inWholeMilliseconds
        val USER_PROFILE = 6.hours.inWholeMilliseconds
        val SEARCH_RESULTS = 1.hours.inWholeMilliseconds
        val REPO_STATS = 6.hours.inWholeMilliseconds
        const val CLEANUP_INTERVAL_MS: Long = 60L * 60L * 1000L
    }
}
