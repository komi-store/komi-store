package zed.rainxch.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.getPlatform
import zed.rainxch.core.domain.helpers.ShareManager
import zed.rainxch.core.domain.isDesktop
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.hasActualUpdate
import zed.rainxch.core.domain.model.installation.isReallyInstalled
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.repository.PaginatedDiscoveryRepositories
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.BrowseFilterStore
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.HiddenReposRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.SeenReposRepository
import zed.rainxch.core.domain.repository.StarredRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.domain.use_cases.SyncInstalledAppsUseCase
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_share_link
import zed.rainxch.githubstore.core.presentation.res.home_failed_to_load_repositories
import zed.rainxch.githubstore.core.presentation.res.link_copied_to_clipboard
import zed.rainxch.home.domain.repository.HomeRepository
import zed.rainxch.home.presentation.model.ChartTab
import zed.rainxch.home.presentation.model.HomeRepoCardUi
import zed.rainxch.home.presentation.model.toHomeRepoCardUi

class HomeViewModel(
    private val homeRepository: HomeRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val syncInstalledAppsUseCase: SyncInstalledAppsUseCase,
    private val favouritesRepository: FavouritesRepository,
    private val starredRepository: StarredRepository,
    private val tweaksRepository: TweaksRepository,
    private val seenReposRepository: SeenReposRepository,
    private val hiddenReposRepository: HiddenReposRepository,
    private val userSessionRepository: UserSessionRepository,
    private val browseFilterStore: BrowseFilterStore,
    private val logger: KomiStoreLogger,
    private val shareManager: ShareManager,
) : ViewModel() {
    private class ChartCache {
        var repos: List<GithubRepoSummary> = emptyList()
        var nextPage: Int = 1
        var hasMore: Boolean = true
        var loaded: Boolean = false
    }

    private val cache = ChartTab.entries.associateWith { ChartCache() }
    private var selectedChart = ChartTab.Trending

    private var hasLoadedInitialData = false
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    private var selectedPlatform: DiscoveryPlatform = DiscoveryPlatform.All
    private var installedById: Map<Long, List<InstalledApp>> = emptyMap()
    private var favouriteIds: Set<Long> = emptySet()
    private var starredIds: Set<Long> = emptySet()
    private var seenIds: Set<Long> = emptySet()
    private var hiddenIds: Set<Long> = emptySet()
    private var isHideSeenEnabled = false
    private var currentUserLogin: String? = null
    private var actionSheetRepoId: Long? = null

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    selectedPlatform = browseFilterStore.platform.value
                    observeCurrentUser()
                    syncSystemState()
                    observeInstalledApps()
                    observeFavourites()
                    observeStarredRepos()
                    observeSeenRepos()
                    observeHiddenRepos()
                    observeBrowseFilter()
                    observeHideSeenEnabled()
                    observeRepoGridLayout()
                    loadChart(ChartTab.Trending, isRefresh = false)
                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = HomeState(),
            )

    private val _events = Channel<HomeEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: HomeAction) {
        when (action) {
            HomeAction.OnRetry -> {
                loadChart(selectedChart, isRefresh = false)
            }

            HomeAction.OnRefresh -> {
                loadChart(selectedChart, isRefresh = true)
            }

            HomeAction.OnLoadMore -> {
                loadMore()
            }

            is HomeAction.OnChartSelected -> {
                selectChart(action.chart)
            }

            HomeAction.OnPlatformPopupOpen -> {
                _state.update { it.copy(isPlatformPopupVisible = true) }
            }

            HomeAction.OnPlatformPopupDismiss -> {
                _state.update { it.copy(isPlatformPopupVisible = false) }
            }

            is HomeAction.OnPlatformSelected -> {
                _state.update { it.copy(isPlatformPopupVisible = false) }
                browseFilterStore.setPlatform(action.platform)
            }

            is HomeAction.OnRepoLongClick -> {
                actionSheetRepoId = action.repoId
                rebuild()
            }

            HomeAction.OnActionSheetDismiss -> {
                actionSheetRepoId = null
                rebuild()
            }

            is HomeAction.OnShareClick -> {
                viewModelScope.launch {
                    runCatching {
                        shareManager.shareText("https://github-store.org/app?repo=${action.repo.fullName}")
                    }.onFailure { t ->
                        logger.error("Failed to share link: ${t.message}")
                        _events.send(HomeEvent.OnMessage(getString(Res.string.failed_to_share_link)))
                        return@launch
                    }

                    if (isDesktop()) {
                        _events.send(HomeEvent.OnMessage(getString(Res.string.link_copied_to_clipboard)))
                    }
                }
            }

            is HomeAction.OnHideRepository -> {
                viewModelScope.launch {
                    val repo = action.repo
                    try {
                        hiddenReposRepository.hide(
                            repoId = repo.id,
                            repoName = repo.name,
                            repoOwner = repo.owner.login,
                            repoOwnerAvatarUrl = repo.owner.avatarUrl,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        logger.warn("Hide repository failed for ${repo.id}: ${e.message}")
                    }
                }
            }

            is HomeAction.OnMarkAsSeen -> {
                viewModelScope.launch {
                    val repo = action.repo
                    try {
                        seenReposRepository.markAsSeen(
                            repoId = repo.id,
                            repoName = repo.name,
                            repoOwner = repo.owner.login,
                            repoOwnerAvatarUrl = repo.owner.avatarUrl,
                            repoDescription = repo.description,
                            primaryLanguage = repo.language,
                            repoUrl = repo.htmlUrl,
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        logger.warn("Mark as seen failed for ${repo.id}: ${e.message}")
                    }
                }
            }

            is HomeAction.OnMarkAsUnseen -> {
                viewModelScope.launch {
                    try {
                        seenReposRepository.removeFromHistory(action.repoId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        logger.warn("Mark as unseen failed for ${action.repoId}: ${e.message}")
                    }
                }
            }

            is HomeAction.OnRepoClick, HomeAction.OnSearchClick -> {}
        }
    }

    private fun selectChart(chart: ChartTab) {
        if (chart == selectedChart) return
        selectedChart = chart
        rebuild()
        viewModelScope.launch { _events.send(HomeEvent.OnScrollToListTop) }
        if (!cache.getValue(chart).loaded) {
            loadChart(chart, isRefresh = false)
        }
    }

    private fun loadChart(
        tab: ChartTab,
        isRefresh: Boolean,
    ) {
        loadJob?.cancel()
        loadMoreJob?.cancel()
        loadJob =
            viewModelScope.launch {
                val c = cache.getValue(tab)
                c.nextPage = 1
                c.hasMore = true
                if (!isRefresh) {
                    c.repos = emptyList()
                }
                if (tab == selectedChart) {
                    _state.update {
                        it.copy(
                            isLoading = !isRefresh,
                            isRefreshing = isRefresh,
                            isLoadingMore = false,
                            errorMessage = null,
                        )
                    }
                    if (!isRefresh) rebuild()
                }
                fetchPage(tab, page = 1, replace = true)
            }
    }

    private fun loadMore() {
        val c = cache.getValue(selectedChart)
        if (!c.hasMore || _state.value.isLoadingMore || _state.value.isLoading) return
        _state.update { it.copy(isLoadingMore = true) }
        loadMoreJob =
            viewModelScope.launch {
                fetchPage(selectedChart, page = c.nextPage, replace = false)
            }
    }

    private suspend fun fetchPage(
        tab: ChartTab,
        page: Int,
        replace: Boolean,
    ) {
        try {
            val result = chartFlow(tab, page).first()
            val c = cache.getValue(tab)
            c.repos = if (replace) result.repos else (c.repos + result.repos).distinctBy { it.id }
            c.hasMore = result.hasMore
            c.nextPage = result.nextPageIndex
            c.loaded = true
            if (tab == selectedChart) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        errorMessage = null,
                    )
                }
            }
            rebuild()
        } catch (t: CancellationException) {
            throw t
        } catch (t: Throwable) {
            logger.error("Chart $tab load failed: ${t.message}")
            cache.getValue(tab).loaded = true
            if (tab == selectedChart) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        errorMessage =
                            it.errorMessage ?: t.message
                                ?: getString(Res.string.home_failed_to_load_repositories),
                    )
                }
            }
        }
    }

    private fun platformSet(): Set<DiscoveryPlatform> =
        if (selectedPlatform == DiscoveryPlatform.All) emptySet() else setOf(selectedPlatform)

    private fun chartFlow(
        tab: ChartTab,
        page: Int,
    ): Flow<PaginatedDiscoveryRepositories> =
        when (tab) {
            ChartTab.Trending -> homeRepository.getTrendingRepositories(platformSet(), page)
            ChartTab.Releases -> homeRepository.getHotReleaseRepositories(platformSet(), page)
            ChartTab.Popular -> homeRepository.getMostPopular(platformSet(), page)
        }

    private fun resetAllCharts() {
        cache.values.forEach { c ->
            c.repos = emptyList()
            c.nextPage = 1
            c.hasMore = true
            c.loaded = false
        }
    }

    private fun rebuild() {
        val c = cache.getValue(selectedChart)
        val visible =
            c.repos
                .filterVisible()
                .map { it.toCard() }
                .toImmutableList()
        val sheetCard =
            actionSheetRepoId?.let { id ->
                c.repos.firstOrNull { it.id == id }?.toCard()
            }
        _state.update {
            it.copy(
                selectedChart = selectedChart,
                repos = visible,
                hasMore = c.hasMore,
                actionSheetCard = sheetCard,
            )
        }
    }

    private fun List<GithubRepoSummary>.filterVisible(): List<GithubRepoSummary> =
        filter { repo ->
            repo.id !in hiddenIds && (!isHideSeenEnabled || repo.id !in seenIds)
        }

    private fun GithubRepoSummary.toCard(): HomeRepoCardUi {
        val apps = installedById[id].orEmpty()
        return toHomeRepoCardUi(
            repo = this,
            isInstalled = apps.any { it.isReallyInstalled() },
            isUpdateAvailable = apps.any { it.hasActualUpdate() },
            isFavourite = id in favouriteIds,
            isStarred = id in starredIds,
            isSeen = id in seenIds,
            isCurrentUserOwner =
                currentUserLogin != null &&
                    owner.login.equals(currentUserLogin, ignoreCase = true),
        )
    }

    private fun syncSystemState() {
        viewModelScope.launch {
            try {
                val result = syncInstalledAppsUseCase()
                if (result.isFailure) {
                    logger.warn("Initial sync had issues: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Initial sync failed: ${e.message}")
            }
        }
    }

    private fun observeInstalledApps() {
        viewModelScope.launch {
            installedAppsRepository.getAllInstalledApps().collect { apps ->
                installedById = apps.groupBy { it.repoId }
                rebuild()
            }
        }
    }

    private fun observeBrowseFilter() {
        viewModelScope.launch {
            browseFilterStore.platform.collect { next ->
                _state.update { it.copy(selectedPlatform = next) }
                if (next != selectedPlatform) {
                    selectedPlatform = next
                    resetAllCharts()
                    loadChart(selectedChart, isRefresh = false)
                }
            }
        }
    }

    private fun observeSeenRepos() {
        viewModelScope.launch {
            seenReposRepository.getAllSeenRepoIds().collect { ids ->
                seenIds = ids
                rebuild()
            }
        }
    }

    private fun observeHiddenRepos() {
        viewModelScope.launch {
            hiddenReposRepository.getAllHiddenRepoIds().collect { ids ->
                hiddenIds = ids
                rebuild()
            }
        }
    }

    private fun observeRepoGridLayout() {
        viewModelScope.launch {
            tweaksRepository.getRepoGridLayout().collect { grid ->
                _state.update { it.copy(isGridLayout = grid) }
            }
        }
    }

    private fun observeHideSeenEnabled() {
        viewModelScope.launch {
            tweaksRepository.getHideSeenEnabled().collect { enabled ->
                isHideSeenEnabled = enabled
                rebuild()
            }
        }
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            userSessionRepository.getUser().collect { user ->
                currentUserLogin = user?.username
                rebuild()
            }
        }
    }

    private fun observeFavourites() {
        viewModelScope.launch {
            favouritesRepository.getAllFavorites().collect { favourites ->
                favouriteIds = favourites.map { it.repoId }.toSet()
                rebuild()
            }
        }
    }

    private fun observeStarredRepos() {
        viewModelScope.launch {
            starredRepository.getAllStarred().collect { starred ->
                starredIds = starred.map { it.repoId }.toSet()
                rebuild()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        loadJob?.cancel()
        loadMoreJob?.cancel()
    }
}
