package zed.rainxch.core.data.network

import io.ktor.client.HttpClient
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
import zed.rainxch.core.data.data_source.TokenStore
import zed.rainxch.core.domain.model.settings.ProxyConfig
import zed.rainxch.core.domain.repository.HostTokenRepository
import zed.rainxch.core.domain.repository.RateLimitRepository
import zed.rainxch.core.domain.repository.UserSessionRepository

class GitHubClientProvider(
    private val tokenStore: TokenStore,
    private val rateLimitRepository: RateLimitRepository,
    private val userSessionRepository: UserSessionRepository,
    private val hostTokenRepository: HostTokenRepository,
    proxyConfigFlow: StateFlow<ProxyConfig>,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    @Volatile
    private var currentClient: HttpClient =
        createGitHubHttpClient(
            tokenStore = tokenStore,
            rateLimitRepository = rateLimitRepository,
            userSessionRepository = userSessionRepository,
            hostTokenRepository = hostTokenRepository,
            proxyConfig = proxyConfigFlow.value,
        )

    init {
        proxyConfigFlow
            .drop(1)
            .distinctUntilChanged()
            .onEach { proxyConfig ->
                mutex.withLock {
                    val replacement =
                        createGitHubHttpClient(
                            tokenStore = tokenStore,
                            rateLimitRepository = rateLimitRepository,
                            userSessionRepository = userSessionRepository,
                            hostTokenRepository = hostTokenRepository,
                            proxyConfig = proxyConfig,
                        )
                    val previous = currentClient
                    currentClient = replacement
                    previous.close()
                }
            }.launchIn(scope)
    }

    val client: HttpClient get() = currentClient

    fun close() {
        currentClient.close()
        scope.cancel()
    }
}
