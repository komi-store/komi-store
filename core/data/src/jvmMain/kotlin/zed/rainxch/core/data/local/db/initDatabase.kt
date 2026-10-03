package zed.rainxch.core.data.local.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import zed.rainxch.core.data.local.DesktopAppDataPaths
import zed.rainxch.core.data.local.db.migrations.MIGRATION_19_20
import java.io.File

fun initDatabase(): AppDatabase {

    DesktopAppDataPaths.migrateFromTmpIfNeeded("github_store.db-wal")
    DesktopAppDataPaths.migrateFromTmpIfNeeded("github_store.db-shm")
    DesktopAppDataPaths.migrateFromTmpIfNeeded("github_store.db")

    val dbFile = File(DesktopAppDataPaths.appDataDir(), "github_store.db")
    return Room
        .databaseBuilder<AppDatabase>(
            name = dbFile.absolutePath,
        ).setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .fallbackToDestructiveMigration(true)
        .addMigrations(MIGRATION_19_20)
        .build()
}
