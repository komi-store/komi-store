package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.StateFlow
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.repository.FeedCategory

interface BrowseFilterStore {
    val platform: StateFlow<DiscoveryPlatform>
    val category: StateFlow<FeedCategory>

    fun setPlatform(platform: DiscoveryPlatform)

    fun setCategory(category: FeedCategory)
}
