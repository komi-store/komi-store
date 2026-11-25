package zed.rainxch.feed.presentation.marks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.StarredRepository
import zed.rainxch.core.domain.repository.UserSessionRepository

class FeedLibraryMarksSource(
    private val installedAppsRepository: InstalledAppsRepository,
    private val favouritesRepository: FavouritesRepository,
    private val starredRepository: StarredRepository,
    private val userSessionRepository: UserSessionRepository,
) {
    // Each source starts empty so a slow profile fetch doesn't hold back the other badges.
    fun observe(): Flow<LibraryMarks> =
        combine(
            installedAppsRepository
                .getAllInstalledApps()
                .map { apps -> apps.groupBy { it.repoId } }
                .onStart { emit(emptyMap<Long, List<InstalledApp>>()) },
            favouritesRepository
                .getAllFavorites()
                .map { favourites -> favourites.map { it.repoId }.toSet() }
                .onStart { emit(emptySet()) },
            starredRepository
                .getAllStarred()
                .map { starred -> starred.map { it.repoId }.toSet() }
                .onStart { emit(emptySet()) },
            userSessionRepository
                .getUser()
                .map { it?.username }
                .onStart { emit(null) },
            ::LibraryMarks,
        )
}
