package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_6_7 =
    object : Migration(6, 7) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                CREATE TABLE IF NOT EXISTS search_history (
                    query TEXT NOT NULL PRIMARY KEY,
                    searchedAt INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }
