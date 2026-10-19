package zed.rainxch.apps.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.apps.domain.model.GithubRepoInfo
import zed.rainxch.apps.domain.model.ImportResult
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.installation.DeviceApp
import zed.rainxch.core.domain.model.installation.InstalledApp

interface AppsRepository {
    suspend fun getApps(): Flow<List<InstalledApp>>

    suspend fun openApp(
        installedApp: InstalledApp,
        onCantLaunchApp: () -> Unit = { },
    )

    suspend fun getLatestRelease(
        owner: String,
        repo: String,
        includePreReleases: Boolean = false,
        sourceHost: String? = null,
    ): GithubRelease?

    suspend fun getDeviceApps(): List<DeviceApp>

    suspend fun getTrackedPackageNames(): Set<String>

    suspend fun fetchRepoInfo(
        owner: String,
        repo: String,
        sourceHost: String? = null,
    ): GithubRepoInfo?

    suspend fun linkAppToRepo(
        deviceApp: DeviceApp,
        repoInfo: GithubRepoInfo,
        assetFilterRegex: String? = null,
        fallbackToOlderReleases: Boolean = false,
        pickedAssetName: String? = null,
        pickedAssetSiblingCount: Int = 0,
        pickedAssetReleaseTag: String? = null,
        preferredAssetVariant: String? = null,
        preferredAssetTokens: String? = null,
        assetGlobPattern: String? = null,
        pickedAssetIndex: Int? = null,
        sourceHost: String? = null,
    )

    suspend fun exportApps(): String

    suspend fun exportObtainium(): String

    suspend fun importApps(json: String): ImportResult
}
