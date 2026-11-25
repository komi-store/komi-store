package zed.rainxch.apps.domain.repository

import zed.rainxch.apps.domain.model.GithubRepoInfo
import zed.rainxch.core.domain.model.installation.DeviceApp

interface AppLinking {
    suspend fun getDeviceApps(): List<DeviceApp>

    suspend fun getTrackedPackageNames(): Set<String>

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
}
