package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// DEFAULT NULL has to be spelled out. Room compares each column's default against the
// exported schema on open, so omitting it fails validation on both platforms.
val MIGRATION_21_22 =
    object : Migration(21, 22) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE installed_apps ADD COLUMN pendingInstallStartedAt INTEGER DEFAULT NULL",
            )
        }
    }
