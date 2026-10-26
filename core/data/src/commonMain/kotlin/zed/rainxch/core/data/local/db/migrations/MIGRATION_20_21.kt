package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// DEFAULT NULL is spelled out because Room checks each column's default against the exported schema on open.
val MIGRATION_20_21 =
    object : Migration(20, 21) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE installed_apps ADD COLUMN installedReleasePublishedAt TEXT DEFAULT NULL",
            )
            connection.execSQL(
                "ALTER TABLE installed_apps ADD COLUMN pendingInstallReleasePublishedAt TEXT DEFAULT NULL",
            )
        }
    }
