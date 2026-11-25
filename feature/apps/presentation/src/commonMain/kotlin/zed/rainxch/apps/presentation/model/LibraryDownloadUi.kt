package zed.rainxch.apps.presentation.model

import zed.rainxch.core.domain.system.DownloadStage

data class LibraryDownloadUi(
    val id: String,
    val packageName: String,
    val displayAppName: String,
    val repoOwner: String,
    val repoName: String,
    val repoId: Long?,
    val sourceHost: String?,
    val repoOwnerAvatarUrl: String?,
    val filePath: String?,
    val stage: DownloadStage,
    val releaseTag: String,
    val assetSize: Long,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val progressPercent: Int?,
    val errorMessage: String?,
)
