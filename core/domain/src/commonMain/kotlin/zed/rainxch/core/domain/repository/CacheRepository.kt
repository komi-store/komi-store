package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.Flow

interface CacheRepository {
    fun observeCacheSize(): Flow<Long>

    suspend fun clearCache()
}
