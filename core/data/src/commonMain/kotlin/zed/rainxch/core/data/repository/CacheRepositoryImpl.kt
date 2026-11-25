package zed.rainxch.core.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.services.FileLocationsProvider
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.repository.CacheRepository

class CacheRepositoryImpl(
    private val logger: KomiStoreLogger,
    private val fileLocationsProvider: FileLocationsProvider,
    private val cacheManager: CacheManager,
) : CacheRepository {
    override fun observeCacheSize(): Flow<Long> =
        flow {
            val sizeBytes = fileLocationsProvider.getCacheSizeBytes()
            emit(sizeBytes)
        }.flowOn(Dispatchers.IO)

    override suspend fun clearCache() {
        fileLocationsProvider.clearCacheFiles()
        cacheManager.clearAll()
        logger.debug("Cache cleared successfully")
    }
}
