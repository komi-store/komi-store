package zed.rainxch.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import co.touchlab.kermit.Logger
import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import zed.rainxch.core.data.local.datastore.removeKeys
import zed.rainxch.core.data.local.datastore.snapshotOrNull
import zed.rainxch.core.data.secure.safeDelete
import zed.rainxch.core.data.secure.safeGet
import zed.rainxch.core.data.secure.safeGetFlow
import zed.rainxch.core.data.secure.safePut
import zed.rainxch.core.domain.repository.AnnouncementsCacheStore

class AnnouncementsCacheStoreImpl(
    private val ksafe: KSafe,
    private val legacyDataStore: DataStore<Preferences>,
) : AnnouncementsCacheStore {
    private val logger = Logger.withTag("AnnouncementsCacheStore")
    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val migrationDeferred = CompletableDeferred<Unit>()

    @Volatile private var migrated: Boolean = false

    init {
        migrationScope.launch {
            try {
                migrateIfNeeded()
            } finally {
                migrationDeferred.complete(Unit)
            }
        }
    }

    override fun getCachedPayload(): Flow<String?> =
        flow {
            migrationDeferred.await()
            emitAll(
                ksafe.safeGetFlow<String?>(K_CACHED_PAYLOAD, null).map { it?.takeIf { v -> v.isNotEmpty() } },
            )
        }

    override suspend fun setCachedPayload(payload: String?) {
        migrationDeferred.await()
        if (payload == null) {
            ksafe.safeDelete(K_CACHED_PAYLOAD)
        } else {
            ksafe.safePut(K_CACHED_PAYLOAD, payload)
        }
    }

    private suspend fun migrateIfNeeded() {
        if (migrated) return
        if (ksafe.safeGet<String?>(K_CACHED_PAYLOAD, null) == null) {
            val legacy = legacyDataStore.snapshotOrNull()?.get(LEGACY_PAYLOAD_KEY)
            if (!legacy.isNullOrEmpty()) {
                if (!ksafe.safePut(K_CACHED_PAYLOAD, legacy)) return
                if (!legacyDataStore.removeKeys(listOf(LEGACY_PAYLOAD_KEY))) logger.w("legacy clear failed")
            }
        }
        migrated = true
    }

    private companion object {
        const val K_CACHED_PAYLOAD = "announcements_cached_payload"
        val LEGACY_PAYLOAD_KEY = stringPreferencesKey("announcements_cached_payload")
    }
}
