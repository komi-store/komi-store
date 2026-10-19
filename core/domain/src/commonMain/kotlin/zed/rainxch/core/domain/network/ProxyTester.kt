package zed.rainxch.core.domain.network

import zed.rainxch.core.domain.model.settings.ProxyConfig

interface ProxyTester {
    suspend fun test(config: ProxyConfig): ProxyTestOutcome

    suspend fun test(
        config: ProxyConfig,
        url: String,
    ): ProxyTestOutcome = test(config)
}
