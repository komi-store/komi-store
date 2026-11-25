package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// DEFAULT NULL is spelled out because Room checks each column's default against the exported schema on open.
val MIGRATION_19_20 =
    object : Migration(19, 20) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE installed_apps ADD COLUMN installedReleaseId INTEGER DEFAULT NULL",
            )
            connection.execSQL(
                "ALTER TABLE installed_apps ADD COLUMN installedAssetId INTEGER DEFAULT NULL",
            )
            connection.execSQL(
                "ALTER TABLE installed_apps ADD COLUMN installedAssetDigest TEXT DEFAULT NULL",
            )
            connection.execSQL(
                "ALTER TABLE installed_apps ADD COLUMN pendingInstallReleaseId INTEGER DEFAULT NULL",
            )
            connection.execSQL(
                "ALTER TABLE installed_apps ADD COLUMN pendingInstallAssetId INTEGER DEFAULT NULL",
            )
            connection.execSQL(
                "ALTER TABLE installed_apps ADD COLUMN pendingInstallAssetDigest TEXT DEFAULT NULL",
            )
        }
    }
