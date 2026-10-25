package zed.rainxch.favourites.presentation.import

import kotlinx.collections.immutable.ImmutableList
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform

data class ImportCandidateUi(
    val repoId: Long,
    val owner: String,
    val name: String,
    val ownerAvatarUrl: String,
    val description: String?,
    val primaryLanguage: String?,
    val repoUrl: String,
    val stargazersCount: Int,
    val latestVersion: String?,
    val platforms: ImmutableList<DiscoveryPlatform>,
    val isAlreadyFavourited: Boolean,
)
