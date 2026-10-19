package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_1_2 =
    object : Migration(1, 2) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE installed_apps ADD COLUMN installedVersionName TEXT")
            connection.execSQL("ALTER TABLE installed_apps ADD COLUMN installedVersionCode INTEGER NOT NULL DEFAULT 0")
            connection.execSQL("ALTER TABLE installed_apps ADD COLUMN latestVersionName TEXT")
            connection.execSQL("ALTER TABLE installed_apps ADD COLUMN latestVersionCode INTEGER")
        }
    }
