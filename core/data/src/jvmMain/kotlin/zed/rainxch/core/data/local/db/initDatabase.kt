package zed.rainxch.core.data.local.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import zed.rainxch.core.data.local.DesktopAppDataPaths
import zed.rainxch.core.data.local.db.migrations.MIGRATIONS
import java.io.File

fun initDatabase(): AppDatabase {
    DesktopAppDataPaths.migrateFromTmpIfNeeded("github_store.db-wal")
    DesktopAppDataPaths.migrateFromTmpIfNeeded("github_store.db-shm")
    DesktopAppDataPaths.migrateFromTmpIfNeeded("github_store.db")

    return buildDatabase(File(DesktopAppDataPaths.appDataDir(), "github_store.db"))
}

@Suppress("SpreadOperator")
internal fun buildDatabase(dbFile: File): AppDatabase =
    Room
        .databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
        ).setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(*MIGRATIONS)
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        .build()
