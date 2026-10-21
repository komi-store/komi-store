package zed.rainxch.home.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.domain.isAndroid
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.buttons.RepoLayoutToggle
import zed.rainxch.core.presentation.components.cards.DiscoveryRepoCard
import zed.rainxch.core.presentation.components.cards.KomiRepoCardFeed
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.overlays.KomiToastState
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.progress.KomiLoadingIndicator
import zed.rainxch.core.presentation.components.refresh.KomiPullToRefresh
import zed.rainxch.core.presentation.components.refresh.drivesPullToRefresh
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.usesDecor
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.core.presentation.layout.CardGridSpec
import zed.rainxch.core.presentation.layout.rememberWidthCappedStaggeredCells
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.feed_empty_title
import zed.rainxch.githubstore.core.presentation.res.feed_end_cap
import zed.rainxch.githubstore.core.presentation.res.home_finding_repositories
import zed.rainxch.githubstore.core.presentation.res.home_platform_filter
import zed.rainxch.githubstore.core.presentation.res.home_retry
import zed.rainxch.home.presentation.components.HomeChartTabs
import zed.rainxch.core.domain.isDesktop
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.githubstore.core.presentation.res.home_masthead_subtitle
import zed.rainxch.githubstore.core.presentation.res.home_masthead_title
import zed.rainxch.home.presentation.components.HomePlatformPicker
import zed.rainxch.home.presentation.components.RepositoryActionsSheet
import zed.rainxch.home.presentation.model.ChartTab
import zed.rainxch.home.presentation.model.toDiscoveryUi
import zed.rainxch.core.presentation.layout.withoutBottom
import androidx.compose.ui.unit.Dp

