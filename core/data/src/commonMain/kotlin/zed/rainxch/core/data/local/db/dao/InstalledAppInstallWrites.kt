package zed.rainxch.core.data.local.db.dao

import androidx.room.Query
import androidx.room.Update
import zed.rainxch.core.data.local.db.entities.InstalledAppAwaitingInstall
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity

interface InstalledAppInstallWrites {
    @Query(
        """
        UPDATE installed_apps
           SET signingFingerprint = :signingFingerprint
         WHERE packageName = :packageName
        """,
    )
    suspend fun updateSigningFingerprint(
        packageName: String,
        signingFingerprint: String,
    )

    @Query(
        """
        UPDATE installed_apps
           SET installedReleaseId = NULL,
               installedAssetId = NULL,
               installedAssetDigest = NULL,
               installedReleasePublishedAt = NULL
         WHERE packageName = :packageName
        """,
    )
    suspend fun clearInstallBinding(packageName: String)

    @Query(
        """
        UPDATE installed_apps
           SET installedAssetDigest = :digest
         WHERE packageName = :packageName
        """,
    )
    suspend fun setInstalledAssetDigest(
        packageName: String,
        digest: String,
    )

    @Query(
        """
        UPDATE installed_apps
           SET installedReleasePublishedAt = :publishedAt
         WHERE packageName = :packageName
        """,
    )
    suspend fun setInstalledReleasePublishedAt(
        packageName: String,
        publishedAt: String?,
    )

    // Must not touch the pending identity: callers clear the path before the system confirms the install.
    @Query(
        """
        UPDATE installed_apps
           SET pendingInstallFilePath = :path,
               pendingInstallVersion = :version,
               pendingInstallAssetName = :assetName,
               isPendingInstall = CASE WHEN :path IS NULL THEN 0 ELSE 1 END
         WHERE packageName = :packageName
        """,
    )
    suspend fun updatePendingInstallFilePath(
        packageName: String,
        path: String?,
        version: String?,
        assetName: String?,
    )

    @Query(
        """
        UPDATE installed_apps
           SET pendingInstallFilePath = NULL,
               pendingInstallVersion = NULL,
               pendingInstallAssetName = NULL,
               isPendingInstall = 0
         WHERE packageName = :packageName
           AND pendingInstallFilePath IS :path
        """,
    )
    suspend fun clearPendingInstallIfPathMatches(
        packageName: String,
        path: String?,
    )

    @Update(entity = InstalledAppEntity::class)
    suspend fun markAwaitingInstall(awaiting: InstalledAppAwaitingInstall)
}
