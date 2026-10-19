package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.settings.ProxyConfig
import zed.rainxch.core.domain.model.settings.ProxyScope

interface ProxyRepository {
    fun getProxyConfig(scope: ProxyScope): Flow<ProxyConfig>

    suspend fun setProxyConfig(
        scope: ProxyScope,
        config: ProxyConfig,
    )

    fun getMasterProxyConfig(): Flow<ProxyConfig?>

    suspend fun setMasterProxyConfig(config: ProxyConfig)

    fun getUseMaster(scope: ProxyScope): Flow<Boolean>

    suspend fun setUseMaster(
        scope: ProxyScope,
        useMaster: Boolean,
    )
}
