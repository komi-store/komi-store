package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_9_10 =
    object : Migration(9, 10) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE installed_apps ADD COLUMN assetFilterRegex TEXT")
            connection.execSQL(
                "ALTER TABLE installed_apps ADD COLUMN fallbackToOlderReleases INTEGER NOT NULL DEFAULT 0",
            )
        }
    }
