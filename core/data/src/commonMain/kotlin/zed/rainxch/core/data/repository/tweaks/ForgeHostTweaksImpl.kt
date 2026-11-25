package zed.rainxch.core.data.repository.tweaks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import zed.rainxch.core.data.secure.safeGet
import zed.rainxch.core.data.secure.safePut
import zed.rainxch.core.domain.repository.tweaks.ForgeHostTweaks

internal class ForgeHostTweaksImpl(
    private val store: TweakStore,
) : ForgeHostTweaks {
    override fun getCustomForgeHosts(): Flow<Set<String>> =
        store.gatedGetFlow<List<String>>(K_CUSTOM_FORGE_HOSTS, emptyList()).map { stored ->
            stored.mapNotNull { it.trim().lowercase().takeIf(String::isNotEmpty) }.toSet()
        }

    override suspend fun addCustomForgeHost(host: String) {
        store.awaitMigration()
        val normalized = host.trim().lowercase()
        if (normalized.isEmpty()) return
        store.rmwLock.withLock {
            val current = store.ksafe.safeGet<List<String>>(K_CUSTOM_FORGE_HOSTS, emptyList()).toSet()
            store.ksafe.safePut(K_CUSTOM_FORGE_HOSTS, (current + normalized).toList())
        }
    }

    override suspend fun removeCustomForgeHost(host: String) {
        store.awaitMigration()
        val normalized = host.trim().lowercase()
        if (normalized.isEmpty()) return
        store.rmwLock.withLock {
            val current = store.ksafe.safeGet<List<String>>(K_CUSTOM_FORGE_HOSTS, emptyList()).toSet()
            if (normalized !in current) return@withLock
            store.ksafe.safePut(K_CUSTOM_FORGE_HOSTS, (current - normalized).toList())
        }
    }
}
