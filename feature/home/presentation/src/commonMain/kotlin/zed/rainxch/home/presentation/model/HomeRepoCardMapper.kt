package zed.rainxch.home.presentation.model

import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.presentation.model.DiscoveryRepositoryUi
import zed.rainxch.core.presentation.utils.toUi
import zed.rainxch.home.presentation.marks.LibraryMarks
import zed.rainxch.home.presentation.marks.ViewingMarks

fun HomeRepoCardUi.toDiscoveryUi(): DiscoveryRepositoryUi =
    DiscoveryRepositoryUi(
        isInstalled = isInstalled,
        isUpdateAvailable = isUpdateAvailable,
        isFavourite = isFavourite,
        isStarred = isStarred,
        isSeen = isSeen,
        isCurrentUserOwner = isCurrentUserOwner,
        repository = rawRepository,
    )

fun GithubRepoSummary.toHomeRepoCardUi(
    library: LibraryMarks,
    viewing: ViewingMarks,
): HomeRepoCardUi {
    val ui = toUi()
    return HomeRepoCardUi(
        id = ui.id,
        name = ui.name,
        ownerLogin = ui.owner.login,
        ownerAvatarUrl = ui.owner.avatarUrl,
        description = ui.description.orEmpty(),
        starsCount = ui.stargazersCount,
        downloadsCount = downloadCount,
        language = language,
        topics = ui.topics.orEmpty().toImmutableList(),
        isInstalled = library.isInstalled(id),
        isUpdateAvailable = library.isUpdateAvailable(id),
        isFavourite = id in library.favouriteIds,
        isStarred = id in library.starredIds,
        isSeen = id in viewing.seenIds,
        isCurrentUserOwner = library.isCurrentUser(owner.login),
        rawRepository = ui,
    )
}
