package zed.rainxch.core.data.local.db.dao

import androidx.room.Query
import androidx.room.Update
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.data.local.db.entities.InstalledAppVersionInfo

interface InstalledAppUpdateWrites {
    @Update(entity = InstalledAppEntity::class)
    suspend fun updateVersionInfo(info: InstalledAppVersionInfo)

    @Query("UPDATE installed_apps SET lastCheckedAt = :timestamp WHERE packageName = :packageName")
    suspend fun updateLastChecked(
        packageName: String,
        timestamp: Long,
    )

    @Query(
        """
        UPDATE installed_apps
           SET isUpdateAvailable = 0,
               lastCheckedAt = :timestamp
         WHERE packageName = :packageName
        """,
    )
    suspend fun clearUpdateFlagKeepBaseline(
        packageName: String,
        timestamp: Long,
    )

    @Query(
        """
        UPDATE installed_apps
           SET skippedReleaseTag = :tag,
               isUpdateAvailable = CASE WHEN :tag IS NULL THEN isUpdateAvailable ELSE 0 END
         WHERE packageName = :packageName
        """,
    )
    suspend fun setSkippedReleaseTag(
        packageName: String,
        tag: String?,
    )

    @Query(
        """
        UPDATE installed_apps
           SET installedVersion = :installedVersion,
               installedVersionName = :installedVersionName,
               installedVersionCode = :installedVersionCode,
               isUpdateAvailable = :isUpdateAvailable
         WHERE packageName = :packageName
        """,
    )
    suspend fun updateInstalledVersion(
        packageName: String,
        installedVersion: String,
        installedVersionName: String?,
        installedVersionCode: Long,
        isUpdateAvailable: Boolean,
    )

    @Query(
        """
        UPDATE installed_apps
           SET isUpdateAvailable = 0,
               latestVersion = NULL,
               latestAssetName = NULL,
               latestAssetUrl = NULL,
               latestAssetSize = NULL,
               latestReleaseId = NULL,
               latestAssetId = NULL,
               latestAssetDigest = NULL,
               latestVersionName = NULL,
               latestVersionCode = NULL,
               latestReleasePublishedAt = NULL,
               releaseNotes = NULL,
               lastCheckedAt = :timestamp
         WHERE packageName = :packageName
        """,
    )
    suspend fun clearUpdateMetadata(
        packageName: String,
        timestamp: Long,
    )
}
