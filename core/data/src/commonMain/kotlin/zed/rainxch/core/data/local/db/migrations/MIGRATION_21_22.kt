package zed.rainxch.core.data.local.db.migrations

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

// What the library rows show beyond a name: stars, platforms, the newest version and the host,
// saved when a repo is viewed, favourited or synced from the user's stars. Older rows keep NULL
// until that happens again.
val MIGRATION_21_22 =
    object : Migration(21, 22) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL("ALTER TABLE seen_repos ADD COLUMN stargazersCount INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE seen_repos ADD COLUMN platforms TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE seen_repos ADD COLUMN latestVersion TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE seen_repos ADD COLUMN sourceHost TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE favorite_repos ADD COLUMN stargazersCount INTEGER DEFAULT NULL")
            connection.execSQL("ALTER TABLE favorite_repos ADD COLUMN platforms TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE favorite_repos ADD COLUMN sourceHost TEXT DEFAULT NULL")
            connection.execSQL("ALTER TABLE starred_repos ADD COLUMN platforms TEXT DEFAULT NULL")
        }
    }
