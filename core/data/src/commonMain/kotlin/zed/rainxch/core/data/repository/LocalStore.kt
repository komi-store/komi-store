package zed.rainxch.core.data.repository

import androidx.sqlite.SQLiteException
import zed.rainxch.core.domain.model.error.LocalStoreException

// Room raises SQLite failures on both drivers as androidx.sqlite.SQLiteException (android.database.SQLException on Android).
internal inline fun <T> localStore(block: () -> T): T =
    try {
        block()
    } catch (e: SQLiteException) {
        throw LocalStoreException(e)
    }
