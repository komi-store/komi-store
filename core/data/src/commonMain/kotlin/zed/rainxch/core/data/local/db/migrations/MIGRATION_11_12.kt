package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

val MIGRATION_11_12 =
    object : Migration(11, 12) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE installed_apps ADD COLUMN preferredAssetTokens TEXT")
            connection.execSQL("ALTER TABLE installed_apps ADD COLUMN assetGlobPattern TEXT")
            connection.execSQL("ALTER TABLE installed_apps ADD COLUMN pickedAssetIndex INTEGER")
            connection.execSQL("ALTER TABLE installed_apps ADD COLUMN pickedAssetSiblingCount INTEGER")
        }
    }
