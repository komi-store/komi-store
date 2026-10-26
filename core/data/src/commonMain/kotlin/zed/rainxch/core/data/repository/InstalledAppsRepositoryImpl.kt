package zed.rainxch.core.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLiteException
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import zed.rainxch.core.data.local.db.AppDatabase
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.dao.UpdateHistoryDao
import zed.rainxch.core.data.local.db.entities.InstalledAppAwaitingInstall
import zed.rainxch.core.data.local.db.entities.InstalledAppVariantPin
import zed.rainxch.core.data.local.db.entities.UpdateHistoryEntity
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toEntity
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.SystemPackagesException
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.InstalledBuild
import zed.rainxch.core.domain.model.installation.ParkedInstallDisposal
import zed.rainxch.core.domain.model.installation.clearPending
import zed.rainxch.core.domain.model.installation.confirmInstall
import zed.rainxch.core.domain.model.installation.markPending
import zed.rainxch.core.domain.model.smartdetect.MatchingPreview
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.utils.ResolvedRelease

class InstalledAppsRepositoryImpl internal constructor(
    private val database: AppDatabase,
    private val installedAppsDao: InstalledAppDao,
    private val historyDao: UpdateHistoryDao,
    private val updateChecker: InstalledAppUpdateChecker,
) : InstalledAppsRepository {
    override suspend fun <R> executeInTransaction(block: suspend () -> R): R =
        localStore {
            database.useWriterConnection { transactor ->
                transactor.immediateTransaction {
                    block()
                }
            }
        }

    override fun getAllInstalledApps(): Flow<List<InstalledApp>> =
        installedAppsDao
            .getAllInstalledApps()
            .map { it.map { app -> app.toDomain() } }
            .catch { e -> throw if (e is SQLiteException) LocalStoreException(e) else e }

    override fun getAppsWithUpdates(): Flow<List<InstalledApp>> =
        installedAppsDao
            .getAppsWithUpdates()
            .map { it.map { app -> app.toDomain() } }

    override fun getUpdateCount(): Flow<Int> = installedAppsDao.getUpdateCount()

    override suspend fun getAppByPackage(packageName: String): InstalledApp? =
        installedAppsDao
            .getAppByPackage(packageName)
            ?.toDomain()

    override suspend fun getAppByRepoId(repoId: Long): InstalledApp? = installedAppsDao.getAppByRepoId(repoId)?.toDomain()

    override fun getAppByRepoIdAsFlow(repoId: Long): Flow<InstalledApp?> =
        installedAppsDao.getAppByRepoIdAsFlow(repoId).map { it?.toDomain() }

    override suspend fun getAppsByRepoId(repoId: Long): List<InstalledApp> = installedAppsDao.getAppsByRepoId(repoId).map { it.toDomain() }

    override fun getAppsByRepoIdAsFlow(repoId: Long): Flow<List<InstalledApp>> =
        installedAppsDao.getAppsByRepoIdAsFlow(repoId).map { list -> list.map { it.toDomain() } }

    override suspend fun isAppInstalled(repoId: Long): Boolean = installedAppsDao.getAppByRepoId(repoId) != null

    override suspend fun saveInstalledApp(app: InstalledApp) {
        installedAppsDao.insertApp(app.toEntity())
    }

    override suspend fun deleteInstalledApp(packageName: String) {
        localStore { installedAppsDao.deleteByPackageName(packageName) }
    }

    override suspend fun checkForUpdates(packageName: String): Boolean = updateChecker.checkForUpdates(packageName)

    override suspend fun checkForUpdatesWithReleases(
        packageName: String,
        releases: List<GithubRelease>,
    ): Boolean = updateChecker.checkForUpdatesWithReleases(packageName, releases)

    override suspend fun resolveTrackedRelease(packageName: String): ResolvedRelease? = updateChecker.resolveTrackedRelease(packageName)

    override suspend fun checkAllForUpdates() {
        updateChecker.checkAllForUpdates()
    }

    override suspend fun updateAppVersion(
        packageName: String,
        build: InstalledBuild,
        isPendingInstall: Boolean,
    ) {
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

    override suspend fun clearInstallBinding(packageName: String) {
        installedAppsDao.clearInstallBinding(packageName)
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
        installedAppsDao.updateInstalledVersion(
            packageName = packageName,
            installedVersion = installedVersion,
            installedVersionName = installedVersionName,
            installedVersionCode = installedVersionCode,
            isUpdateAvailable = isUpdateAvailable,
        )
    }

    override suspend fun updateSigningFingerprint(
        packageName: String,
        signingFingerprint: String,
    ) {
        installedAppsDao.updateSigningFingerprint(packageName, signingFingerprint)
    }

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

    override suspend fun setIncludePreReleases(
        packageName: String,
        enabled: Boolean,
    ) {
        installedAppsDao.updateIncludePreReleases(packageName, enabled)
    }

    override suspend fun setUpdateCheckEnabled(
        packageName: String,
        enabled: Boolean,
    ) {
        installedAppsDao.updateUpdateCheckEnabled(packageName, enabled)
        if (enabled) {
            recheckAfterChange(packageName, "enabled update checks")
        } else {
            installedAppsDao.clearUpdateMetadata(packageName, System.currentTimeMillis())
        }
    }

    override suspend fun setAssetFilter(
        packageName: String,
        regex: String?,
        fallbackToOlderReleases: Boolean,
    ) {
        val normalized = regex?.trim()?.takeIf { it.isNotEmpty() }
        installedAppsDao.updateAssetFilter(
            packageName = packageName,
            regex = normalized,
            fallback = fallbackToOlderReleases,
        )
        recheckAfterChange(packageName, "a new asset filter")
    }

    override suspend fun setPreferredVariant(
        packageName: String,
        variant: String?,
        tokens: String?,
        glob: String?,
        pickedIndex: Int?,
        siblingCount: Int?,
    ) {
        installedAppsDao.updatePreferredVariant(
            InstalledAppVariantPin(
                packageName = packageName,
                preferredAssetVariant = variant?.trim()?.takeIf { it.isNotEmpty() },
                preferredAssetTokens = tokens?.trim()?.takeIf { it.isNotEmpty() },
                assetGlobPattern = glob?.trim()?.takeIf { it.isNotEmpty() },
                pickedAssetIndex = pickedIndex,
                pickedAssetSiblingCount = siblingCount?.takeIf { it > 0 },
            ),
        )
        recheckAfterChange(packageName, "a new variant")
    }

    override suspend fun clearPreferredVariant(packageName: String) {
        setPreferredVariant(
            packageName = packageName,
            variant = null,
            tokens = null,
            glob = null,
            pickedIndex = null,
            siblingCount = null,
        )
    }

    override suspend fun setSkippedReleaseTag(
        packageName: String,
        tag: String?,
    ) {
        installedAppsDao.setSkippedReleaseTag(packageName, tag?.takeIf { it.isNotBlank() })
    }

    override fun getAppsWithSkippedReleaseTag(): Flow<List<InstalledApp>> =
        installedAppsDao
            .getAppsWithSkippedReleaseTag()
            .map { it.map { entity -> entity.toDomain() } }

    override suspend fun setPendingInstallFilePath(
        packageName: String,
        path: String?,
        version: String?,
        assetName: String?,
    ) {
        installedAppsDao.updatePendingInstallFilePath(
            packageName = packageName,
            path = path,
            version = version,
            assetName = assetName,
        )
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

    override suspend fun discardParkedInstall(
        packageName: String,
        expectedPath: String?,
    ): ParkedInstallDisposal = localStore { installedAppsDao.discardParkedInstall(packageName, expectedPath) }

    override suspend fun previewMatchingAssets(
        owner: String,
        repo: String,
        regex: String?,
        includePreReleases: Boolean,
        fallbackToOlderReleases: Boolean,
    ): MatchingPreview = updateChecker.previewMatchingAssets(owner, repo, regex, includePreReleases, fallbackToOlderReleases)

    // The preference is saved either way; a failed re-check leaves the next scheduled check to pick it up.
    private suspend fun recheckAfterChange(
        packageName: String,
        change: String,
    ) {
        try {
            updateChecker.checkForUpdates(packageName)
        } catch (e: LocalStoreException) {
            Logger.w(e) { "Saved $change for $packageName but the immediate re-check failed" }
        } catch (e: SystemPackagesException) {
            Logger.w(e) { "Saved $change for $packageName but the immediate re-check failed" }
        }
    }
}
