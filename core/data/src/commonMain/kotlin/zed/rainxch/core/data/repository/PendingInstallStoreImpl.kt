package zed.rainxch.core.data.repository

import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.entities.InstalledAppAwaitingInstall
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toEntity
import zed.rainxch.core.domain.model.installation.ParkedInstallDisposal
import zed.rainxch.core.domain.model.installation.clearPending
import zed.rainxch.core.domain.model.installation.markPending
import zed.rainxch.core.domain.repository.installedapps.PendingInstallStore

internal class PendingInstallStoreImpl(
    private val installedAppsDao: InstalledAppDao,
) : PendingInstallStore {
    override suspend fun updatePendingStatus(
        packageName: String,
        isPending: Boolean,
    ) {
        localStore {
            val app = installedAppsDao.getAppByPackage(packageName) ?: return
            installedAppsDao.updateApp(
                app
                    .toDomain()
                    .let {
                        if (isPending) {
                            it.markPending(
                                releaseId = app.latestReleaseId,
                                assetId = app.latestAssetId,
                                assetDigest = app.latestAssetDigest,
                                releasePublishedAt = app.latestReleasePublishedAt,
                            )
                        } else {
                            it.clearPending()
                        }
                    }.toEntity(),
            )
        }
    }

    override suspend fun setPendingInstallFilePath(
        packageName: String,
        path: String?,
        version: String?,
        assetName: String?,
    ) {
        localStore {
            installedAppsDao.updatePendingInstallFilePath(
                packageName = packageName,
                path = path,
                version = version,
                assetName = assetName,
            )
        }
    }

    override suspend fun markAwaitingInstall(
        packageName: String,
        path: String,
        version: String?,
        assetName: String?,
        releaseId: Long?,
        assetId: Long?,
        releasePublishedAt: String?,
        assetDigest: String?,
    ) {
        localStore {
            installedAppsDao.markAwaitingInstall(
                InstalledAppAwaitingInstall(
                    packageName = packageName,
                    pendingInstallFilePath = path,
                    pendingInstallVersion = version,
                    pendingInstallAssetName = assetName,
                    pendingInstallReleaseId = releaseId,
                    pendingInstallAssetId = assetId,
                    pendingInstallAssetDigest = assetDigest,
                    pendingInstallReleasePublishedAt = releasePublishedAt,
                ),
            )
        }
    }

    override suspend fun discardParkedInstall(
        packageName: String,
        expectedPath: String?,
    ): ParkedInstallDisposal = localStore { installedAppsDao.discardParkedInstall(packageName, expectedPath) }
}
