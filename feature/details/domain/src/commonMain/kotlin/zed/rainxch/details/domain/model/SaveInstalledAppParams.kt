package zed.rainxch.details.domain.model

import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary

data class SaveInstalledAppParams(
    val repo: GithubRepoSummary,
    val apkInfo: ApkPackageInfo,
    val assetName: String,
    val assetUrl: String,
    val assetSize: Long,
    val releaseTag: String,
    val releaseId: Long?,
    val assetId: Long?,
    val assetDigest: String?,
    val isPendingInstall: Boolean,
    val isFavourite: Boolean,
    val siblingAssetCount: Int,
    val pickedAssetIndex: Int?,

    val pendingInstallFilePath: String? = null,

    val sourceHost: String? = null,
)
