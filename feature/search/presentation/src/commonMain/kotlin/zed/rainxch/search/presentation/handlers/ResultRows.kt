package zed.rainxch.search.presentation.handlers

import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.installation.hasActualUpdate
import zed.rainxch.core.domain.model.installation.isReallyInstalled
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.StarredRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.domain.usecases.SyncInstalledAppsUseCase
import zed.rainxch.core.presentation.model.DiscoveryRepositoryUi
import zed.rainxch.core.presentation.utils.toUi

// Builds each result row's badges and keeps them current as installs, favourites, stars and the signed-in user change.
class ResultRows(
    private val installedAppsRepository: InstalledAppsRepository,
    private val favouritesRepository: FavouritesRepository,
    private val starredRepository: StarredRepository,
    private val userSessionRepository: UserSessionRepository,
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase,
    private val logger: KomiStoreLogger,
) {
    @Volatile
    private var currentUserLogin: String? = null

    internal fun observe(store: SearchStore) {
        observeCurrentUser(store)
        store.launch {
            syncInstalledAppsUseCase().onFailure { logger.warn("Initial sync had issues: ${it.message}") }
        }
        observeInstalledApps(store)
        observeFavourites(store)
        observeStarred(store)
    }

    internal suspend fun rowsFor(
        repos: List<GithubRepoSummary>,
        seenIds: Set<Long>,
    ): List<DiscoveryRepositoryUi> {
        val installedMap = installedAppsRepository.getAllInstalledApps().first().groupBy { it.repoId }
        val favouriteIds = favouritesRepository.getAllFavorites().first().mapTo(HashSet()) { it.repoId }
        val starredIds = starredRepository.getAllStarred().first().mapTo(HashSet()) { it.repoId }
        val login = currentUserLogin
        return repos.map { repo ->
            val apps = installedMap[repo.id].orEmpty()
            DiscoveryRepositoryUi(
                isInstalled = apps.any { it.isReallyInstalled() },
                isFavourite = repo.id in favouriteIds,
                isStarred = repo.id in starredIds,
                isSeen = repo.id in seenIds,
                isCurrentUserOwner = login.owns(repo.owner.login),
                isUpdateAvailable = apps.any { it.hasActualUpdate() },
                repository = repo.toUi(),
            )
        }
    }

    private fun observeCurrentUser(store: SearchStore) {
        store.launch {
            userSessionRepository.getUser().collect { user ->
                val login = user?.username
                currentUserLogin = login
                store.update { current ->
                    current.copy(
                        repositories =
                            current.repositories
                                .map { it.copy(isCurrentUserOwner = login.owns(it.repository.owner.login)) }
                                .toImmutableList(),
                    )
                }
            }
        }
    }

    private fun observeInstalledApps(store: SearchStore) {
        store.observe(installedAppsRepository.getAllInstalledApps()) { installedApps ->
            val installedMap = installedApps.groupBy { it.repoId }
            copy(
                repositories =
                    repositories
                        .map { row ->
                            val apps = installedMap[row.repository.id].orEmpty()
                            row.copy(
                                isInstalled = apps.any { it.isReallyInstalled() },
                                isUpdateAvailable = apps.any { it.hasActualUpdate() },
                            )
                        }.toImmutableList(),
            )
        }
    }

    private fun observeFavourites(store: SearchStore) {
        store.observe(favouritesRepository.getAllFavorites()) { favourites ->
            val favouriteIds = favourites.mapTo(HashSet()) { it.repoId }
            copy(repositories = repositories.map { it.copy(isFavourite = it.repository.id in favouriteIds) }.toImmutableList())
        }
    }

    private fun observeStarred(store: SearchStore) {
        store.observe(starredRepository.getAllStarred()) { starred ->
            val starredIds = starred.mapTo(HashSet()) { it.repoId }
            copy(repositories = repositories.map { it.copy(isStarred = it.repository.id in starredIds) }.toImmutableList())
        }
    }

    private fun String?.owns(ownerLogin: String): Boolean = this != null && ownerLogin.equals(this, ignoreCase = true)
}
