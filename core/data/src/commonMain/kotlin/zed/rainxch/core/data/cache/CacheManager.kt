package zed.rainxch.core.data.cache

import co.touchlab.kermit.Logger
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
import zed.rainxch.core.data.repository.localStore
import zed.rainxch.core.domain.model.error.LocalStoreException
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
                try {
                    cleanupExpired()
                } catch (e: LocalStoreException) {
                    Logger.w(e) { "Cache cleanup failed; the next pass retries" }
                }
            }
        }
    }

    fun now(): Long = Clock.System.now().toEpochMilliseconds()

    suspend inline fun <reified T> get(key: String): T? {
        val currentTime = now()

        val cached = memoryCacheMutex.withLock { memoryCache[key] }
        val memoryHit = cached?.takeIf { (expiresAt, _) -> expiresAt > currentTime }
        if (cached != null && memoryHit == null) evictFromMemory(key, cached)

        val diskRow = if (memoryHit == null) readRow(key) { cacheDao.getValid(key, currentTime) } else null
        val snapshot = memoryHit ?: diskRow?.let { it.expiresAt to it.jsonData } ?: return null
        if (diskRow != null) {
            memoryCacheMutex.withLock {
                memoryCache[key] = snapshot
            }
        }

        return try {
            json.decodeFromString(serializer<T>(), snapshot.second)
        } catch (_: IllegalArgumentException) {
            if (diskRow != null) localStore { cacheDao.deleteIfMatches(key, diskRow.cachedAt) }
            evictFromMemory(key, snapshot)
            null
        }
    }

    @PublishedApi
    internal suspend fun evictFromMemory(
        key: String,
        expected: Pair<Long, String>,
    ) {
        memoryCacheMutex.withLock {
            if (memoryCache[key] == expected) memoryCache.remove(key)
        }
    }

    suspend inline fun <reified T> getStale(key: String): T? {
        val entry = readRow(key) { cacheDao.getAny(key) } ?: return null
        return try {
            json.decodeFromString(serializer<T>(), entry.jsonData)
        } catch (_: IllegalArgumentException) {
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

        if (jsonData.length > MAX_DISK_CHARS) {
            localStore { cacheDao.delete(key) }
            return
        }

        localStore {
            cacheDao.put(
                CacheEntryEntity(
                    key = key,
                    jsonData = jsonData,
                    cachedAt = currentTime,
                    expiresAt = expiresAt,
                ),
            )
        }
    }

    suspend fun getCachedAt(key: String): Long? = readRow(key) { cacheDao.getValid(key, now()) }?.cachedAt

    // A row over the 2 MB CursorWindow throws SQLiteBlobTooBigException on Android 9+ and IllegalStateException before.
    @PublishedApi
    internal suspend fun readRow(
        key: String,
        read: suspend () -> CacheEntryEntity?,
    ): CacheEntryEntity? =
        try {
            localStore { read() }
        } catch (e: CancellationException) {
            throw e
        } catch (_: LocalStoreException) {
            localStore { cacheDao.delete(key) }
            null
        } catch (_: IllegalStateException) {
            localStore { cacheDao.delete(key) }
            null
        }

    suspend fun invalidate(key: String) {
        memoryCacheMutex.withLock { memoryCache.remove(key) }
        localStore { cacheDao.delete(key) }
    }

    suspend fun clearAll() {
        memoryCacheMutex.withLock { memoryCache.clear() }
        localStore { cacheDao.deleteAll() }
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
        localStore { cacheDao.deleteExpired(currentTime) }
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

        // Up to 3 UTF-8 bytes a char keeps a row under the 2 MB CursorWindow; bigger values stay in memory.
        const val MAX_DISK_CHARS = 600_000
    }
}
