package zed.rainxch.core.data.datasource.impl

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import eu.anifantakis.lib.ksafe.KSafe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import zed.rainxch.core.data.datasource.TokenStore
import zed.rainxch.core.data.dto.GithubDeviceTokenSuccessDto
import zed.rainxch.core.data.local.datastore.removeKeys
import zed.rainxch.core.data.local.datastore.snapshotOrNull
import zed.rainxch.core.data.secure.safeDelete
import zed.rainxch.core.data.secure.safeGet
import zed.rainxch.core.data.secure.safeGetFlow
import zed.rainxch.core.data.secure.safePut
import kotlin.time.Clock

class DefaultTokenStore(
    private val ksafe: KSafe,
    private val legacyDataStore: DataStore<Preferences>,
) : TokenStore {
    private val tokenKey = TOKEN_KEY_NAME
    private val legacyKey = stringPreferencesKey("token")
    private val json = Json { ignoreUnknownKeys = true }

    private val migrationLock = Mutex()
    private val migrationDeferred = CompletableDeferred<Unit>()
    private val migrationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        migrationScope.launch {
            try {
                migrateIfNeeded()
            } finally {
                migrationDeferred.complete(Unit)
            }
        }
    }

    override suspend fun save(token: GithubDeviceTokenSuccessDto) {
        migrationDeferred.await()
        val stamped =
            token.copy(
                savedAtEpochMillis = token.savedAtEpochMillis ?: Clock.System.now().toEpochMilliseconds(),
            )
        ksafe.safePut(tokenKey, stamped)
    }

    override fun tokenFlow(): Flow<GithubDeviceTokenSuccessDto?> =
        flow {
            migrationDeferred.await()
            emitAll(ksafe.safeGetFlow<GithubDeviceTokenSuccessDto?>(tokenKey, null))
        }

    override suspend fun currentToken(): GithubDeviceTokenSuccessDto? {
        migrationDeferred.await()
        return ksafe.safeGet<GithubDeviceTokenSuccessDto?>(tokenKey, null)
    }

    override fun blockingCurrentToken(): GithubDeviceTokenSuccessDto? = runBlocking { currentToken() }

    override suspend fun clear() {
        migrationDeferred.await()

        ksafe.safeDelete(tokenKey)
        legacyDataStore.removeKeys(listOf(legacyKey))
    }

    override suspend fun isTokenExpired(): Boolean {
        val token = currentToken() ?: return true
        val savedAt = token.savedAtEpochMillis ?: return false
        val expiresIn = token.expiresIn ?: return false
        val expiresAtMillis = savedAt + (expiresIn * MILLIS_PER_SECOND)
        return Clock.System.now().toEpochMilliseconds() > expiresAtMillis
    }

    private suspend fun migrateIfNeeded() =
        migrationLock.withLock {
            if (ksafe.safeGet<GithubDeviceTokenSuccessDto?>(tokenKey, null) != null) return@withLock
            val legacyRaw = legacyDataStore.snapshotOrNull()?.get(legacyKey) ?: return@withLock
            val parsed = parseLegacyToken(legacyRaw) ?: return@withLock
            if (ksafe.safePut(tokenKey, parsed)) legacyDataStore.removeKeys(listOf(legacyKey))
        }

    private fun parseLegacyToken(raw: String): GithubDeviceTokenSuccessDto? =
        try {
            json.decodeFromString(GithubDeviceTokenSuccessDto.serializer(), raw)
        } catch (_: IllegalArgumentException) {
            null
        }

    private companion object {
        const val TOKEN_KEY_NAME = "github_token"
        const val MILLIS_PER_SECOND = 1000L
    }
}
