package zed.rainxch.core.data.repository

import androidx.sqlite.SQLiteException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import zed.rainxch.core.domain.model.error.LocalStoreException

// Room raises SQLite failures on both drivers as androidx.sqlite.SQLiteException (android.database.SQLException on Android).
inline fun <T> localStore(block: () -> T): T =
    try {
        block()
    } catch (e: SQLiteException) {
        throw LocalStoreException(e)
    }

fun <T> Flow<T>.localStoreFailures(): Flow<T> = catch { e -> throw if (e is SQLiteException) LocalStoreException(e) else e }
