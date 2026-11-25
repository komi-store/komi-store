package zed.rainxch.feed.presentation.model

import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.presentation.model.DiscoveryRepositoryUi
import zed.rainxch.core.presentation.utils.toUi
import zed.rainxch.feed.presentation.marks.LibraryMarks
import zed.rainxch.feed.presentation.marks.ViewingMarks

fun GithubRepoSummary.toDiscoveryRepoUi(
    library: LibraryMarks,
    viewing: ViewingMarks,
): DiscoveryRepositoryUi =
    DiscoveryRepositoryUi(
        isInstalled = library.isInstalled(id),
        isUpdateAvailable = library.isUpdateAvailable(id),
        isFavourite = id in library.favouriteIds,
        isStarred = id in library.starredIds,
        isSeen = id in viewing.seenIds,
        isCurrentUserOwner = library.isCurrentUser(owner.login),
        repository = toUi(),
    )
