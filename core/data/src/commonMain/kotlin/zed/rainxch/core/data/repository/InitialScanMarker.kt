package zed.rainxch.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import eu.anifantakis.lib.ksafe.KSafe
import zed.rainxch.core.data.local.datastore.removeKeys
import zed.rainxch.core.data.local.datastore.snapshotOrNull
import zed.rainxch.core.data.secure.safeGet
import zed.rainxch.core.data.secure.safePut
import kotlin.time.Clock

// When the first external-app scan finished; older installs kept it in the legacy DataStore.
internal class InitialScanMarker(
    private val ksafe: KSafe,
    private val legacyDataStore: DataStore<Preferences>,
) {
    suspend fun isFirstLaunch(): Boolean {
        migrateLegacyFlag()
        return ksafe.safeGet<Long?>(K_INITIAL_SCAN_AT, null) == null
    }

    suspend fun markComplete() {
        ksafe.safePut(K_INITIAL_SCAN_AT, Clock.System.now().toEpochMilliseconds())
    }

    private suspend fun migrateLegacyFlag() {
        if (ksafe.safeGet<Long?>(K_INITIAL_SCAN_AT, null) != null) return
        val legacyValue = legacyDataStore.snapshotOrNull()?.get(LEGACY_KEY) ?: return
        if (ksafe.safePut(K_INITIAL_SCAN_AT, legacyValue)) legacyDataStore.removeKeys(listOf(LEGACY_KEY))
    }

    private companion object {
        const val K_INITIAL_SCAN_AT = "external_import_initial_scan_at"
        val LEGACY_KEY = longPreferencesKey("external_import_initial_scan_at")
    }
}
