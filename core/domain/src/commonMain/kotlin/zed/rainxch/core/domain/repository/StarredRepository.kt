package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.repository.StarredRepository

interface StarredRepository {
    fun getAllStarred(): Flow<List<StarredRepository>>

    suspend fun isStarred(repoId: Long): Boolean

    suspend fun setStarred(
        owner: String,
        repo: String,
        starred: Boolean,
    ): Result<Unit>

    suspend fun syncStarredRepos(forceRefresh: Boolean = false): Result<Unit>

    suspend fun fetchStarredForUsername(username: String): Result<List<StarredRepository>>

    suspend fun getLastSyncTime(): Long?

    suspend fun needsSync(): Boolean
}
