package zed.rainxch.details.data.system

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.InstalledApp
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

        // DB may predate fingerprint recording. Fall back to the package actually installed
        // on this device; both sides are still local signatures. Unknown stays silent.
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

    @OptIn(ExperimentalTime::class)
    override suspend fun saveNewInstalledApp(params: SaveInstalledAppParams): InstalledApp? =
        try {
            val apkInfo = params.apkInfo
            val repo = params.repo

            val fingerprint =
                AssetVariant.fingerprintFromPickedAsset(
                    pickedAssetName = params.assetName,
                    siblingAssetCount = params.siblingAssetCount,
                    releaseTag = params.releaseTag,
                )
            val serializedTokens = fingerprint?.tokens?.let(AssetVariant::serializeTokens)
            val pickedIndex = params.pickedAssetIndex?.takeIf { it >= 0 }
            val siblingCount = params.siblingAssetCount.takeIf { it > 0 }

            val defaultIncludePreReleases =
                runCatching { tweaksRepository.getIncludePreReleases().first() }
                    .getOrDefault(false)

            val installedApp =
                InstalledApp(
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
                    // Details also installs a tracked app this way, and the insert replaces its row.
                    signingFingerprint =
                        apkInfo.signingFingerprint
                            ?: installedAppsRepository.getAppByPackage(apkInfo.packageName)?.signingFingerprint,
                    preferredAssetVariant = fingerprint?.variant,
                    preferredAssetTokens = serializedTokens,
                    assetGlobPattern = fingerprint?.glob,
                    pickedAssetIndex = pickedIndex,
                    pickedAssetSiblingCount = siblingCount,
                    includePreReleases = defaultIncludePreReleases,
                    pendingInstallFilePath = params.pendingInstallFilePath,
                    pendingInstallVersion =
                        params.releaseTag.takeIf { params.pendingInstallFilePath != null },
                    pendingInstallAssetName =
                        params.assetName.takeIf { params.pendingInstallFilePath != null },
                    sourceHost = params.sourceHost,
                )

            installedAppsRepository.saveInstalledApp(installedApp)

            if (params.isFavourite) {
                favouritesRepository.updateFavoriteInstallStatus(
                    repoId = repo.id,
                    installed = true,
                    packageName = apkInfo.packageName,
                )
            }

            delay(1000)
            val reloaded = installedAppsRepository.getAppByPackage(apkInfo.packageName)
            logger.debug("Successfully saved and reloaded app: ${reloaded?.packageName}")
            reloaded
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            logger.error("Failed to save installed app to database: ${t.message}")
            t.printStackTrace()
            null
        }

    override suspend fun updateInstalledAppVersion(params: UpdateInstalledAppParams) {
        installedAppsRepository.updateAppVersion(
            packageName = params.apkInfo.packageName,
            newTag = params.releaseTag,
            newReleaseId = params.releaseId,
            newAssetId = params.assetId,
            newAssetDigest = params.assetDigest,
            newReleasePublishedAt = params.releasePublishedAt,
            newAssetName = params.assetName,
            newAssetUrl = params.assetUrl,
            newVersionName = params.apkInfo.versionName,
            newVersionCode = params.apkInfo.versionCode,
            signingFingerprint = params.apkInfo.signingFingerprint,
            isPendingInstall = params.isPendingInstall,
        )
    }
}
