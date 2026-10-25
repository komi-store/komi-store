package zed.rainxch.core.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import zed.rainxch.core.data.local.DesktopAppDataPaths
import java.io.File

fun createAnnouncementsDataStore(): DataStore<Preferences> =
    createDataStore(
        producePath = {
            DesktopAppDataPaths.migrateFromTmpIfNeeded(announcementsDataStoreFileName)
            File(DesktopAppDataPaths.appDataDir(), announcementsDataStoreFileName).absolutePath
        },
    )
