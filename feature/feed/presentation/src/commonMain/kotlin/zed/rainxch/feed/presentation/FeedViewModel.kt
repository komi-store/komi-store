package zed.rainxch.feed.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.repository.FeedCategory
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.BrowseFilterStore
import zed.rainxch.core.presentation.model.GithubRepoSummaryUi
import zed.rainxch.feed.domain.repository.FeedRepository
import zed.rainxch.feed.presentation.marks.FeedLibraryMarksSource
import zed.rainxch.feed.presentation.marks.FeedViewingMarksStore
import zed.rainxch.feed.presentation.marks.LibraryMarks
import zed.rainxch.feed.presentation.marks.ViewingMarks
import zed.rainxch.feed.presentation.model.toDiscoveryRepoUi
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_share_link
import zed.rainxch.githubstore.core.presentation.res.feed_failed_to_load
import zed.rainxch.githubstore.core.presentation.res.link_copied_to_clipboard

class FeedViewModel(
    private val feedRepository: FeedRepository,
    private val libraryMarksSource: FeedLibraryMarksSource,
    private val viewingMarksStore: FeedViewingMarksStore,
    private val browseFilterStore: BrowseFilterStore,
    private val shareManager: ShareManager,
    private val logger: KomiStoreLogger,
) : ViewModel() {
    private var hasLoadedInitialData = false
    private var loadJob: Job? = null
    private var pageJob: Job? = null

    private var feedRepos: List<GithubRepoSummary> = emptyList()
    private var nextPage = 1
    private var hasMore = false
    private var isOffline = false
    private var selectedPlatform = detectedPlatform()
    private var selectedCategory = FeedCategory.All

    private var library = LibraryMarks()
    private var viewing = ViewingMarks()

    private val _state = MutableStateFlow(FeedState(selectedPlatform = selectedPlatform))
    val state: StateFlow<FeedState> =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    selectedPlatform = browseFilterStore.platform.value
                    selectedCategory = browseFilterStore.category.value
                    _state.update {
                        it.copy(selectedPlatform = selectedPlatform, selectedCategory = selectedCategory)
                    }
                    observeMarks()
                    observeBrowseFilter()
                    reload(isRefresh = false)
                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = FeedState(selectedPlatform = selectedPlatform),
            )

    private val _events = Channel<FeedEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: FeedAction) {
        when (action) {
            is FeedAction.Paging -> onPagingAction(action)
            is FeedAction.Filters -> onFiltersAction(action)
            is FeedAction.RepoAction -> viewModelScope.launch { onRepoAction(action) }
            FeedAction.OnSearchClick, FeedAction.OnProfileClick, is FeedAction.OnRepoClick -> Unit
        }
    }

    private fun onPagingAction(action: FeedAction.Paging) {
        when (action) {
            FeedAction.OnRefresh -> reload(isRefresh = true)
            FeedAction.OnRetry -> reload(isRefresh = false)
            FeedAction.OnLoadMore -> loadMore()
        }
    }

    private fun onFiltersAction(action: FeedAction.Filters) {
        when (action) {
            FeedAction.OnPlatformPickerOpen -> {
                _state.update { it.copy(isPlatformPickerVisible = true) }
            }

            FeedAction.OnPlatformPickerDismiss -> {
                _state.update { it.copy(isPlatformPickerVisible = false) }
            }

            FeedAction.OnResetFilters -> {
                browseFilterStore.setCategory(FeedCategory.All)
                browseFilterStore.setPlatform(DiscoveryPlatform.All)
            }

            is FeedAction.OnPlatformSelected -> {
                _state.update { it.copy(isPlatformPickerVisible = false) }
                browseFilterStore.setPlatform(action.platform)
            }

            is FeedAction.OnCategorySelected -> {
                browseFilterStore.setCategory(action.category)
            }
        }
    }

    private suspend fun onRepoAction(action: FeedAction.RepoAction) {
        when (action) {
            is FeedAction.OnShareClick -> share(action.repo)
            is FeedAction.OnHideRepository -> viewingMarksStore.hide(action.repo)
            is FeedAction.OnMarkAsSeen -> viewingMarksStore.markSeen(action.repo)
            is FeedAction.OnMarkAsUnseen -> viewingMarksStore.markUnseen(action.repoId)
        }
    }

    // shareText is not a suspend call, and the platform share sheet or clipboard can throw anything.
    private suspend fun share(repo: GithubRepoSummaryUi) {
        val shared = runCatching { shareManager.shareText("https://github-store.org/app?repo=${repo.fullName}") }
        if (shared.isFailure) {
            logger.error("Failed to share link: ${shared.exceptionOrNull()?.message}")
            _events.send(FeedEvent.OnMessage(getString(Res.string.failed_to_share_link)))
        } else if (isDesktop()) {
            _events.send(FeedEvent.OnMessage(getString(Res.string.link_copied_to_clipboard)))
        }
    }

    private fun reload(isRefresh: Boolean) {
        loadJob?.cancel()
        pageJob?.cancel()
        loadJob =
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        isRefreshing = isRefresh,
                        isLoading = !isRefresh && feedRepos.isEmpty(),
                        errorMessage = null,
                    )
                }
                loadPage(page = 1, replace = true, forceRefresh = isRefresh)
            }
    }

    private fun loadMore() {
        if (!hasMore || _state.value.isLoadingMore || _state.value.isLoading) return
        pageJob?.cancel()
        pageJob =
            viewModelScope.launch {
                loadPage(page = nextPage, replace = false, forceRefresh = false)
            }
    }

    private suspend fun loadPage(
        page: Int,
        replace: Boolean,
        forceRefresh: Boolean,
    ) {
        if (!replace) _state.update { it.copy(isLoadingMore = true) }

        feedRepository.getFeed(selectedPlatform, page, forceRefresh).fold(
            onSuccess = { feedPage ->
                feedRepos =
                    if (replace) {
                        feedPage.items
                    } else {
                        val existing = feedRepos.mapTo(HashSet()) { it.id }
                        feedRepos + feedPage.items.filter { it.id !in existing }
                    }
                nextPage = feedPage.page + 1
                hasMore = feedPage.hasMore
                isOffline = feedPage.isOffline
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        errorMessage = null,
                    )
                }
                rebuild()
            },
            onFailure = { error ->
                if (error is CancellationException) throw error
                logger.error("Feed load failed (page $page): ${error.message}")
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isLoadingMore = false,
                        errorMessage =
                            if (feedRepos.isEmpty()) {
                                error.message ?: getString(Res.string.feed_failed_to_load)
                            } else {
                                it.errorMessage
                            },
                    )
                }
            },
        )
    }

    private fun rebuild() {
        val visible =
            feedRepos
                .filter { repo -> viewing.isVisible(repo.id) }
                .filter { repo -> selectedCategory.matches(repo.topicCodes) }
                .map { it.toDiscoveryRepoUi(library, viewing) }
                .toImmutableList()

        _state.update {
            it.copy(
                repos = visible,
                hasMore = hasMore,
                isOffline = isOffline,
            )
        }
    }

    private fun observeBrowseFilter() {
        viewModelScope.launch {
            browseFilterStore.platform.collect { p ->
                if (p != selectedPlatform) {
                    selectedPlatform = p
                    _state.update { it.copy(selectedPlatform = p) }
                    _events.send(FeedEvent.OnScrollToTop)
                    reload(isRefresh = false)
                }
            }
        }
        viewModelScope.launch {
            browseFilterStore.category.collect { c ->
                if (c != selectedCategory) {
                    selectedCategory = c
                    _state.update { it.copy(selectedCategory = c) }
                    _events.send(FeedEvent.OnScrollToTop)
                    rebuild()
                }
            }
        }
    }

    private fun detectedPlatform(): DiscoveryPlatform =
        when (getPlatform()) {
            Platform.ANDROID -> DiscoveryPlatform.Android
            Platform.WINDOWS -> DiscoveryPlatform.Windows
            Platform.MACOS -> DiscoveryPlatform.Macos
            Platform.LINUX -> DiscoveryPlatform.Linux
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
}
