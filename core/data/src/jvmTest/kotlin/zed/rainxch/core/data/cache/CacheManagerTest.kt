package zed.rainxch.core.data.cache

import kotlinx.coroutines.runBlocking
import zed.rainxch.core.data.local.db.dao.CacheDao
import zed.rainxch.core.data.local.db.entities.CacheEntryEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CacheManagerTest {
    @Test
    fun a_row_that_cannot_be_read_is_a_miss_and_is_dropped() =
        runBlocking {
            val dao = FakeCacheDao(failReads = true)
            val cache = CacheManager(dao)

            assertNull(cache.get<List<String>>("details:releases:SagerNet/sing-box"))
            assertNull(cache.getStale<List<String>>("details:releases:SagerNet/sing-box"))
            assertNull(cache.getCachedAt("details:releases:SagerNet/sing-box"))
            assertEquals(List(3) { "details:releases:SagerNet/sing-box" }, dao.deleted)
        }

    @Test
    fun a_value_too_big_for_a_row_stays_in_memory_only() =
        runBlocking {
            val dao = FakeCacheDao()
            val cache = CacheManager(dao)
            val notes = "x".repeat(CacheManager.MAX_DISK_CHARS)

            cache.put("details:releases:big", listOf(notes), CacheManager.RELEASES)

            assertTrue(dao.rows.isEmpty())
            assertEquals(listOf("details:releases:big"), dao.deleted)
            assertEquals(listOf(notes), cache.get<List<String>>("details:releases:big"))
        }

    @Test
    fun a_normal_value_is_written_to_disk() =
        runBlocking {
            val dao = FakeCacheDao()
            val cache = CacheManager(dao)

            cache.put("details:releases:small", listOf("notes"), CacheManager.RELEASES)

            assertEquals(setOf("details:releases:small"), dao.rows.keys)
        }

    private class FakeCacheDao(
        private val failReads: Boolean = false,
    ) : CacheDao {
        val rows = mutableMapOf<String, CacheEntryEntity>()
        val deleted = mutableListOf<String>()

        private fun read(key: String): CacheEntryEntity? {
            check(!failReads) { "Row too big to fit into CursorWindow" }
            return rows[key]
        }

        override suspend fun getValid(
            key: String,
            now: Long,
        ): CacheEntryEntity? = read(key)?.takeIf { it.expiresAt > now }

        override suspend fun getAny(key: String): CacheEntryEntity? = read(key)

        override suspend fun getValidByPrefix(
            prefix: String,
            now: Long,
        ): List<CacheEntryEntity> = rows.values.filter { it.key.startsWith(prefix) && it.expiresAt > now }

        override suspend fun put(entry: CacheEntryEntity) {
            rows[entry.key] = entry
        }

        override suspend fun delete(key: String) {
            deleted += key
            rows.remove(key)
        }

        override suspend fun deleteIfMatches(
            key: String,
            cachedAt: Long,
        ) {
            if (rows[key]?.cachedAt == cachedAt) rows.remove(key)
        }

        override suspend fun deleteByPrefix(prefix: String) {
            rows.keys.removeAll { it.startsWith(prefix) }
        }

        override suspend fun deleteExpired(now: Long) {
            rows.values.removeAll { it.expiresAt <= now }
        }

        override suspend fun deleteAll() {
            rows.clear()
        }
    }
}
