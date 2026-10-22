package zed.rainxch.favourites.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.model.repository.FavoriteRepo
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.presentation.utils.TimeZoneChangeSignal
import zed.rainxch.favourites.presentation.mappers.toFavouriteRepositoryUi
import zed.rainxch.favourites.presentation.model.FavouritesSortRule
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class FavouritesViewModel(
    private val favouritesRepository: FavouritesRepository,
    private val tweaksRepository: TweaksRepository,
    private val userSessionRepository: UserSessionRepository,
) : ViewModel() {
    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(FavouritesState())
    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    loadFavouriteRepos()

                    hasLoadedInitialData = true
                }
            }.map { it.withDerived() }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = FavouritesState(),
            )

    private fun FavouritesState.withDerived(): FavouritesState {
        val query = searchQuery.trim().lowercase()
        val filtered =
            if (query.isBlank()) {
                favouriteRepositories
            } else {
                favouriteRepositories
                    .filter { repo ->
                        repo.repoName.lowercase().contains(query) ||
                            repo.repoOwner.lowercase().contains(query) ||
                            (repo.repoDescription?.lowercase()?.contains(query) == true) ||
                            (repo.primaryLanguage?.lowercase()?.contains(query) == true)
                    }.toImmutableList()
            }
        return copy(filteredRepositories = filtered)
    }

    private fun loadFavouriteRepos() {
        viewModelScope.launch {
            combine(
                favouritesRepository.getAllFavorites(),
                userSessionRepository.getUser(),
                tweaksRepository.getFavouritesSortRule(),
                TimeZoneChangeSignal.revision,
            ) { favorites, user, sortStored, _ ->
                val sortRule = FavouritesSortRule.fromName(sortStored)
                val currentLogin = user?.username
                val sorted = favorites.sortedWith(favouritesComparator(sortRule))
                val items =
                    sorted.map { repo ->
                        repo.toFavouriteRepositoryUi(
                            isCurrentUserOwner =
                                currentLogin != null &&
                                    repo.repoOwner.equals(currentLogin, ignoreCase = true),
                        )
                    }
                items to sortRule
            }.flowOn(Dispatchers.Default)
                .collect { (favoriteRepos, sortRule) ->
                    _state.update {
                        it.copy(
                            favouriteRepositories = favoriteRepos.toImmutableList(),
                            sortRule = sortRule,
                        )
                    }
                }
        }
    }

    private fun favouritesComparator(sortRule: FavouritesSortRule): Comparator<FavoriteRepo> =
        when (sortRule) {
            FavouritesSortRule.RecentlyAdded -> {
                compareByDescending<FavoriteRepo> { it.addedAt }
                    .thenBy { it.repoName.lowercase() }
            }

            FavouritesSortRule.NameAsc -> {
                compareBy<FavoriteRepo> { it.repoName.lowercase() }
                    .thenBy { it.repoOwner.lowercase() }
            }
        }

    @OptIn(ExperimentalTime::class)
    fun onAction(action: FavouritesAction) {
        when (action) {
            FavouritesAction.OnNavigateBackClick -> {}

            is FavouritesAction.OnRepositoryClick -> {}

            is FavouritesAction.OnDeveloperProfileClick -> {}

            FavouritesAction.OnImportStarsClick -> {}

            is FavouritesAction.OnSearchChange -> {
                _state.update { it.copy(searchQuery = action.query) }
            }

            is FavouritesAction.OnSortRuleSelected -> {
                viewModelScope.launch {
                    try {
                        tweaksRepository.setFavouritesSortRule(action.sortRule.name)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                    }
                }
            }

            is FavouritesAction.OnToggleFavorite -> {
                viewModelScope.launch {
                    val repo = action.favouriteRepository

                    val favoriteRepo =
                        FavoriteRepo(
                            repoId = repo.repoId,
                            repoName = repo.repoName,
                            repoOwner = repo.repoOwner,
                            repoOwnerAvatarUrl = repo.repoOwnerAvatarUrl,
                            repoDescription = repo.repoDescription,
                            primaryLanguage = repo.primaryLanguage,
                            repoUrl = repo.repoUrl,
                            latestVersion = repo.latestRelease,
                            latestReleaseUrl = repo.latestReleaseUrl,
                            addedAt = Clock.System.now().toEpochMilliseconds(),
                            lastSyncedAt = Clock.System.now().toEpochMilliseconds(),
                        )

                    favouritesRepository.toggleFavorite(favoriteRepo)
                }
            }
        }
    }
}
