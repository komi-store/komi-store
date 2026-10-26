package zed.rainxch.core.domain.repository.installedapps

import zed.rainxch.core.domain.model.installation.ParkedInstallDisposal

interface PendingInstallStore {
    suspend fun updatePendingStatus(
        packageName: String,
        isPending: Boolean,
    )

    suspend fun setPendingInstallFilePath(
        packageName: String,
        path: String?,
        version: String? = null,
        assetName: String? = null,
    )

    suspend fun markAwaitingInstall(
        packageName: String,
        path: String,
        version: String?,
        assetName: String?,
        releaseId: Long? = null,
        assetId: Long? = null,
        releasePublishedAt: String? = null,
        assetDigest: String? = null,
    )

    suspend fun discardParkedInstall(
        packageName: String,
        expectedPath: String?,
    ): ParkedInstallDisposal
}
