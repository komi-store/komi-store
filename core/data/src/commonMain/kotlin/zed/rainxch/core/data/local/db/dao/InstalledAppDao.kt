package zed.rainxch.core.data.local.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity

@Dao
interface InstalledAppDao :
    InstalledAppReads,
    InstalledAppUpdateWrites,
    InstalledAppTrackingWrites,
    InstalledAppInstallWrites {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: InstalledAppEntity)

    @Update
    suspend fun updateApp(app: InstalledAppEntity)

    @Delete
    suspend fun deleteApp(app: InstalledAppEntity)

    @Query("DELETE FROM installed_apps WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)
}
