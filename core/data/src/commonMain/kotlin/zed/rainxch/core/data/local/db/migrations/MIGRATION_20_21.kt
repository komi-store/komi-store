package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// DEFAULT NULL has to be spelled out. Room compares each column's default against the
// exported schema on open, so omitting it fails validation on both platforms.
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
