package zed.rainxch.core.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import java.io.IOException

// DataStore reports a file it can't read or write as IOException, CorruptionException included; a migration then retries later.
internal suspend fun DataStore<Preferences>.snapshotOrNull(): Preferences? =
    try {
        data.first()
    } catch (_: IOException) {
        null
    }

internal suspend fun DataStore<Preferences>.removeKeys(keys: Collection<Preferences.Key<*>>): Boolean =
    try {
        edit { prefs -> keys.forEach { prefs.remove(it) } }
        true
    } catch (_: IOException) {
        false
    }
