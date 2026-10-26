package zed.rainxch.core.data.local.db.dao

import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.data.local.db.entities.StarredRepositoryEntity

interface StarredRepoReads {
    @Query("SELECT * FROM starred_repos ORDER BY starredAt DESC")
    fun getAllStarred(): Flow<List<StarredRepositoryEntity>>

    @Query("SELECT * FROM starred_repos WHERE repoId = :repoId")
    suspend fun getStarredById(repoId: Long): StarredRepositoryEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM starred_repos WHERE repoId = :repoId)")
    suspend fun isStarred(repoId: Long): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM starred_repos WHERE repoId = :repoId)")
    fun isStarredFlow(repoId: Long): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM starred_repos WHERE repoId = :repoId)")
    suspend fun isStarredSync(repoId: Long): Boolean

    @Query("SELECT COUNT(*) FROM starred_repos")
    suspend fun getCount(): Int

    @Query("SELECT MAX(lastSyncedAt) FROM starred_repos")
    suspend fun getLastSyncTime(): Long?
}
