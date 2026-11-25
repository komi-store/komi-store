package zed.rainxch.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.repository.installedapps.InstalledAppsReader

internal class InstalledAppsReaderImpl(
    private val installedAppsDao: InstalledAppDao,
) : InstalledAppsReader {
    override fun getAllInstalledApps(): Flow<List<InstalledApp>> =
        installedAppsDao
            .getAllInstalledApps()
            .map { it.map { app -> app.toDomain() } }
            .localStoreFailures()

    override fun getAppsWithUpdates(): Flow<List<InstalledApp>> =
        installedAppsDao
            .getAppsWithUpdates()
            .map { it.map { app -> app.toDomain() } }
            .localStoreFailures()

    override fun getUpdateCount(): Flow<Int> = installedAppsDao.getUpdateCount().localStoreFailures()

    override suspend fun getAppByPackage(packageName: String): InstalledApp? =
        localStore {
            installedAppsDao
                .getAppByPackage(packageName)
                ?.toDomain()
        }

    override suspend fun getAppByRepoId(repoId: Long): InstalledApp? = localStore { installedAppsDao.getAppByRepoId(repoId)?.toDomain() }

    override fun getAppByRepoIdAsFlow(repoId: Long): Flow<InstalledApp?> =
        installedAppsDao.getAppByRepoIdAsFlow(repoId).map { it?.toDomain() }.localStoreFailures()

    override suspend fun getAppsByRepoId(repoId: Long): List<InstalledApp> =
        localStore { installedAppsDao.getAppsByRepoId(repoId).map { it.toDomain() } }

    override fun getAppsByRepoIdAsFlow(repoId: Long): Flow<List<InstalledApp>> =
        installedAppsDao.getAppsByRepoIdAsFlow(repoId).map { list -> list.map { it.toDomain() } }.localStoreFailures()

    override suspend fun isAppInstalled(repoId: Long): Boolean = localStore { installedAppsDao.getAppByRepoId(repoId) != null }

    override fun getAppsWithSkippedReleaseTag(): Flow<List<InstalledApp>> =
        installedAppsDao
            .getAppsWithSkippedReleaseTag()
            .map { it.map { entity -> entity.toDomain() } }
            .localStoreFailures()
}
