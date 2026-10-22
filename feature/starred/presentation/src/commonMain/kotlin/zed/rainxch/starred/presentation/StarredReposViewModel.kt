@file:OptIn(ExperimentalTime::class)

package zed.rainxch.starred.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
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
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.model.repository.FavoriteRepo
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.StarredRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.presentation.utils.toUserMessage
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.starred.presentation.mappers.toStarredRepositoryUi
import zed.rainxch.starred.presentation.model.StarredRepositoryUi
import zed.rainxch.starred.presentation.model.StarredSortRule
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class StarredReposViewModel(
    private val userSessionRepository: UserSessionRepository,
    private val starredRepository: StarredRepository,
    private val favouritesRepository: FavouritesRepository,
    private val tweaksRepository: TweaksRepository,
) : ViewModel() {
    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(StarredReposState())
    val state =
        _state
            .map { state ->
                val matching = filterStarred(state.starredRepositories, state.searchQuery)
                val installable = matching.filter { it.latestRelease != null }
                state.copy(
                    filteredRepositories = if (state.showAllStarred) matching else installable.toImmutableList(),
                    notInstallableCount = matching.size - installable.size,
                )
            }.flowOn(Dispatchers.Default)
            .onStart {
                if (!hasLoadedInitialData) {
                    checkAuthAndLoad()
                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = StarredReposState(),
            )

    private fun filterStarred(
        repositories: ImmutableList<StarredRepositoryUi>,
        query: String,
    ): ImmutableList<StarredRepositoryUi> {
        val normalized = query.trim().lowercase()
        if (normalized.isBlank()) return repositories

        return repositories
            .filter { repository ->
                repository.repoName.lowercase().contains(normalized) ||
                    repository.repoOwner.lowercase().contains(normalized) ||
                    repository.repoDescription?.lowercase()?.contains(normalized) == true ||
                    repository.primaryLanguage?.lowercase()?.contains(normalized) == true
            }.toImmutableList()
    }

    private suspend fun lastSyncSubtitle(lastSyncTime: Long?): String? {
        if (lastSyncTime == null) return null

        val diff = Clock.System.now().toEpochMilliseconds() - lastSyncTime
        val relative =
            when {
                diff < 60_000 -> getString(Res.string.just_now)
                diff < 3_600_000 -> getString(Res.string.minutes_ago, diff / 60_000)
                diff < 86_400_000 -> getString(Res.string.hours_ago, diff / 3_600_000)
                else -> getString(Res.string.days_ago, diff / 86_400_000)
            }

        return "${getString(Res.string.last_synced)}: $relative"
    }

    private fun checkAuthAndLoad() {
        viewModelScope.launch {
            val isAuthenticated = userSessionRepository.isCurrentlyUserLoggedIn()

            _state.update { it.copy(isAuthenticated = isAuthenticated) }

            if (isAuthenticated) {
                loadStarredRepos()
                syncIfNeeded()
            }
        }
    }

    private fun loadStarredRepos() {
        viewModelScope.launch {
            combine(
                starredRepository.getAllStarred(),
                favouritesRepository.getAllFavorites(),
                userSessionRepository.getUser(),
                tweaksRepository.getStarredSortRule(),
            ) { starred, favorites, user, sortStored ->
                val sortRule = StarredSortRule.fromName(sortStored)
                val favoriteIds = favorites.map { it.repoId }.toSet()
                val currentLogin = user?.username

                val items =
                    starred.map {
                        it.toStarredRepositoryUi(
                            isFavorite = favoriteIds.contains(it.repoId),
                            isCurrentUserOwner =
                                currentLogin != null &&
                                    it.repoOwner.equals(currentLogin, ignoreCase = true),
                        )
                    }

                items.sortedWith(starredComparator(sortRule)) to sortRule
            }.flowOn(Dispatchers.Default)
                .collect { (starredRepos, sortRule) ->
                    _state.update {
                        it.copy(
                            starredRepositories = starredRepos.toImmutableList(),
                            sortRule = sortRule,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    private fun starredComparator(sortRule: StarredSortRule): Comparator<StarredRepositoryUi> =
        when (sortRule) {
            StarredSortRule.RecentlyStarred -> {
                compareByDescending<StarredRepositoryUi> { it.starredAt ?: Long.MIN_VALUE }
                    .thenBy { it.repoName.lowercase() }
            }

            StarredSortRule.NameAsc -> {
                compareBy<StarredRepositoryUi> { it.repoName.lowercase() }
                    .thenBy { it.repoOwner.lowercase() }
            }

            StarredSortRule.StarsDesc -> {
                compareByDescending<StarredRepositoryUi> { it.stargazersCount }
                    .thenBy { it.repoName.lowercase() }
            }
        }

    private fun syncIfNeeded() {
        viewModelScope.launch {
            if (starredRepository.needsSync()) {
                syncStarredRepos()
            } else {
                val lastSync = starredRepository.getLastSyncTime()
                val subtitle = lastSyncSubtitle(lastSync)
                _state.update { it.copy(lastSyncTime = lastSync, lastSyncSubtitle = subtitle) }
            }
        }
    }

    private fun syncStarredRepos(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(isSyncing = true, errorMessage = null, lastSyncSubtitle = null) }

            val result = starredRepository.syncStarredRepos(forceRefresh)

            result
                .onSuccess {
                    val lastSync = starredRepository.getLastSyncTime()
                    val subtitle = lastSyncSubtitle(lastSync)
                    _state.update {
                        it.copy(
                            isSyncing = false,
                            lastSyncTime = lastSync,
                            lastSyncSubtitle = subtitle,
                        )
                    }
                }.onFailure { error ->
                    if (error is CancellationException) throw error
                    val subtitle = lastSyncSubtitle(_state.value.lastSyncTime)
                    _state.update {
                        it.copy(
                            isSyncing = false,
                            lastSyncSubtitle = subtitle,
                            errorMessage = error.toUserMessage(Res.string.sync_starred_failed),
                        )
                    }
                }
        }
    }

    fun onAction(action: StarredReposAction) {
        when (action) {
            StarredReposAction.OnNavigateBackClick,
            is StarredReposAction.OnRepositoryClick,
            is StarredReposAction.OnDeveloperProfileClick,
            StarredReposAction.OnSignInClick,
            -> {}

            StarredReposAction.OnRefresh -> {
                _state.update { it.copy(searchQuery = "") }
                syncStarredRepos(forceRefresh = true)
            }

            StarredReposAction.OnRetrySync -> {
                syncStarredRepos(forceRefresh = true)
            }

            StarredReposAction.OnDismissError -> {
                _state.update { it.copy(errorMessage = null) }
            }

            StarredReposAction.OnToggleShowAllStarred -> {
                _state.update { it.copy(showAllStarred = !it.showAllStarred) }
            }

            is StarredReposAction.OnSearchChange -> {
                _state.update { it.copy(searchQuery = action.query) }
            }

            is StarredReposAction.OnSortRuleSelected -> {
                viewModelScope.launch {
                    try {
                        tweaksRepository.setStarredSortRule(action.sortRule.name)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                    }
                }
            }

            is StarredReposAction.OnToggleFavorite -> {
                viewModelScope.launch {
                    val repo = action.repository

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
