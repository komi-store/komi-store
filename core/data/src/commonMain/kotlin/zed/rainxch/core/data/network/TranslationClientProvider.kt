package zed.rainxch.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
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
import zed.rainxch.core.domain.model.settings.ProxyConfig

class TranslationClientProvider(
    proxyConfigFlow: StateFlow<ProxyConfig>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private data class Clients(
        val base: HttpClient,
        val configured: HttpClient,
    )

    @Volatile
    private var current: Clients = createTranslationClient(proxyConfigFlow.value)

    init {
        proxyConfigFlow
            .drop(1)
            .distinctUntilChanged()
            .onEach { config ->
                mutex.withLock {

                    val replacement = createTranslationClient(config)
                    val previous = current
                    current = replacement
                    previous.configured.close()
                    previous.base.close()
                }
            }.launchIn(scope)
    }

    private fun createTranslationClient(proxyConfig: ProxyConfig): Clients {
        val base = createPlatformHttpClient(proxyConfig)
        val configured =
            base.config {
                install(HttpTimeout) {
                    requestTimeoutMillis = TRANSLATION_REQUEST_TIMEOUT_MS
                    connectTimeoutMillis = TRANSLATION_CONNECT_TIMEOUT_MS
                    socketTimeoutMillis = TRANSLATION_REQUEST_TIMEOUT_MS
                }
            }
        return Clients(base, configured)
    }

    val client: HttpClient get() = current.configured

    fun close() {
        current.configured.close()
        current.base.close()
        scope.cancel()
    }

    private companion object {
        private const val TRANSLATION_REQUEST_TIMEOUT_MS = 180_000L
        private const val TRANSLATION_CONNECT_TIMEOUT_MS = 15_000L
    }
}
