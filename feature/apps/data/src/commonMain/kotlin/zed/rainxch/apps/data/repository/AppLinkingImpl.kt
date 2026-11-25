package zed.rainxch.apps.data.repository

import kotlinx.coroutines.flow.first
import zed.rainxch.apps.domain.model.GithubRepoInfo
import zed.rainxch.apps.domain.repository.AppLinking
import zed.rainxch.core.domain.model.installation.DeviceApp
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.utils.AssetVariant
import zed.rainxch.core.domain.utils.VersionMath
import kotlin.time.Clock

internal class AppLinkingImpl(
    private val installedApps: InstalledAppsRepository,
    private val packageMonitor: PackageMonitor,
    private val tweaksRepository: TweaksRepository,
) : AppLinking {
    override suspend fun getDeviceApps(): List<DeviceApp> = packageMonitor.getAllInstalledApps()

    override suspend fun getTrackedPackageNames(): Set<String> =
        installedApps
            .getAllInstalledApps()
            .first()
            .map { it.packageName }
            .toSet()

    override suspend fun linkAppToRepo(
        deviceApp: DeviceApp,
        repoInfo: GithubRepoInfo,
        assetFilterRegex: String?,
        fallbackToOlderReleases: Boolean,
        pickedAssetName: String?,
        pickedAssetSiblingCount: Int,
        pickedAssetReleaseTag: String?,
        preferredAssetVariant: String?,
        preferredAssetTokens: String?,
        assetGlobPattern: String?,
        pickedAssetIndex: Int?,
        sourceHost: String?,
    ) {
        val pick =
            PickedAsset(
                name = pickedAssetName,
                siblingCount = pickedAssetSiblingCount,
                releaseTag = pickedAssetReleaseTag,
                index = pickedAssetIndex,
            )
        val variant = variantPin(pick, preferredAssetVariant, preferredAssetTokens, assetGlobPattern)
        val linked =
            linkedApp(deviceApp, repoInfo, Clock.System.now().toEpochMilliseconds()).copy(
                includePreReleases = tweaksRepository.getIncludePreReleases().first(),
                assetFilterRegex = assetFilterRegex?.trim()?.takeIf { it.isNotEmpty() },
                fallbackToOlderReleases = fallbackToOlderReleases,
                preferredAssetVariant = variant.variant,
                preferredAssetTokens = variant.tokens,
                assetGlobPattern = variant.glob,
                pickedAssetIndex = pick.index,
                pickedAssetSiblingCount = pick.siblingCount.takeIf { it > 0 },
                sourceHost = sourceHost,
            )
        installedApps.saveInstalledApp(linked)
    }

    private data class PickedAsset(
        val name: String?,
        val siblingCount: Int,
        val releaseTag: String?,
        val index: Int?,
    )

    private data class VariantPin(
        val variant: String?,
        val tokens: String?,
        val glob: String?,
    )

    // A fresh fingerprint is taken from the picked asset only when the caller brought no tokens or glob of its own.
    private fun variantPin(
        pick: PickedAsset,
        preferredVariant: String?,
        preferredTokens: String?,
        glob: String?,
    ): VariantPin {
        val variant =
            preferredVariant?.trim()?.takeIf { it.isNotEmpty() }
                ?: pick.name?.let { AssetVariant.deriveFromPickedAsset(it, pick.siblingCount, pick.releaseTag) }
        val fresh =
            if (preferredTokens == null && glob == null && pick.name != null) {
                AssetVariant.fingerprintFromPickedAsset(pick.name, pick.siblingCount, pick.releaseTag)
            } else {
                null
            }
        return VariantPin(
            variant = variant,
            tokens = preferredTokens ?: fresh?.tokens?.let { AssetVariant.serializeTokens(it) },
            glob = glob ?: fresh?.glob,
        )
    }

    // An unreconcilable versionName stores the release tag instead, so update checks compare tag to tag (GH#729).
    private fun linkedApp(
        deviceApp: DeviceApp,
        repoInfo: GithubRepoInfo,
        now: Long,
    ): InstalledApp {
        val reconcilable = VersionMath.versionsReconcilable(deviceApp.versionName, repoInfo.latestReleaseTag)
        val isUpdateAvailable =
            reconcilable && VersionMath.isVersionNewer(candidate = repoInfo.latestReleaseTag, current = deviceApp.versionName)
        val installedVersion =
            if (reconcilable) {
                deviceApp.versionName ?: UNKNOWN_VERSION
            } else {
                repoInfo.latestReleaseTag ?: deviceApp.versionName ?: UNKNOWN_VERSION
            }
        return InstalledApp(
            packageName = deviceApp.packageName,
            repoId = repoInfo.id,
            repoName = repoInfo.name,
            repoOwner = repoInfo.owner,
            repoOwnerAvatarUrl = repoInfo.ownerAvatarUrl,
            repoDescription = repoInfo.description,
            primaryLanguage = repoInfo.language,
            repoUrl = repoInfo.htmlUrl,
            installedVersion = installedVersion,
            installedAssetName = null,
            installedAssetUrl = null,
            latestVersion = repoInfo.latestReleaseTag,
            latestAssetName = null,
            latestAssetUrl = null,
            latestAssetSize = null,
            appName = deviceApp.appName,
            installSource = InstallSource.MANUAL,
            installedAt = now,
            lastCheckedAt = 0L,
            lastUpdatedAt = now,
            isUpdateAvailable = isUpdateAvailable,
            updateCheckEnabled = true,
            releaseNotes = null,
            systemArchitecture = "",
            fileExtension = "apk",
            isPendingInstall = false,
            installedVersionName = deviceApp.versionName,
            installedVersionCode = deviceApp.versionCode,
            signingFingerprint = deviceApp.signingFingerprint,
            preferredVariantStale = false,
        )
    }

    private companion object {
        const val UNKNOWN_VERSION = "unknown"
    }
}
