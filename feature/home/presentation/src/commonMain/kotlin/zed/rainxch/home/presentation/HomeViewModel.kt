package zed.rainxch.home.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.helpers.ShareManager
import zed.rainxch.core.domain.isDesktop
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.NetworkException
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.repository.PaginatedDiscoveryRepositories
import zed.rainxch.core.domain.repository.BrowseFilterStore
import zed.rainxch.core.presentation.model.GithubRepoSummaryUi
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_share_link
import zed.rainxch.githubstore.core.presentation.res.home_failed_to_load_repositories
import zed.rainxch.githubstore.core.presentation.res.link_copied_to_clipboard
import zed.rainxch.home.domain.repository.HomeRepository
import zed.rainxch.home.presentation.marks.HomeLibraryMarksSource
import zed.rainxch.home.presentation.marks.HomeViewingMarksStore
import zed.rainxch.home.presentation.marks.LibraryMarks
import zed.rainxch.home.presentation.marks.ViewingMarks
import zed.rainxch.home.presentation.model.ChartTab
import zed.rainxch.home.presentation.model.toHomeRepoCardUi

class HomeViewModel(
    private val homeRepository: HomeRepository,
    private val libraryMarksSource: HomeLibraryMarksSource,
    private val viewingMarksStore: HomeViewingMarksStore,
    private val browseFilterStore: BrowseFilterStore,
    private val shareManager: ShareManager,
    private val logger: KomiStoreLogger,
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
    private var library = LibraryMarks()
    private var viewing = ViewingMarks()
    private var actionSheetRepoId: Long? = null

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    selectedPlatform = browseFilterStore.platform.value
                    viewModelScope.launch { libraryMarksSource.syncInstalledApps() }
                    observeMarks()
                    observeBrowseFilter()
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
            is HomeAction.Charts -> onChartsAction(action)
            is HomeAction.PlatformPicker -> onPlatformPickerAction(action)
            is HomeAction.ActionSheet -> onActionSheetAction(action)
            is HomeAction.RepoAction -> viewModelScope.launch { onRepoAction(action) }
            is HomeAction.OnRepoClick, HomeAction.OnSearchClick -> Unit
        }
    }

    private fun onChartsAction(action: HomeAction.Charts) {
        when (action) {
            HomeAction.OnRetry -> loadChart(selectedChart, isRefresh = false)
            HomeAction.OnRefresh -> loadChart(selectedChart, isRefresh = true)
            HomeAction.OnLoadMore -> loadMore()
            is HomeAction.OnChartSelected -> selectChart(action.chart)
        }
    }

    private fun onPlatformPickerAction(action: HomeAction.PlatformPicker) {
        when (action) {
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
        }
    }

    private fun onActionSheetAction(action: HomeAction.ActionSheet) {
        actionSheetRepoId =
            when (action) {
                is HomeAction.OnRepoLongClick -> action.repoId
                HomeAction.OnActionSheetDismiss -> null
            }
        rebuild()
    }

    private suspend fun onRepoAction(action: HomeAction.RepoAction) {
        when (action) {
            is HomeAction.OnShareClick -> share(action.repo)
            is HomeAction.OnHideRepository -> viewingMarksStore.hide(action.repo)
            is HomeAction.OnMarkAsSeen -> viewingMarksStore.markSeen(action.repo)
            is HomeAction.OnMarkAsUnseen -> viewingMarksStore.markUnseen(action.repoId)
        }
    }

    // shareText is not a suspend call, and the platform share sheet or clipboard can throw anything.
    private suspend fun share(repo: GithubRepoSummaryUi) {
        val shared = runCatching { shareManager.shareText("https://github-store.org/app?repo=${repo.fullName}") }
        if (shared.isFailure) {
            logger.error("Failed to share link: ${shared.exceptionOrNull()?.message}")
            _events.send(HomeEvent.OnMessage(getString(Res.string.failed_to_share_link)))
        } else if (isDesktop()) {
            _events.send(HomeEvent.OnMessage(getString(Res.string.link_copied_to_clipboard)))
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
            applyPage(tab, chartFlow(tab, page).first(), replace)
        } catch (e: NetworkException) {
            showLoadFailure(tab, e)
        } catch (e: RateLimitException) {
            showLoadFailure(tab, e)
        } catch (e: LocalStoreException) {
            showLoadFailure(tab, e)
        }
    }

    private fun applyPage(
        tab: ChartTab,
        result: PaginatedDiscoveryRepositories,
        replace: Boolean,
    ) {
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
    }

    private suspend fun showLoadFailure(
        tab: ChartTab,
        failure: Exception,
    ) {
        logger.error("Chart $tab load failed: ${failure.message}")
        cache.getValue(tab).loaded = true
        if (tab == selectedChart) {
            _state.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    errorMessage =
                        it.errorMessage ?: failure.message
                            ?: getString(Res.string.home_failed_to_load_repositories),
                )
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
                .filter { viewing.isVisible(it.id) }
                .map { it.toHomeRepoCardUi(library, viewing) }
                .toImmutableList()
        val sheetCard =
            actionSheetRepoId?.let { id ->
                c.repos.firstOrNull { it.id == id }?.toHomeRepoCardUi(library, viewing)
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

    private fun observeMarks() {
        viewModelScope.launch {
            combine(libraryMarksSource.observe(), viewingMarksStore.observe(), ::Pair).collect { (libraryMarks, viewingMarks) ->
                library = libraryMarks
                viewing = viewingMarks
                rebuild()
            }
        }
        viewModelScope.launch {
            viewingMarksStore.gridLayout().collect { grid ->
                _state.update { it.copy(isGridLayout = grid) }
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
}
