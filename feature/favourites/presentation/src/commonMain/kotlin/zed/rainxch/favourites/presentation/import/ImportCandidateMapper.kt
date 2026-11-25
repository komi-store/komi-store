package zed.rainxch.favourites.presentation.import

import zed.rainxch.core.domain.model.repository.FavoriteRepo
import zed.rainxch.core.domain.model.repository.StarredRepository

internal fun StarredRepository.toImportCandidateUi(isAlreadyFavourited: Boolean): ImportCandidateUi =
    ImportCandidateUi(
        repoId = repoId,
        owner = repoOwner,
        name = repoName,
        ownerAvatarUrl = repoOwnerAvatarUrl,
        description = repoDescription,
        primaryLanguage = primaryLanguage,
        repoUrl = repoUrl,
        stargazersCount = stargazersCount,
        isAlreadyFavourited = isAlreadyFavourited,
    )

internal fun ImportCandidateUi.toFavoriteRepo(now: Long): FavoriteRepo =
    FavoriteRepo(
        repoId = repoId,
        repoName = name,
        repoOwner = owner,
        repoOwnerAvatarUrl = ownerAvatarUrl,
        repoDescription = description,
        primaryLanguage = primaryLanguage,
        repoUrl = repoUrl,
        latestVersion = null,
        latestReleaseUrl = null,
        addedAt = now,
        lastSyncedAt = now,
    )
