package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// Detection stays on for rows that existed before the per-app switch, which is the behaviour
// the previous version shipped.
val MIGRATION_24_25 =
    object : Migration(24, 25) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE installed_apps ADD COLUMN detectAssetRebuildsInRelease INTEGER NOT NULL DEFAULT 1",
            )
        }
    }
