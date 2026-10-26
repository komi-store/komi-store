package zed.rainxch.core.domain.repository.tweaks

import kotlinx.coroutines.flow.Flow

interface ForgeHostTweaks {
    fun getCustomForgeHosts(): Flow<Set<String>>

    suspend fun addCustomForgeHost(host: String)

    suspend fun removeCustomForgeHost(host: String)
}
