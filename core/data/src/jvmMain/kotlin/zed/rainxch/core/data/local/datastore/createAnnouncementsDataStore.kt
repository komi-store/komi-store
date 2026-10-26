package zed.rainxch.core.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import zed.rainxch.core.data.local.DesktopAppDataPaths
import java.io.File

fun createAnnouncementsDataStore(): DataStore<Preferences> =
    createDataStore(
        producePath = {
            DesktopAppDataPaths.migrateFromTmpIfNeeded(ANNOUNCEMENTS_DATA_STORE_FILE_NAME)
            File(DesktopAppDataPaths.appDataDir(), ANNOUNCEMENTS_DATA_STORE_FILE_NAME).absolutePath
        },
    )
