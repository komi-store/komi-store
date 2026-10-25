package zed.rainxch.favourites.presentation.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.components.cards.KomiLibraryStatus

@Immutable
data class FavouriteRepository(
    val repoId: Long,
    val repoName: String,
    val repoOwner: String,
    val repoOwnerAvatarUrl: String,
    val repoDescription: String?,
    val primaryLanguage: String?,
    val repoUrl: String,
    val addedAt: Long,
    val latestRelease: String?,
    val latestReleaseUrl: String?,
    val stars: Int?,
    val platforms: ImmutableList<DiscoveryPlatform>,
    val sourceHost: String?,
    val installedPackageName: String?,
    val status: KomiLibraryStatus?,
    val isCurrentUserOwner: Boolean = false,
)
