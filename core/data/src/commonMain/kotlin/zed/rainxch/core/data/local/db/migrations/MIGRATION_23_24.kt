package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// The parked build's code is the cheap proof that the right build landed. NULL means
// unknown (legacy/park-only), never "0".
val MIGRATION_23_24 =
    object : Migration(23, 24) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE installed_apps ADD COLUMN pendingInstallVersionCode INTEGER DEFAULT NULL",
            )
        }
    }
