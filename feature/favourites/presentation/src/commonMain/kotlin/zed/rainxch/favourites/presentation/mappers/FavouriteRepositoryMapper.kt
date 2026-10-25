package zed.rainxch.favourites.presentation.mappers

import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.core.domain.model.repository.FavoriteRepo
import zed.rainxch.core.presentation.model.LibraryInstall
import zed.rainxch.favourites.presentation.model.FavouriteRepository

fun FavoriteRepo.toFavouriteRepositoryUi(
    isCurrentUserOwner: Boolean,
    install: LibraryInstall?,
): FavouriteRepository =
    FavouriteRepository(
        repoId = repoId,
        repoName = repoName,
        repoOwner = repoOwner,
        repoOwnerAvatarUrl = repoOwnerAvatarUrl,
        repoDescription = repoDescription,
        primaryLanguage = primaryLanguage,
        repoUrl = repoUrl,
        addedAt = addedAt,
        latestRelease = latestVersion,
        latestReleaseUrl = latestReleaseUrl,
        stars = stargazersCount,
        platforms = platforms.toImmutableList(),
        sourceHost = sourceHost,
        installedPackageName = install?.packageName,
        status = install?.status,
        isCurrentUserOwner = isCurrentUserOwner,
    )
