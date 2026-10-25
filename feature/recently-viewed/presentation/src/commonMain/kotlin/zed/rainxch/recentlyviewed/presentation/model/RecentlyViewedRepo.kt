package zed.rainxch.recentlyviewed.presentation.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.components.cards.KomiLibraryStatus

@Immutable
data class RecentlyViewedRepo(
    val repoId: Long,
    val repoName: String,
    val repoOwner: String,
    val repoOwnerAvatarUrl: String,
    val repoDescription: String?,
    val repoUrl: String,
    val platforms: ImmutableList<DiscoveryPlatform>,
    val latestVersion: String?,
    val stars: Int?,
    val installedPackageName: String?,
    val status: KomiLibraryStatus?,
    val viewedAt: Long,
)
