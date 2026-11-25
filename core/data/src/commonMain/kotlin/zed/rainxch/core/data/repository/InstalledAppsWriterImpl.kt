package zed.rainxch.core.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import co.touchlab.kermit.Logger
import zed.rainxch.core.data.local.db.AppDatabase
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.dao.UpdateHistoryDao
import zed.rainxch.core.data.local.db.entities.UpdateHistoryEntity
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toEntity
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.InstalledBuild
import zed.rainxch.core.domain.model.installation.confirmInstall
import zed.rainxch.core.domain.repository.installedapps.InstalledAppsWriter

internal class InstalledAppsWriterImpl(
    private val database: AppDatabase,
    private val installedAppsDao: InstalledAppDao,
    private val historyDao: UpdateHistoryDao,
) : InstalledAppsWriter {
    override suspend fun <R> executeInTransaction(block: suspend () -> R): R =
        localStore {
            database.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    block()
                }
            }
        }

    override suspend fun saveInstalledApp(app: InstalledApp) {
        localStore { installedAppsDao.insertApp(app.toEntity()) }
    }

    override suspend fun deleteInstalledApp(packageName: String) {
        localStore { installedAppsDao.deleteByPackageName(packageName) }
    }

    override suspend fun updateAppVersion(
        packageName: String,
        build: InstalledBuild,
        isPendingInstall: Boolean,
    ) {
        localStore {
            val app = installedAppsDao.getAppByPackage(packageName) ?: return

            Logger.d {
                "Updating app version: $packageName from ${app.installedVersion} to ${build.tag}"
            }

            historyDao.insertHistory(
                UpdateHistoryEntity(
                    packageName = packageName,
                    appName = app.appName,
                    repoOwner = app.repoOwner,
                    repoName = app.repoName,
                    fromVersion = app.installedVersion,
                    toVersion = build.tag,
                    updatedAt = System.currentTimeMillis(),
                    updateSource = InstallSource.THIS_APP,
                    success = true,
                ),
            )

            installedAppsDao.updateApp(
                app
                    .toDomain()
                    .confirmInstall(
                        build = build,
                        isPending = isPendingInstall,
                        at = System.currentTimeMillis(),
                    ).toEntity(),
            )
        }
    }

    override suspend fun clearInstallBinding(packageName: String) {
        localStore { installedAppsDao.clearInstallBinding(packageName) }
    }

    override suspend fun updateApp(app: InstalledApp) {
        localStore { installedAppsDao.updateApp(app.toEntity()) }
    }

    override suspend fun updateInstalledVersion(
        packageName: String,
        installedVersion: String,
        installedVersionName: String?,
        installedVersionCode: Long,
        isUpdateAvailable: Boolean,
    ) {
        localStore {
            installedAppsDao.updateInstalledVersion(
                packageName = packageName,
                installedVersion = installedVersion,
                installedVersionName = installedVersionName,
                installedVersionCode = installedVersionCode,
                isUpdateAvailable = isUpdateAvailable,
            )
        }
    }

    override suspend fun updateSigningFingerprint(
        packageName: String,
        signingFingerprint: String,
    ) {
        localStore { installedAppsDao.updateSigningFingerprint(packageName, signingFingerprint) }
    }
}
