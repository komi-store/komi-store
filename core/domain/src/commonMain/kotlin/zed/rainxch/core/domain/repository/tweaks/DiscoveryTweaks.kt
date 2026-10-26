package zed.rainxch.core.domain.repository.tweaks

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform

interface DiscoveryTweaks {
    fun getAutoDetectClipboardLinks(): Flow<Boolean>

    suspend fun setAutoDetectClipboardLinks(enabled: Boolean)

    fun getHideSeenEnabled(): Flow<Boolean>

    suspend fun setHideSeenEnabled(enabled: Boolean)

    fun getDiscoveryPlatforms(): Flow<Set<DiscoveryPlatform>>

    suspend fun setDiscoveryPlatforms(platforms: Set<DiscoveryPlatform>)

    fun getShowAllPlatforms(): Flow<Boolean>

    suspend fun setShowAllPlatforms(enabled: Boolean)

    fun getRepoGridLayout(): Flow<Boolean>

    suspend fun setRepoGridLayout(enabled: Boolean)
}
