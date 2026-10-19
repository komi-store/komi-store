package zed.rainxch.core.data.repository

import eu.anifantakis.lib.ksafe.KSafe
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import zed.rainxch.core.domain.model.account.HostNames
import zed.rainxch.core.domain.model.account.HostToken
import zed.rainxch.core.domain.model.account.TokenValidation
import zed.rainxch.core.domain.model.error.TokenValidationException
import zed.rainxch.core.domain.repository.HostTokenRepository
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock

class HostTokenRepositoryImpl(
    private val ksafe: KSafe,
    private val httpClient: HttpClient,
) : HostTokenRepository {
    private val initScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val rmwLock = Mutex()
    private val cache = MutableStateFlow<List<HostToken>>(emptyList())
    private val loadOnce = Mutex()

    @Volatile private var loaded = false

    private val json = Json { ignoreUnknownKeys = true }

    init {

        initScope.launch { runCatching { ensureLoaded() } }
    }

    override fun observeAll(): Flow<List<HostToken>> = cache.asStateFlow().onStart { ensureLoaded() }

    override suspend fun get(host: String): HostToken? {
        ensureLoaded()
        val key = HostNames.normalize(host)
        return cache.value.firstOrNull { it.host == key }
    }

    override suspend fun set(
        host: String,
        token: String,
        displayName: String?,
    ) {
        val key = HostNames.normalize(host)
        if (key.isBlank() || token.isBlank()) return
        rmwLock.withLock {
            ensureLoadedLocked()
            val now = Clock.System.now().toEpochMilliseconds()
            val updated =
                HostToken(
                    host = key,
                    token = token,
                    displayName = displayName?.trim()?.takeIf { it.isNotEmpty() },
                    createdAtEpochMillis = now,
                )
            val next = cache.value.filterNot { it.host == key } + updated
            persistOrThrow(next)
        }
    }

    override suspend fun delete(host: String) {
        val key = HostNames.normalize(host)
        rmwLock.withLock {
            ensureLoadedLocked()
            val before = cache.value
            val after = before.filterNot { it.host == key }
            if (after.size == before.size) return@withLock
            persistOrThrow(after)
        }
    }

    override suspend fun validate(
        host: String,
        token: String,
    ): Result<TokenValidation> {
        val key = HostNames.normalize(host)
        if (key.isBlank()) return Result.failure(IllegalArgumentException("blank host"))
        return try {
            val response =
                httpClient.get(buildUserUrl(key)) {
                    accept(ContentType.Application.Json)
                    header(HttpHeaders.Authorization, "token $token")
                    header(HttpHeaders.UserAgent, "GithubStore/1.0 (host-token-validate)")
                }
            Result.success(mapValidation(response))
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: IOException) {
            Result.failure(TokenValidationException.Unreachable(e))
        } catch (e: UnresolvedAddressException) {
            Result.failure(TokenValidationException.Unreachable(e))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun buildUserUrl(host: String): String =
        when (host) {
            HostNames.GITHUB -> "https://api.github.com/user"
            else -> "https://$host/api/v1/user"
        }

    private suspend fun mapValidation(response: HttpResponse): TokenValidation {
        val status = response.status
        if (status.value !in 200..299) throw TokenValidationException.HttpStatus(status.value)
        val login =
            runCatching {
                val text = response.bodyAsText()
                val pattern = Regex("\"login\"\\s*:\\s*\"([^\"]+)\"")
                pattern.find(text)?.groupValues?.getOrNull(1)
            }.getOrNull()
        val scopesHeader = response.headers["X-OAuth-Scopes"].orEmpty()
        val scopes =
            if (scopesHeader.isBlank()) {
                emptyList()
            } else {
                scopesHeader.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            }
        val remaining = response.headers["X-RateLimit-Remaining"]?.toIntOrNull()
        return TokenValidation(login = login, scopes = scopes, rateLimitRemaining = remaining)
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        loadOnce.withLock {
            if (loaded) return
            ensureLoadedLocked()
        }
    }

    private suspend fun ensureLoadedLocked() {
        if (loaded) return
        val raw = runCatching { ksafe.get(KEY_TOKENS_JSON, "") }.getOrDefault("")
        val list =
            if (raw.isBlank()) {
                emptyList()
            } else {
                runCatching { json.decodeFromString(ListSerializer(HostToken.serializer()), raw) }
                    .getOrDefault(emptyList())
            }
        cache.value = list
        loaded = true
    }

    private suspend fun persistOrThrow(list: List<HostToken>) {
        val previous = cache.value
        val encoded = json.encodeToString(ListSerializer(HostToken.serializer()), list)
        try {
            ksafe.put(KEY_TOKENS_JSON, encoded)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            cache.value = previous
            throw t
        }
        cache.value = list
    }

    private companion object {
        const val KEY_TOKENS_JSON = "host_tokens_v1"
    }
}
