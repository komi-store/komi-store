package zed.rainxch.favourites.presentation.mappers

import zed.rainxch.core.domain.model.repository.FavoriteRepo
import zed.rainxch.core.presentation.utils.formatAddedAt
import zed.rainxch.favourites.presentation.model.FavouriteRepository

suspend fun FavoriteRepo.toFavouriteRepositoryUi(isCurrentUserOwner: Boolean = false): FavouriteRepository =
    FavouriteRepository(
        repoId = repoId,
        repoName = repoName,
        repoOwner = repoOwner,
        repoOwnerAvatarUrl = repoOwnerAvatarUrl,
        repoDescription = repoDescription,
        primaryLanguage = primaryLanguage,
        repoUrl = repoUrl,
        latestRelease = latestVersion,
        latestReleaseUrl = latestReleaseUrl,
        addedAtFormatter = formatAddedAt(addedAt),
        isCurrentUserOwner = isCurrentUserOwner,
    )
