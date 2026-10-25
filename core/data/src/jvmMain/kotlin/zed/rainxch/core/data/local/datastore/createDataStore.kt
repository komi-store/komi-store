package zed.rainxch.core.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import zed.rainxch.core.data.local.DesktopAppDataPaths
import java.io.File

fun createDataStore(): DataStore<Preferences> =
    createDataStore(
        producePath = {
            DesktopAppDataPaths.migrateFromTmpIfNeeded(dataStoreFileName)
            File(DesktopAppDataPaths.appDataDir(), dataStoreFileName).absolutePath
        },
    )
