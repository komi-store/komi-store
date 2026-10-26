package zed.rainxch.core.data.local.db.dao

import androidx.room.Query
import androidx.room.Update
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.data.local.db.entities.InstalledAppVariantPin

interface InstalledAppTrackingWrites {
    @Query("UPDATE installed_apps SET includePreReleases = :enabled WHERE packageName = :packageName")
    suspend fun updateIncludePreReleases(
        packageName: String,
        enabled: Boolean,
    )

    @Query("UPDATE installed_apps SET updateCheckEnabled = :enabled WHERE packageName = :packageName")
    suspend fun updateUpdateCheckEnabled(
        packageName: String,
        enabled: Boolean,
    )

    @Query(
        """
        UPDATE installed_apps
           SET assetFilterRegex = :regex,
               fallbackToOlderReleases = :fallback
         WHERE packageName = :packageName
        """,
    )
    suspend fun updateAssetFilter(
        packageName: String,
        regex: String?,
        fallback: Boolean,
    )

    @Update(entity = InstalledAppEntity::class)
    suspend fun updatePreferredVariant(pin: InstalledAppVariantPin)

    @Query(
        """
        UPDATE installed_apps
           SET preferredVariantStale = :stale
         WHERE packageName = :packageName
        """,
    )
    suspend fun updateVariantStaleness(
        packageName: String,
        stale: Boolean,
    )
}
