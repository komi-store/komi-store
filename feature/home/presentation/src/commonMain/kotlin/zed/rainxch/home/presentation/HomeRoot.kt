package zed.rainxch.home.presentation

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.domain.isDesktop
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.bars.LocalTabCardSlot
import zed.rainxch.core.presentation.components.cards.DiscoveryRepoCard
import zed.rainxch.core.presentation.components.cards.KomiRepoCardFeed
import zed.rainxch.core.presentation.components.discovery.DiscoveryChip
import zed.rainxch.core.presentation.components.discovery.DiscoveryFeed
import zed.rainxch.core.presentation.components.discovery.DiscoveryFeedStatus
import zed.rainxch.core.presentation.components.discovery.DiscoveryPlatformButton
import zed.rainxch.core.presentation.components.discovery.DiscoveryPlatformPicker
import zed.rainxch.core.presentation.components.discovery.DiscoverySearchButton
import zed.rainxch.core.presentation.components.overlays.KomiToastState
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.layout.withoutBottom
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.home_chart_popular
import zed.rainxch.githubstore.core.presentation.res.home_chart_releases
import zed.rainxch.githubstore.core.presentation.res.home_chart_trending
import zed.rainxch.githubstore.core.presentation.res.home_masthead_subtitle
import zed.rainxch.githubstore.core.presentation.res.home_masthead_title
import zed.rainxch.home.presentation.components.RepositoryActionsSheet
import zed.rainxch.home.presentation.model.ChartTab
import zed.rainxch.home.presentation.model.toDiscoveryUi

@Composable
fun HomeRoot(
    onNavigateToSearch: () -> Unit,
    onNavigateToDetails: (repoId: Long) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyStaggeredGridState()
    val coroutineScope = rememberCoroutineScope()
    val toastState = rememberKomiToastState()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            HomeEvent.OnScrollToListTop -> {
                coroutineScope.launch {
                    listState.animateScrollToItem(0)
                }
            }

            is HomeEvent.OnMessage -> {
                toastState.show(event.message)
            }
        }
    }

    HomeScreen(
        state = state,
        toastState = toastState,
        listState = listState,
        onAction = { action ->
            when (action) {
                HomeAction.OnSearchClick -> onNavigateToSearch()
                is HomeAction.OnRepoClick -> onNavigateToDetails(action.repo.id)
                else -> viewModel.onAction(action)
            }
        },
    )
}

@Composable
private fun HomeScreen(
    state: HomeState,
    toastState: KomiToastState,
    listState: LazyStaggeredGridState,
    onAction: (HomeAction) -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val chips = ChartTab.entries.map { DiscoveryChip(id = it.name, label = it.label()) }.toImmutableList()

    KomiScaffold(
        topBar = {
            KomiTopBar(
                title = stringResource(Res.string.home_masthead_title),
                titleAccent = stringResource(Res.string.home_masthead_subtitle),
                // The Desktop sidebar holds the platform.
                actions =
                    if (isDesktop()) {
                        null
                    } else {
                        {
                            if (LocalTabCardSlot.current == null) {
                                DiscoverySearchButton(onClick = { onAction(HomeAction.OnSearchClick) })
                            }
                            DiscoveryPlatformButton(
                                platform = state.selectedPlatform,
                                onClick = { onAction(HomeAction.OnPlatformPopupOpen) },
                            )
                        }
                    },
            )
        },
        toastState = toastState,
    ) { innerPadding ->
        DiscoveryFeed(
            chips = chips,
            selectedChip = state.selectedChart.name,
            status =
                DiscoveryFeedStatus(
                    isLoading = state.isLoading,
                    isRefreshing = state.isRefreshing,
                    isLoadingMore = state.isLoadingMore,
                    hasMore = state.hasMore,
                    isEmpty = state.repos.isEmpty(),
                    errorMessage = state.errorMessage,
                ),
            listState = listState,
            gridLayout = state.isGridLayout,
            bottomInset = innerPadding.calculateBottomPadding(),
            onSelectChip = { id -> onAction(HomeAction.OnChartSelected(ChartTab.valueOf(id))) },
            onRefresh = { onAction(HomeAction.OnRefresh) },
            onRetry = { onAction(HomeAction.OnRetry) },
            onLoadMore = { onAction(HomeAction.OnLoadMore) },
            modifier = Modifier.padding(innerPadding.withoutBottom()),
        ) {
            itemsIndexed(
                items = state.repos,
                key = { _, card -> "chart_${card.id}" },
            ) { index, card ->
                DiscoveryRepoCard(
                    discoveryRepositoryUi = card.toDiscoveryUi(),
                    onClick = { onAction(HomeAction.OnRepoClick(card.rawRepository)) },
                    onShareClick = { onAction(HomeAction.OnShareClick(card.rawRepository)) },
                    onLongPress = { onAction(HomeAction.OnRepoLongClick(card.id)) },
                    rank = if (state.selectedChart == ChartTab.Popular) index + 1 else 1,
                    feed = state.selectedChart.toFeed(),
                    compact = state.isGridLayout,
                    modifier = Modifier.fillMaxWidth().animateItem(),
                )
            }
        }

        if (state.isPlatformPopupVisible) {
            DiscoveryPlatformPicker(
                selected = state.selectedPlatform,
                onSelect = { onAction(HomeAction.OnPlatformSelected(it)) },
                onDismiss = { onAction(HomeAction.OnPlatformPopupDismiss) },
            )
        }

        state.actionSheetCard?.let { card ->
            RepositoryActionsSheet(
                repository = card.rawRepository,
                isSeen = card.isSeen,
                onDismiss = { onAction(HomeAction.OnActionSheetDismiss) },
                onShare = {
                    onAction(HomeAction.OnActionSheetDismiss)
                    onAction(HomeAction.OnShareClick(card.rawRepository))
                },
                onOpenOnGithub = {
                    onAction(HomeAction.OnActionSheetDismiss)
                    uriHandler.openUri(card.rawRepository.htmlUrl)
                },
                onToggleSeen = {
                    onAction(HomeAction.OnActionSheetDismiss)
                    if (card.isSeen) {
                        onAction(HomeAction.OnMarkAsUnseen(card.id))
                    } else {
                        onAction(HomeAction.OnMarkAsSeen(card.rawRepository))
                    }
                },
                onHide = {
                    onAction(HomeAction.OnActionSheetDismiss)
                    onAction(HomeAction.OnHideRepository(card.rawRepository))
                },
            )
        }
    }
}

@Composable
private fun ChartTab.label(): String =
    stringResource(
        when (this) {
            ChartTab.Trending -> Res.string.home_chart_trending
            ChartTab.Releases -> Res.string.home_chart_releases
            ChartTab.Popular -> Res.string.home_chart_popular
        },
    )

private fun ChartTab.toFeed(): KomiRepoCardFeed =
    when (this) {
        ChartTab.Trending -> KomiRepoCardFeed.Trending
        ChartTab.Releases -> KomiRepoCardFeed.Release
        ChartTab.Popular -> KomiRepoCardFeed.Popular
    }
