package zed.rainxch.recentlyviewed.presentation.mappers

import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.core.domain.model.repository.SeenRepo
import zed.rainxch.core.presentation.model.LibraryInstall
import zed.rainxch.recentlyviewed.presentation.model.RecentlyViewedRepo

fun SeenRepo.toRecentlyViewedRepoUi(install: LibraryInstall?): RecentlyViewedRepo =
    RecentlyViewedRepo(
        repoId = repoId,
        repoName = repoName,
        repoOwner = repoOwner,
        repoOwnerAvatarUrl = repoOwnerAvatarUrl,
        repoDescription = repoDescription,
        repoUrl = repoUrl,
        platforms = platforms.toImmutableList(),
        latestVersion = latestVersion,
        stars = stargazersCount,
        installedPackageName = install?.packageName,
        status = install?.status,
        viewedAt = seenAt,
    )
