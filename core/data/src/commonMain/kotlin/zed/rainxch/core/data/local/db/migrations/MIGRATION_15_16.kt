package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_15_16 =
    object : Migration(15, 16) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                ALTER TABLE installed_apps
                ADD COLUMN skippedReleaseTag TEXT DEFAULT NULL
                """.trimIndent(),
            )
        }
    }
