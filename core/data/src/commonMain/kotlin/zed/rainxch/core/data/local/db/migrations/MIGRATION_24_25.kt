package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// Existing rows start at zero. Only update checks advance the revision, and they match on the
// value they read, so an older check cannot overwrite a newer result.
val MIGRATION_24_25 =
    object : Migration(24, 25) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE installed_apps ADD COLUMN updateStateRevision INTEGER NOT NULL DEFAULT 0",
            )
        }
    }
