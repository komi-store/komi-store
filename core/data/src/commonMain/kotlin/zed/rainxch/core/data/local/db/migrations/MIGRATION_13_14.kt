package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_13_14 =
    object : Migration(13, 14) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE installed_apps ADD COLUMN pendingInstallVersion TEXT")
            connection.execSQL("ALTER TABLE installed_apps ADD COLUMN pendingInstallAssetName TEXT")
        }
    }
