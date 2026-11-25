package zed.rainxch.search.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import zed.rainxch.core.data.local.db.dao.SearchHistoryDao
import zed.rainxch.core.data.local.db.entities.SearchHistoryEntity
import zed.rainxch.core.data.repository.localStore
import zed.rainxch.core.data.repository.localStoreFailures
import zed.rainxch.search.domain.repository.SearchHistoryRepository
import kotlin.time.Clock

class SearchHistoryRepositoryImpl(
    private val searchHistoryDao: SearchHistoryDao,
) : SearchHistoryRepository {
    override fun getRecentSearches(): Flow<List<String>> =
        searchHistoryDao
            .getRecentSearches()
            .map { entities ->
                entities.map { it.query }
            }.localStoreFailures()

    override suspend fun addSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        localStore {
            searchHistoryDao.insert(
                SearchHistoryEntity(
                    query = trimmed,
                    searchedAt = Clock.System.now().toEpochMilliseconds(),
                ),
            )
        }
    }

    override suspend fun removeSearch(query: String) {
        localStore { searchHistoryDao.deleteByQuery(query) }
    }

    override suspend fun clearAll() {
        localStore { searchHistoryDao.clearAll() }
    }
}
