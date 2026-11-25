package zed.rainxch.core.domain.repository.installedapps

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.installation.InstalledApp

interface InstalledAppsReader {
    fun getAllInstalledApps(): Flow<List<InstalledApp>>

    fun getAppsWithUpdates(): Flow<List<InstalledApp>>

    fun getUpdateCount(): Flow<Int>

    suspend fun getAppByPackage(packageName: String): InstalledApp?

    suspend fun getAppByRepoId(repoId: Long): InstalledApp?

    fun getAppByRepoIdAsFlow(repoId: Long): Flow<InstalledApp?>

    suspend fun getAppsByRepoId(repoId: Long): List<InstalledApp>

    fun getAppsByRepoIdAsFlow(repoId: Long): Flow<List<InstalledApp>>

    suspend fun isAppInstalled(repoId: Long): Boolean

    fun getAppsWithSkippedReleaseTag(): Flow<List<InstalledApp>>
}
