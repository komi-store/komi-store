package zed.rainxch.core.data.local.db

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import zed.rainxch.core.data.local.db.migrations.MIGRATIONS

@Suppress("SpreadOperator")
fun initDatabase(context: Context): AppDatabase {
    val appContext = context.applicationContext
    val dbFile = appContext.getDatabasePath("github_store.db")
    return Room
        .databaseBuilder<AppDatabase>(
            context = appContext,
            name = dbFile.absolutePath,
        ).setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(*MIGRATIONS)
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        .build()
}
