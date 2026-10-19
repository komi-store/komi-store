package zed.rainxch.core.data.network

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
import zed.rainxch.core.domain.repository.HostTokenRepository

class ForgejoClientRegistry(
    private val proxyConfigFlow: StateFlow<ProxyConfig>,
    private val hostTokenRepository: HostTokenRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val clients = mutableMapOf<String, ForgejoApiClient>()

    init {
        proxyConfigFlow
            .drop(1)
            .distinctUntilChanged()
            .onEach { _ ->
                mutex.withLock {
                    clients.values.forEach { it.close() }
                    clients.clear()
                }
            }.launchIn(scope)
    }

    suspend fun clientFor(host: String): ForgejoApiClient {
        val key = host.lowercase().trim()
        return mutex.withLock {
            clients.getOrPut(key) {
                ForgejoApiClient(key, proxyConfigFlow.value, hostTokenRepository)
            }
        }
    }

    suspend fun close() {
        mutex.withLock {
            clients.values.forEach { it.close() }
            clients.clear()
        }
        scope.cancel()
    }
}
