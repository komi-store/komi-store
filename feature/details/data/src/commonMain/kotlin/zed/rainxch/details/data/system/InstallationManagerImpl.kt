package zed.rainxch.details.data.system

import androidx.sqlite.SQLiteException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.InstalledBuild
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.utils.AssetVariant
import zed.rainxch.details.domain.model.ApkValidationResult
import zed.rainxch.details.domain.model.FingerprintCheckResult
import zed.rainxch.details.domain.model.SaveInstalledAppParams
import zed.rainxch.details.domain.model.UpdateInstalledAppParams
import zed.rainxch.details.domain.system.InstallationManager
import kotlin.time.Clock.System
import kotlin.time.ExperimentalTime

class InstallationManagerImpl(
    private val installer: Installer,
    private val installedAppsRepository: InstalledAppsRepository,
    private val favouritesRepository: FavouritesRepository,
    private val tweaksRepository: TweaksRepository,
    private val packageMonitor: PackageMonitor,
    private val logger: KomiStoreLogger,
) : InstallationManager {
    override suspend fun validateApk(
        filePath: String,
        isUpdate: Boolean,
        trackedPackageName: String?,
    ): ApkValidationResult {
        val apkInfo =
            installer.getApkInfoExtractor().extractPackageInfo(filePath)
                ?: return ApkValidationResult.ExtractionFailed

        if (isUpdate && trackedPackageName != null && apkInfo.packageName != trackedPackageName) {
            return ApkValidationResult.PackageMismatch(
                apkPackageName = apkInfo.packageName,
                installedPackageName = trackedPackageName,
            )
        }

        return ApkValidationResult.Valid(apkInfo)
    }

    override suspend fun checkSigningFingerprint(apkInfo: ApkPackageInfo): FingerprintCheckResult {
        val existingApp =
            installedAppsRepository.getAppByPackage(apkInfo.packageName)
                ?: return FingerprintCheckResult.Ok

        // Rows older than fingerprint recording fall back to the installed package; unknown stays silent.
        val expectedFp =
            existingApp.signingFingerprint
                ?: packageMonitor.getInstalledPackageInfo(apkInfo.packageName)?.signingFingerprint
                ?: return FingerprintCheckResult.Ok
        val actualFp = apkInfo.signingFingerprint ?: return FingerprintCheckResult.Ok

        return if (expectedFp == actualFp) {
            FingerprintCheckResult.Ok
        } else {
            FingerprintCheckResult.Mismatch(
                expectedFingerprint = expectedFp,
                actualFingerprint = actualFp,
            )
        }
    }

    override suspend fun saveNewInstalledApp(params: SaveInstalledAppParams): InstalledApp? =
        try {
            val packageName = params.apkInfo.packageName
            val installedApp =
                newInstalledApp(
                    params = params,
                    includePreReleases = tweaksRepository.getIncludePreReleases().first(),
                    // Details also installs a tracked app this way, and the insert replaces its row.
                    signingFingerprint =
                        params.apkInfo.signingFingerprint
                            ?: installedAppsRepository.getAppByPackage(packageName)?.signingFingerprint,
                ).withPickedVariant(params)

            installedAppsRepository.saveInstalledApp(installedApp)
            if (params.isFavourite) markFavouriteInstalled(params.repo.id, packageName)

            delay(RELOAD_DELAY_MS)
            val reloaded = installedAppsRepository.getAppByPackage(packageName)
            logger.debug("Successfully saved and reloaded app: ${reloaded?.packageName}")
            reloaded
        } catch (e: LocalStoreException) {
            logger.error("Failed to save installed app to database: ${e.message}", e)
            null
        }

    @OptIn(ExperimentalTime::class)
    private fun newInstalledApp(
        params: SaveInstalledAppParams,
        includePreReleases: Boolean,
        signingFingerprint: String?,
    ): InstalledApp {
        val apkInfo = params.apkInfo
        val repo = params.repo
        return InstalledApp(
            packageName = apkInfo.packageName,
            repoId = repo.id,
            repoName = repo.name,
            repoOwner = repo.owner.login,
            repoOwnerAvatarUrl = repo.owner.avatarUrl,
            repoDescription = repo.description,
            primaryLanguage = repo.language,
            repoUrl = repo.htmlUrl,
            installedVersion = params.releaseTag,
            installedAssetName = params.assetName,
            installedAssetUrl = params.assetUrl,
            installedReleaseId = params.releaseId,
            installedAssetId = params.assetId,
            installedAssetDigest = params.assetDigest,
            installedReleasePublishedAt = params.releasePublishedAt,
            latestVersion = null,
            latestAssetName = null,
            latestAssetUrl = null,
            latestAssetSize = null,
            appName = apkInfo.appName,
            installSource = InstallSource.THIS_APP,
            installedAt = System.now().toEpochMilliseconds(),
            lastCheckedAt = System.now().toEpochMilliseconds(),
            lastUpdatedAt = System.now().toEpochMilliseconds(),
            isUpdateAvailable = false,
            updateCheckEnabled = true,
            releaseNotes = "",
            systemArchitecture = installer.detectSystemArchitecture().name,
            fileExtension = params.assetName.substringAfterLast('.', ""),
            isPendingInstall = params.isPendingInstall,
            installedVersionName = apkInfo.versionName,
            installedVersionCode = apkInfo.versionCode,
            latestVersionName = null,
            latestVersionCode = null,
            signingFingerprint = signingFingerprint,
            includePreReleases = includePreReleases,
            pendingInstallFilePath = params.pendingInstallFilePath,
            pendingInstallVersion = params.releaseTag.takeIf { params.pendingInstallFilePath != null },
            pendingInstallAssetName = params.assetName.takeIf { params.pendingInstallFilePath != null },
            sourceHost = params.sourceHost,
        )
    }

    private fun InstalledApp.withPickedVariant(params: SaveInstalledAppParams): InstalledApp {
        val fingerprint =
            AssetVariant.fingerprintFromPickedAsset(
                pickedAssetName = params.assetName,
                siblingAssetCount = params.siblingAssetCount,
                releaseTag = params.releaseTag,
            )
        return copy(
            preferredAssetVariant = fingerprint?.variant,
            preferredAssetTokens = fingerprint?.tokens?.let(AssetVariant::serializeTokens),
            assetGlobPattern = fingerprint?.glob,
            pickedAssetIndex = params.pickedAssetIndex?.takeIf { it >= 0 },
            pickedAssetSiblingCount = params.siblingAssetCount.takeIf { it > 0 },
        )
    }

    private suspend fun markFavouriteInstalled(
        repoId: Long,
        packageName: String,
    ) {
        try {
            favouritesRepository.updateFavoriteInstallStatus(repoId = repoId, installed = true, packageName = packageName)
        } catch (e: SQLiteException) {
            // FavouritesRepositoryImpl hands Room's failure through untyped.
            throw LocalStoreException(e)
        }
    }

    override suspend fun updateInstalledAppVersion(params: UpdateInstalledAppParams) {
        installedAppsRepository.updateAppVersion(
            packageName = params.apkInfo.packageName,
            build =
                InstalledBuild(
                    tag = params.releaseTag,
                    releaseId = params.releaseId,
                    assetId = params.assetId,
                    assetDigest = params.assetDigest,
                    releasePublishedAt = params.releasePublishedAt,
                    assetName = params.assetName,
                    assetUrl = params.assetUrl,
                    versionName = params.apkInfo.versionName,
                    versionCode = params.apkInfo.versionCode,
                    signingFingerprint = params.apkInfo.signingFingerprint,
                ),
            isPendingInstall = params.isPendingInstall,
        )
    }

    private companion object {
        const val RELOAD_DELAY_MS = 1_000L
    }
}
