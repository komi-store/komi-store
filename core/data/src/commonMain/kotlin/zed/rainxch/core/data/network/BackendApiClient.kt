package zed.rainxch.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.observer.ResponseObserver
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import zed.rainxch.core.data.datasource.TokenStore
import zed.rainxch.core.domain.model.settings.ProxyConfig

internal const val X_GITHUB_TOKEN_HEADER = "X-GitHub-Token"

// The backend's one HTTP client, rebuilt when the proxy changes; its routes are extensions in the Backend*Calls files.
class BackendApiClient(
    proxyConfigFlow: StateFlow<ProxyConfig>,
    private val tokenStore: TokenStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    @Volatile
    private var httpClient: HttpClient = buildClient(proxyConfigFlow.value)

    init {
        proxyConfigFlow
            .drop(1)
            .distinctUntilChanged()
            .onEach { config ->
                mutex.withLock {
                    httpClient.close()
                    httpClient = buildClient(config)
                }
            }.launchIn(scope)
    }

    private fun buildClient(proxyConfig: ProxyConfig): HttpClient =
        createPlatformHttpClient(proxyConfig).config {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = REQUEST_TIMEOUT_MS
                connectTimeoutMillis = CONNECT_TIMEOUT_MS
                socketTimeoutMillis = SOCKET_TIMEOUT_MS
            }
            install(BackgroundRequestMarker)
            install(ResponseObserver) {
                onResponse { response ->
                    BackendRateLimitTracker.record(response)
                }
            }
            defaultRequest {
                url(BACKEND_BASE_URL)
            }
            expectSuccess = false
        }

    fun close() {
        httpClient.close()
        scope.cancel()
    }

    // The token is read only for calls that need the user, so public reads stay anonymous.
    internal suspend fun <T> request(
        authed: Boolean,
        block: suspend HttpClient.(token: String?) -> Result<T>,
    ): Result<T> =
        recoverHttpFailure({ Result.failure(it) }) {
            val token = if (authed) currentUserGithubToken() else null
            httpClient.block(token)
        }

    private suspend fun currentUserGithubToken(): String? =
        tokenStore
            .currentToken()
            ?.accessToken
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private companion object {
        const val REQUEST_TIMEOUT_MS = 5_000L
        const val CONNECT_TIMEOUT_MS = 3_000L
        const val SOCKET_TIMEOUT_MS = 5_000L
    }
}