@Composable
fun HomeRoot(
    onNavigateToDetails: (repoId: Long) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyStaggeredGridState()
    val coroutineScope = rememberCoroutineScope()
    val toastState = rememberKomiToastState()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            HomeEvent.OnScrollToListTop -> coroutineScope.launch {
                listState.animateScrollToItem(0)
            }

            is HomeEvent.OnMessage -> toastState.show(event.message)
        }
    }

    HomeScreen(
        state = state,
        toastState = toastState,
        listState = listState,
        onAction = { action ->
            when (action) {
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

    val reachedEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastIndex = info.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
            lastIndex >= info.totalItemsCount - 4
        }
    }

    LaunchedEffect(reachedEnd, state.hasMore, state.isLoadingMore, state.isLoading) {
        if (reachedEnd && state.hasMore && !state.isLoadingMore && !state.isLoading) {
            onAction(HomeAction.OnLoadMore)
        }
    }

    KomiScaffold(
        topBar = {
            KomiTopBar(
                title = stringResource(Res.string.home_masthead_title),
                titleAccent = stringResource(Res.string.home_masthead_subtitle),
                actions = {
                    RepoLayoutToggle(
                        isGridLayout = state.isGridLayout,
                        onToggle = { onAction(HomeAction.OnToggleGridLayout) },
                    )
                    if (!isDesktop()) {
                        KomiIconButton(
                            icon = Icons.Rounded.Tune,
                            contentDescription = stringResource(Res.string.home_platform_filter),
                            onClick = { onAction(HomeAction.OnPlatformPopupOpen) },
                            variant = KomiButtonVariant.Primary,
                        )
                    }
                }
            )
        },
        toastState = toastState
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding.withoutBottom()),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (isAndroid()) {
                KomiPullToRefresh(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { onAction(HomeAction.OnRefresh) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    HomeChartFeed(
                        state = state,
                        listState = listState,
                        bottomInset = innerPadding.calculateBottomPadding(),
                        onAction = onAction
                    )
                }
            } else {
                HomeChartFeed(
                    state = state,
                    listState = listState,
                    bottomInset = innerPadding.calculateBottomPadding(),
                    onAction = onAction
                )
            }
        }

        if (state.isPlatformPopupVisible) {
            HomePlatformPicker(
                platforms = state.platformOptions,
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
private fun HomeChartFeed(
    state: HomeState,
    listState: LazyStaggeredGridState,
    bottomInset: Dp,
    onAction: (HomeAction) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val gridPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp + bottomInset)
    val cells =
        rememberWidthCappedStaggeredCells(
            contentPadding = gridPadding,
            maxCardWidth = if (state.isGridLayout) CardGridSpec.ChartCompactMaxCardWidth else CardGridSpec.InfoMaxCardWidth,
        )

    Column(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(colors.background)
                    .drivesPullToRefresh(),
        ) {
            HomeChartTabs(
                selected = state.selectedChart,
                onSelect = { onAction(HomeAction.OnChartSelected(it)) },
            )

            when (LocalPersonality.current) {
                is MangaPersonality -> KomiHorizontalDivider(thickness = 3.dp, color = colors.outline)
                is ClassicPersonality -> Unit
            }
        }

        LazyVerticalStaggeredGrid(
            columns = cells,
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = gridPadding,
            verticalItemSpacing = CardGridSpec.GridItemSpacing,
            horizontalArrangement = CardGridSpec.GridArrangement,
        ) {
            when {
                state.isLoading && state.repos.isEmpty() -> {
                    item(key = "home_loading", span = StaggeredGridItemSpan.FullLine) { HomeLoading() }
                }

                state.errorMessage != null && state.repos.isEmpty() -> {
                    item(key = "home_error", span = StaggeredGridItemSpan.FullLine) {
                        HomeError(
                            message = state.errorMessage,
                            onRetry = { onAction(HomeAction.OnRetry) },
                        )
                    }
                }

                state.repos.isEmpty() -> {
                    item(key = "home_empty", span = StaggeredGridItemSpan.FullLine) { HomeEmpty() }
                }

                else -> {
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

                    if (state.isLoadingMore) {
                        item(key = "home_loading_more", span = StaggeredGridItemSpan.FullLine) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                KomiCircularProgress(modifier = Modifier.size(28.dp))
                            }
                        }
                    } else if (!state.hasMore) {
                        item(key = "home_end_cap", span = StaggeredGridItemSpan.FullLine) { HomeEndCap() }
                    }
                }
            }
        }
    }
}

private fun ChartTab.toFeed(): KomiRepoCardFeed =
    when (this) {
        ChartTab.Trending -> KomiRepoCardFeed.Trending
        ChartTab.Releases -> KomiRepoCardFeed.Release
        ChartTab.Popular -> KomiRepoCardFeed.Popular
    }

@Composable
private fun HomeEndCap() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KomiHorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 2.dp,
            color = LocalPersonality.current.colors.divider,
        )
        if (LocalPersonality.current.usesDecor) {
            KomiText(
                text = stringResource(Res.string.feed_end_cap),
                role = KomiTextRole.Overline,
                color = LocalPersonality.current.colors.onSurfaceVariant,
            )
        }
        KomiHorizontalDivider(
            modifier = Modifier.weight(1f),
            thickness = 2.dp,
            color = LocalPersonality.current.colors.divider,
        )
    }
}

@Composable
private fun HomeEmpty() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        KomiText(
            text = stringResource(Res.string.feed_empty_title),
            role = KomiTextRole.SectionTitle,
            color = LocalPersonality.current.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HomeLoading() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KomiLoadingIndicator()

            Spacer(modifier = Modifier.height(8.dp))

            KomiText(
                text = stringResource(Res.string.home_finding_repositories),
                role = KomiTextRole.SectionTitle,
                color = LocalPersonality.current.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HomeError(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            KomiText(
                text = message,
                role = KomiTextRole.Body,
                color = LocalPersonality.current.colors.onSurface,
            )

            KomiButton(
                onClick = onRetry,
                label = stringResource(Res.string.home_retry),
                variant = KomiButtonVariant.Outline,
            )
        }
    }
}
