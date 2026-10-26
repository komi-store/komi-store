package zed.rainxch.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import zed.rainxch.core.data.local.datastore.removeKeys
import zed.rainxch.core.data.local.datastore.snapshotOrNull
import zed.rainxch.core.data.network.ProxyManager
import zed.rainxch.core.data.secure.safeDelete
import zed.rainxch.core.data.secure.safeGet
import zed.rainxch.core.data.secure.safeGetFlow
import zed.rainxch.core.data.secure.safePut
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.settings.ProxyConfig
import zed.rainxch.core.domain.model.settings.ProxyScope
import zed.rainxch.core.domain.repository.ProxyRepository

class ProxyRepositoryImpl(
    private val ksafe: KSafe,
    private val legacyDataStore: DataStore<Preferences>,
    private val logger: KomiStoreLogger,
) : ProxyRepository {
    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val migrationLock = Mutex()
    private val migrationDeferred = CompletableDeferred<Unit>()

    @Volatile private var migrated: Boolean = false

    init {
        migrationScope.launch {
            try {
                migrateIfNeeded()
                migrateMasterV2IfNeeded()
            } finally {
                migrationDeferred.complete(Unit)
            }
        }
    }

    private data class ScopeKeys(
        val type: String,
        val host: String,
        val port: String,
        val username: String,
        val password: String,
    )

    private fun keysFor(scope: ProxyScope): ScopeKeys {
        val prefix =
            when (scope) {
                ProxyScope.DISCOVERY -> "discovery"
                ProxyScope.DOWNLOAD -> "download"
                ProxyScope.TRANSLATION -> "translation"
            }
        return ScopeKeys(
            type = "${prefix}_proxy_type",
            host = "${prefix}_proxy_host",
            port = "${prefix}_proxy_port",
            username = "${prefix}_proxy_username",
            password = "${prefix}_proxy_password",
        )
    }

    private object MasterKeys {
        const val TYPE = "master_proxy_type"
        const val HOST = "master_proxy_host"
        const val PORT = "master_proxy_port"
        const val USERNAME = "master_proxy_username"
        const val PASSWORD = "master_proxy_password"
    }

    private fun useMasterKeyFor(scope: ProxyScope): String =
        when (scope) {
            ProxyScope.DISCOVERY -> "discovery_proxy_use_master"
            ProxyScope.DOWNLOAD -> "download_proxy_use_master"
            ProxyScope.TRANSLATION -> "translation_proxy_use_master"
        }

    override fun getProxyConfig(scope: ProxyScope): Flow<ProxyConfig> =
        flow {
            migrationDeferred.await()
            val keys = keysFor(scope)
            emitAll(
                combine(
                    ksafe.safeGetFlow<String?>(keys.type, null),
                    ksafe.safeGetFlow<String?>(keys.host, null),
                    ksafe.safeGetFlow<Int?>(keys.port, null),
                    ksafe.safeGetFlow<String?>(keys.username, null),
                    ksafe.safeGetFlow<String?>(keys.password, null),
                ) { type, host, port, user, pass ->
                    parseConfig(type, host, port, user, pass)
                },
            )
        }

    private fun parseConfig(
        type: String?,
        host: String?,
        port: Int?,
        username: String?,
        password: String?,
    ): ProxyConfig =
        when (type) {
            "system" -> {
                ProxyConfig.System
            }

            "none" -> {
                ProxyConfig.None
            }

            "http" -> {
                val validHost = host?.takeIf { it.isNotBlank() }
                val validPort = port?.takeIf { it in 1..MAX_PORT }
                if (validHost != null && validPort != null) {
                    ProxyConfig.Http(validHost, validPort, username, password)
                } else {
                    logger.warn("Malformed HTTP proxy (host=$host port=$port); fallback System")
                    ProxyConfig.System
                }
            }

            "socks" -> {
                val validHost = host?.takeIf { it.isNotBlank() }
                val validPort = port?.takeIf { it in 1..MAX_PORT }
                if (validHost != null && validPort != null) {
                    ProxyConfig.Socks(validHost, validPort, username, password)
                } else {
                    logger.warn("Malformed SOCKS proxy (host=$host port=$port); fallback System")
                    ProxyConfig.System
                }
            }

            else -> {
                ProxyConfig.System
            }
        }

    override suspend fun setProxyConfig(
        scope: ProxyScope,
        config: ProxyConfig,
    ) {
        migrationDeferred.await()
        // KSafe writes block, and the save handler runs on the main dispatcher.
        withContext(Dispatchers.IO) {
            val keys = keysFor(scope)
            when (config) {
                is ProxyConfig.None -> {
                    ksafe.safePut(keys.type, "none")
                    ksafe.safeDelete(keys.host)
                    ksafe.safeDelete(keys.port)
                    ksafe.safeDelete(keys.username)
                    ksafe.safeDelete(keys.password)
                }

                is ProxyConfig.System -> {
                    ksafe.safePut(keys.type, "system")
                    ksafe.safeDelete(keys.host)
                    ksafe.safeDelete(keys.port)
                    ksafe.safeDelete(keys.username)
                    ksafe.safeDelete(keys.password)
                }

                is ProxyConfig.Http -> {
                    ksafe.safePut(keys.type, "http")
                    ksafe.safePut(keys.host, config.host)
                    ksafe.safePut(keys.port, config.port)
                    writeOrClear(keys.username, config.username)
                    writeOrClear(keys.password, config.password)
                }

                is ProxyConfig.Socks -> {
                    ksafe.safePut(keys.type, "socks")
                    ksafe.safePut(keys.host, config.host)
                    ksafe.safePut(keys.port, config.port)
                    writeOrClear(keys.username, config.username)
                    writeOrClear(keys.password, config.password)
                }
            }
            ProxyManager.setConfig(scope, config)
        }
    }

    private suspend fun writeOrClear(
        key: String,
        value: String?,
    ) {
        if (value != null) ksafe.safePut(key, value) else ksafe.safeDelete(key)
    }

    override fun getMasterProxyConfig(): Flow<ProxyConfig?> =
        flow {
            migrationDeferred.await()
            emitAll(
                combine(
                    ksafe.safeGetFlow<String?>(MasterKeys.TYPE, null),
                    ksafe.safeGetFlow<String?>(MasterKeys.HOST, null),
                    ksafe.safeGetFlow<Int?>(MasterKeys.PORT, null),
                    ksafe.safeGetFlow<String?>(MasterKeys.USERNAME, null),
                    ksafe.safeGetFlow<String?>(MasterKeys.PASSWORD, null),
                ) { type, host, port, user, pass ->
                    if (type == null) null else parseConfig(type, host, port, user, pass)
                },
            )
        }

    override suspend fun setMasterProxyConfig(config: ProxyConfig) {
        migrationDeferred.await()
        writeMasterConfig(config)
    }

    private suspend fun writeMasterConfig(config: ProxyConfig) =
        withContext(Dispatchers.IO) {
            when (config) {
                is ProxyConfig.None -> {
                    ksafe.safePut(MasterKeys.TYPE, "none")
                    ksafe.safeDelete(MasterKeys.HOST)
                    ksafe.safeDelete(MasterKeys.PORT)
                    ksafe.safeDelete(MasterKeys.USERNAME)
                    ksafe.safeDelete(MasterKeys.PASSWORD)
                }

                is ProxyConfig.System -> {
                    ksafe.safePut(MasterKeys.TYPE, "system")
                    ksafe.safeDelete(MasterKeys.HOST)
                    ksafe.safeDelete(MasterKeys.PORT)
                    ksafe.safeDelete(MasterKeys.USERNAME)
                    ksafe.safeDelete(MasterKeys.PASSWORD)
                }

                is ProxyConfig.Http -> {
                    ksafe.safePut(MasterKeys.TYPE, "http")
                    ksafe.safePut(MasterKeys.HOST, config.host)
                    ksafe.safePut(MasterKeys.PORT, config.port)
                    writeOrClear(MasterKeys.USERNAME, config.username)
                    writeOrClear(MasterKeys.PASSWORD, config.password)
                }

                is ProxyConfig.Socks -> {
                    ksafe.safePut(MasterKeys.TYPE, "socks")
                    ksafe.safePut(MasterKeys.HOST, config.host)
                    ksafe.safePut(MasterKeys.PORT, config.port)
                    writeOrClear(MasterKeys.USERNAME, config.username)
                    writeOrClear(MasterKeys.PASSWORD, config.password)
                }
            }
        }

    override fun getUseMaster(scope: ProxyScope): Flow<Boolean> =
        flow {
            migrationDeferred.await()
            emitAll(ksafe.safeGetFlow(useMasterKeyFor(scope), false))
        }

    override suspend fun setUseMaster(
        scope: ProxyScope,
        useMaster: Boolean,
    ) {
        migrationDeferred.await()
        writeUseMaster(scope, useMaster)
    }

    private suspend fun writeUseMaster(
        scope: ProxyScope,
        useMaster: Boolean,
    ) {
        withContext(Dispatchers.IO) { ksafe.safePut(useMasterKeyFor(scope), useMaster) }
    }

    private suspend fun migrateIfNeeded() {
        if (migrated) return
        migrationLock.withLock {
            if (!migrated) migrated = migrateLegacyProxyKeys()
        }
    }

    // True once nothing is left to move; a partial failure keeps the marker unset so the next start retries.
    private suspend fun migrateLegacyProxyKeys(): Boolean {
        if (ksafe.safeGet(MIGRATION_MARKER, false)) return true
        val snapshot = legacyDataStore.snapshotOrNull() ?: return false

        val keysToClear = mutableListOf<Preferences.Key<*>>()
        val scopesMoved = ProxyScope.entries.map { scope -> migrateLegacyScope(keysFor(scope), snapshot, keysToClear) }
        if (keysToClear.isNotEmpty()) keysToClear += LEGACY_SHARED_KEYS

        val cleared = keysToClear.isEmpty() || legacyDataStore.removeKeys(keysToClear)
        if (scopesMoved.any { !it } || !cleared) return false

        ksafe.safePut(MIGRATION_MARKER, true)
        return true
    }

    // A scope's own legacy key wins over the shared pre-scope one; only the scope's keys are queued for removal.
    private suspend fun migrateLegacyScope(
        keys: ScopeKeys,
        snapshot: Preferences,
        keysToClear: MutableList<Preferences.Key<*>>,
    ): Boolean {
        val type = snapshot[stringPreferencesKey(keys.type)] ?: snapshot[LEGACY_TYPE] ?: return true
        if (!ksafe.safePut(keys.type, type)) return false
        val typeKey = stringPreferencesKey(keys.type)
        if (snapshot[typeKey] != null) keysToClear += typeKey

        val moved =
            listOf(
                copyLegacyValue(snapshot, stringPreferencesKey(keys.host), LEGACY_HOST, keys.host, keysToClear),
                copyLegacyValue(snapshot, intPreferencesKey(keys.port), LEGACY_PORT, keys.port, keysToClear),
                copyLegacyValue(snapshot, stringPreferencesKey(keys.username), LEGACY_USERNAME, keys.username, keysToClear),
                copyLegacyValue(snapshot, stringPreferencesKey(keys.password), LEGACY_PASSWORD, keys.password, keysToClear),
            )
        return moved.all { it }
    }

    private suspend inline fun <reified T> copyLegacyValue(
        snapshot: Preferences,
        scopeKey: Preferences.Key<T>,
        sharedKey: Preferences.Key<T>,
        ksafeKey: String,
        keysToClear: MutableList<Preferences.Key<*>>,
    ): Boolean {
        val value = snapshot[scopeKey] ?: snapshot[sharedKey] ?: return true
        if (!ksafe.safePut(ksafeKey, value)) return false
        keysToClear += scopeKey
        return true
    }

    private suspend fun migrateMasterV2IfNeeded() {
        if (ksafe.safeGet(MIGRATION_MARKER_MASTER_V2, false)) return

        val configs =
            ProxyScope.entries.map { scope ->
                scope to readScopeConfigDirect(scope)
            }

        val tieBreakOrder = listOf(ProxyScope.DOWNLOAD, ProxyScope.DISCOVERY, ProxyScope.TRANSLATION)
        val counts = configs.groupBy { it.second }.mapValues { it.value.size }
        val maxCount = counts.values.maxOrNull() ?: 0
        val winners = counts.filter { it.value == maxCount }.keys
        val winnerConfig =
            tieBreakOrder.firstNotNullOfOrNull { scope ->
                configs.firstOrNull { it.first == scope && it.second in winners }?.second
            } ?: configs.first().second

        writeMasterConfig(winnerConfig)

        configs.forEach { (scope, config) ->
            writeUseMaster(scope, config == winnerConfig)
        }

        ksafe.safePut(MIGRATION_MARKER_MASTER_V2, true)
    }

    private suspend fun readScopeConfigDirect(scope: ProxyScope): ProxyConfig {
        val keys = keysFor(scope)
        return parseConfig(
            type = ksafe.safeGet<String?>(keys.type, null),
            host = ksafe.safeGet<String?>(keys.host, null),
            port = ksafe.safeGet<Int?>(keys.port, null),
            username = ksafe.safeGet<String?>(keys.username, null),
            password = ksafe.safeGet<String?>(keys.password, null),
        )
    }

    private companion object {
        const val MIGRATION_MARKER = "__migrated_proxy_v1__"
        const val MIGRATION_MARKER_MASTER_V2 = "__migrated_proxy_master_v2__"
        const val MAX_PORT = 65535

        val LEGACY_TYPE = stringPreferencesKey("proxy_type")
        val LEGACY_HOST = stringPreferencesKey("proxy_host")
        val LEGACY_PORT = intPreferencesKey("proxy_port")
        val LEGACY_USERNAME = stringPreferencesKey("proxy_username")
        val LEGACY_PASSWORD = stringPreferencesKey("proxy_password")
        val LEGACY_SHARED_KEYS = listOf(LEGACY_TYPE, LEGACY_HOST, LEGACY_PORT, LEGACY_USERNAME, LEGACY_PASSWORD)
    }
}
