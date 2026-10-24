package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// DEFAULT NULL is explicit: legacy rows keep their binding (NULL means "not proven foreign"),
// and only a store-side install or a proven rebind stamps the column.
val MIGRATION_22_23 =
    object : Migration(22, 23) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "ALTER TABLE installed_apps ADD COLUMN boundAt INTEGER DEFAULT NULL",
            )
        }
    }
