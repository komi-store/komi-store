package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_17_18 =
    object : Migration(17, 18) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                ALTER TABLE installed_apps
                ADD COLUMN sourceHost TEXT DEFAULT NULL
                """.trimIndent(),
            )
        }
    }
