package zed.rainxch.core.data.repository.tweaks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import zed.rainxch.core.data.secure.safePut
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.repository.tweaks.DiscoveryTweaks

internal class DiscoveryTweaksImpl(
    private val store: TweakStore,
) : DiscoveryTweaks {
    override fun getAutoDetectClipboardLinks(): Flow<Boolean> = store.gatedGetFlow(K_AUTO_DETECT_CLIPBOARD, false)

    override suspend fun setAutoDetectClipboardLinks(enabled: Boolean) {
        store.awaitMigration()
        store.ksafe.safePut(K_AUTO_DETECT_CLIPBOARD, enabled)
    }

    override fun getHideSeenEnabled(): Flow<Boolean> = store.gatedGetFlow(K_HIDE_SEEN_ENABLED, false)

    override suspend fun setHideSeenEnabled(enabled: Boolean) {
        store.awaitMigration()
        store.ksafe.safePut(K_HIDE_SEEN_ENABLED, enabled)
    }

    override fun getDiscoveryPlatforms(): Flow<Set<DiscoveryPlatform>> =
        store.gatedGetFlow<List<String>>(K_DISCOVERY_PLATFORMS, emptyList()).map { stored ->
            stored
                .mapNotNull { name ->
                    DiscoveryPlatform.entries.find { it.name == name && it != DiscoveryPlatform.All }
                }.toSet()
        }

    override suspend fun setDiscoveryPlatforms(platforms: Set<DiscoveryPlatform>) {
        store.awaitMigration()
        store.ksafe.safePut(
            K_DISCOVERY_PLATFORMS,
            platforms.filter { it != DiscoveryPlatform.All }.map { it.name },
        )
    }

    override fun getShowAllPlatforms(): Flow<Boolean> = store.gatedGetFlow(K_SHOW_ALL_PLATFORMS, false)

    override suspend fun setShowAllPlatforms(enabled: Boolean) {
        store.awaitMigration()
        store.ksafe.safePut(K_SHOW_ALL_PLATFORMS, enabled)
    }

    override fun getRepoGridLayout(): Flow<Boolean> = store.gatedGetFlow(K_REPO_GRID_LAYOUT, false)

    override suspend fun setRepoGridLayout(enabled: Boolean) {
        store.awaitMigration()
        store.ksafe.safePut(K_REPO_GRID_LAYOUT, enabled)
    }
}
