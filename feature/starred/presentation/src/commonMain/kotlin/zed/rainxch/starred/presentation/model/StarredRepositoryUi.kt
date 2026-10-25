package zed.rainxch.starred.presentation.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.components.cards.KomiLibraryStatus

data class StarredRepositoryUi(
    val repoId: Long,
    val repoName: String,
    val repoOwner: String,
    val repoOwnerAvatarUrl: String,
    val repoDescription: String?,
    val primaryLanguage: String?,
    val repoUrl: String,
    val stargazersCount: Int,
    val forksCount: Int,
    val openIssuesCount: Int,
    val isInstalled: Boolean,
    val isFavorite: Boolean = false,
    val isCurrentUserOwner: Boolean = false,
    val latestRelease: String?,
    val latestReleaseUrl: String?,
    val starredAt: Long?,
    val platforms: ImmutableList<DiscoveryPlatform> = persistentListOf(),
    val installedPackageName: String? = null,
    val status: KomiLibraryStatus? = null,
)
