package zed.rainxch.core.data.mirror

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Instant
import zed.rainxch.core.data.dto.MirrorEntry
import zed.rainxch.core.data.dto.MirrorListResponse
import zed.rainxch.core.data.network.MirrorApiClient
import zed.rainxch.core.data.secure.MigrationEntry
import zed.rainxch.core.data.secure.migrateDataStoreToKSafe
import zed.rainxch.core.domain.model.mirror.MirrorConfig
import zed.rainxch.core.domain.model.mirror.MirrorPreference
import zed.rainxch.core.domain.model.mirror.MirrorStatus
import zed.rainxch.core.domain.model.mirror.MirrorType
import zed.rainxch.core.domain.model.mirror.TrafficKind
import zed.rainxch.core.domain.network.MirrorLatencyProbe
import zed.rainxch.core.domain.repository.MirrorRepository
import zed.rainxch.core.data.secure.safeDelete
import zed.rainxch.core.data.secure.safeGet
import zed.rainxch.core.data.secure.safeGetFlow
import zed.rainxch.core.data.secure.safePut
import zed.rainxch.core.domain.model.mirror.MirrorRemoved

class MirrorRepositoryImpl(
    private val ksafe: KSafe,
    private val legacyDataStore: DataStore<Preferences>,
    private val apiClient: MirrorApiClient,
    private val latencyProbe: MirrorLatencyProbe,
    appScope: CoroutineScope,
) : MirrorRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val cacheTtlMs = 60L * 60 * 1000

    private val _catalog = MutableStateFlow<List<MirrorConfig>>(emptyList())
    private val _measuredLatencies = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _removedNotices = MutableSharedFlow<MirrorRemoved>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val migrationDeferred = CompletableDeferred<Unit>()

    init {
        appScope.launch {
            runCatching {
                migrateDataStoreToKSafe(
                    legacy = legacyDataStore,
                    ksafe = ksafe,
                    markerKey = MIGRATION_MARKER,
                    entries = listOf(
                        MigrationEntry(
                            legacyKey = stringPreferencesKey("mirror_preferred_id"),
                            ksafeKey = K_PREFERRED
                        ),
                        MigrationEntry(
                            legacyKey = stringPreferencesKey("mirror_custom_template"),
                            ksafeKey = K_CUSTOM_TEMPLATE
                        ),
                        MigrationEntry(
                            legacyKey = stringPreferencesKey("mirror_cached_list_json"),
                            ksafeKey = K_CACHED_JSON
                        ),
                        MigrationEntry(
                            legacyKey = longPreferencesKey("mirror_cached_list_at"),
                            ksafeKey = K_CACHED_AT
                        ),
                        MigrationEntry(
                            legacyKey = longPreferencesKey("mirror_auto_suggest_snooze_until"),
                            ksafeKey = K_SUGGEST_SNOOZE
                        ),
                        MigrationEntry(
                            legacyKey = booleanPreferencesKey("mirror_auto_suggest_dismissed"),
                            ksafeKey = K_SUGGEST_DISMISSED
                        ),
                    ),
                )
            }
            migrationDeferred.complete(Unit)
            _catalog.value = readCachedCatalogOrBundled()
            val cachedAt = runCatching { ksafe.safeGet(K_CACHED_AT, 0L) }.getOrDefault(0L)
            if (Clock.System.now().toEpochMilliseconds() - cachedAt > cacheTtlMs) {
                refreshCatalog()
            }
        }
    }

    override fun observeCatalog(): Flow<List<MirrorConfig>> = _catalog.asStateFlow()

    override suspend fun refreshCatalog(): Result<Unit> =
        apiClient
            .fetchList()
            .onSuccess { response ->
                val configs = response.mirrors.map { it.toDomain() }
                val previousCatalog = _catalog.value
                _catalog.value = configs
                ksafe.safePut(
                    K_CACHED_JSON,
                    json.encodeToString(MirrorListResponse.serializer(), response)
                )
                ksafe.safePut(K_CACHED_AT, Clock.System.now().toEpochMilliseconds())
                checkSelectedMirrorStillExists(fresh = configs, previous = previousCatalog)
            }.map { }

    override fun observePreference(): Flow<MirrorPreference> = flow {
        migrationDeferred.await()
        emitAll(
            combine(
                ksafe.safeGetFlow(K_PREFERRED, DIRECT_MIRROR_ID),
                ksafe.safeGetFlow(K_CUSTOM_TEMPLATE, ""),
            ) { id, template ->
                when (id) {
                    DIRECT_MIRROR_ID -> MirrorPreference.Direct
                    CUSTOM_MIRROR_ID_SENTINEL ->
                        if (template.isBlank()) MirrorPreference.Direct else MirrorPreference.Custom(
                            template
                        )

                    else -> MirrorPreference.Selected(id)
                }
            },
        )
    }

    override suspend fun setPreference(pref: MirrorPreference) {
        migrationDeferred.await()
        when (pref) {
            MirrorPreference.Direct -> {
                ksafe.safePut(K_PREFERRED, DIRECT_MIRROR_ID)
                ksafe.safeDelete(K_CUSTOM_TEMPLATE)
            }

            is MirrorPreference.Selected -> {
                ksafe.safePut(K_PREFERRED, pref.id)
                ksafe.safeDelete(K_CUSTOM_TEMPLATE)
            }

            is MirrorPreference.Custom -> {
                ksafe.safePut(K_PREFERRED, CUSTOM_MIRROR_ID_SENTINEL)
                ksafe.safePut(K_CUSTOM_TEMPLATE, pref.template)
            }
        }
    }

    override fun observeMeasuredLatencies(): Flow<Map<String, Int>> = _measuredLatencies.asStateFlow()

    override suspend fun measureLatencies(): Result<Map<String, Int>> =
        runCatching {
            val measured = latencyProbe.probe(_catalog.value)
            _measuredLatencies.value = measured
            measured
        }

    override fun observeRemovedNotices(): Flow<MirrorRemoved> = _removedNotices.asSharedFlow()

    override suspend fun snoozeAutoSuggest(forMs: Long) {
        migrationDeferred.await()
        ksafe.safePut(K_SUGGEST_SNOOZE, Clock.System.now().toEpochMilliseconds() + forMs)
    }

    override suspend fun dismissAutoSuggestPermanently() {
        migrationDeferred.await()
        ksafe.safePut(K_SUGGEST_DISMISSED, true)
    }

    private suspend fun readCachedCatalogOrBundled(): List<MirrorConfig> {
        val cachedJson = runCatching {
            ksafe.safeGet(key = K_CACHED_JSON, defaultValue = "")
        }.getOrDefault("")

        return if (cachedJson.isBlank()) {
            BundledMirrors.ALL.sortedBy { it.status.ordinal }
        } else {
            runCatching {
                json.decodeFromString(
                    deserializer = MirrorListResponse.serializer(),
                    string = cachedJson
                ).mirrors
                    .map { it.toDomain() }
                    .sortedBy { it.status.ordinal }
            }.getOrElse { BundledMirrors.ALL.sortedBy { it.status.ordinal } }
        }
    }

    private suspend fun checkSelectedMirrorStillExists(
        fresh: List<MirrorConfig>,
        previous: List<MirrorConfig>,
    ) {
        val pref = observePreference().first()
        if (pref !is MirrorPreference.Selected) return
        val match = fresh.firstOrNull { it.id == pref.id }
        if (match == null) {
            val previousName = previous.firstOrNull { it.id == pref.id }?.name ?: pref.id
            setPreference(MirrorPreference.Direct)
            _removedNotices.tryEmit(MirrorRemoved(displayName = previousName))
        }
    }

    private fun MirrorEntry.toDomain(): MirrorConfig =
        MirrorConfig(
            id = id,
            name = name,
            urlTemplate = urlTemplate,
            type = when (type) {
                "official" -> MirrorType.OFFICIAL
                else -> MirrorType.COMMUNITY
            },
            status = when (status) {
                "ok" -> MirrorStatus.OK
                "degraded" -> MirrorStatus.DEGRADED
                "down" -> MirrorStatus.DOWN
                else -> MirrorStatus.UNKNOWN
            },
            latencyMs = latencyMs,
            lastCheckedAt = lastCheckedAt?.let { runCatching { Instant.parse(it) }.getOrNull() },
            trafficKinds = trafficKinds
                ?.mapNotNull { TrafficKind.fromWire(it) }
                ?.toSet()
                ?.ifEmpty { null }
                ?: setOf(TrafficKind.RELEASE_ASSET, TrafficKind.RAW_FILE),
        )

    private companion object {
        const val MIGRATION_MARKER = "__migrated_mirror_v1__"
        const val K_PREFERRED = "mirror_preferred_id"
        const val K_CUSTOM_TEMPLATE = "mirror_custom_template"
        const val K_CACHED_JSON = "mirror_cached_list_json"
        const val K_CACHED_AT = "mirror_cached_list_at"
        const val K_SUGGEST_SNOOZE = "mirror_auto_suggest_snooze_until"
        const val K_SUGGEST_DISMISSED = "mirror_auto_suggest_dismissed"
        const val DIRECT_MIRROR_ID = "direct"
        const val CUSTOM_MIRROR_ID_SENTINEL = "__custom__"
    }
}
