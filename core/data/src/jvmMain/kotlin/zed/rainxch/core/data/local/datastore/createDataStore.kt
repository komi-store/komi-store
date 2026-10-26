package zed.rainxch.core.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import zed.rainxch.core.data.local.DesktopAppDataPaths
import java.io.File

fun createDataStore(): DataStore<Preferences> =
    createDataStore(
        producePath = {
            DesktopAppDataPaths.migrateFromTmpIfNeeded(DATA_STORE_FILE_NAME)
            File(DesktopAppDataPaths.appDataDir(), DATA_STORE_FILE_NAME).absolutePath
        },
    )
