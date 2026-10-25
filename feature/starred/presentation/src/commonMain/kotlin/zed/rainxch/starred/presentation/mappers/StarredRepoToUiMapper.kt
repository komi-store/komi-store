package zed.rainxch.starred.presentation.mappers

import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.core.domain.model.repository.StarredRepository
import zed.rainxch.core.presentation.model.LibraryInstall
import zed.rainxch.starred.presentation.model.StarredRepositoryUi

fun StarredRepository.toStarredRepositoryUi(
    isFavorite: Boolean = false,
    isCurrentUserOwner: Boolean = false,
    install: LibraryInstall? = null,
) = StarredRepositoryUi(
    repoId = repoId,
    repoName = repoName,
    repoOwner = repoOwner,
    repoOwnerAvatarUrl = repoOwnerAvatarUrl,
    repoDescription = repoDescription,
    primaryLanguage = primaryLanguage,
    repoUrl = repoUrl,
    stargazersCount = stargazersCount,
    forksCount = forksCount,
    openIssuesCount = openIssuesCount,
    isInstalled = isInstalled,
    isFavorite = isFavorite,
    isCurrentUserOwner = isCurrentUserOwner,
    latestRelease = latestVersion,
    latestReleaseUrl = latestReleaseUrl,
    starredAt = starredAt,
    platforms = platforms.toImmutableList(),
    installedPackageName = install?.packageName,
    status = install?.status,
)
