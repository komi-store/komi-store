package zed.rainxch.apps.presentation.mappers

import zed.rainxch.apps.presentation.model.LibraryDownloadUi
import zed.rainxch.core.domain.system.OrchestratedDownload

fun OrchestratedDownload.toUi(): LibraryDownloadUi =
    LibraryDownloadUi(
        id = id,
        packageName = packageName,
        displayAppName = displayAppName,
        repoOwner = repoOwner,
        repoName = repoName,
        repoId = repoId,
        sourceHost = sourceHost,
        repoOwnerAvatarUrl = repoOwnerAvatarUrl,
        filePath = filePath,
        stage = stage,
        releaseTag = releaseTag,
        assetSize = assetSize,
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytes,
        progressPercent = progressPercent,
        errorMessage = errorMessage,
    )
