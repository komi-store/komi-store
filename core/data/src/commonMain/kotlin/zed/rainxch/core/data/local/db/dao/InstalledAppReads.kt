package zed.rainxch.core.data.local.db.dao

import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity

interface InstalledAppReads {
    @Query("SELECT * FROM installed_apps ORDER BY installedAt DESC")
    fun getAllInstalledApps(): Flow<List<InstalledAppEntity>>

    @Query("SELECT * FROM installed_apps WHERE isUpdateAvailable = 1 ORDER BY lastCheckedAt DESC")
    fun getAppsWithUpdates(): Flow<List<InstalledAppEntity>>

    @Query("SELECT * FROM installed_apps WHERE packageName = :packageName")
    suspend fun getAppByPackage(packageName: String): InstalledAppEntity?

    @Query("SELECT * FROM installed_apps WHERE repoId = :repoId")
    suspend fun getAppByRepoId(repoId: Long): InstalledAppEntity?

    @Query("SELECT * FROM installed_apps WHERE repoId = :repoId")
    fun getAppByRepoIdAsFlow(repoId: Long): Flow<InstalledAppEntity?>

    @Query("SELECT * FROM installed_apps WHERE repoId = :repoId ORDER BY installedAt DESC")
    suspend fun getAppsByRepoId(repoId: Long): List<InstalledAppEntity>

    @Query("SELECT * FROM installed_apps WHERE repoId = :repoId ORDER BY installedAt DESC")
    fun getAppsByRepoIdAsFlow(repoId: Long): Flow<List<InstalledAppEntity>>

    @Query("SELECT COUNT(*) FROM installed_apps WHERE isUpdateAvailable = 1")
    fun getUpdateCount(): Flow<Int>

    @Query("SELECT packageName FROM installed_apps")
    suspend fun getTrackedPackageNames(): List<String>

    @Query(
        """
        SELECT * FROM installed_apps
         WHERE skippedReleaseTag IS NOT NULL
         ORDER BY appName COLLATE NOCASE ASC
        """,
    )
    fun getAppsWithSkippedReleaseTag(): Flow<List<InstalledAppEntity>>
}
