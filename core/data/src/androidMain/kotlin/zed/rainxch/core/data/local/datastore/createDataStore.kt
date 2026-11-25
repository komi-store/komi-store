package zed.rainxch.core.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences

fun createDataStore(context: Context): DataStore<Preferences> =
    createDataStore(
        producePath = {
            context.filesDir.resolve(DATA_STORE_FILE_NAME).absolutePath
        },
    )
