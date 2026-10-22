package zed.rainxch.feed.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.domain.isDesktop
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.repository.FeedCategory
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.bars.LocalTabCardSlot
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
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
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.layout.withoutBottom
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.usesDecor
import zed.rainxch.core.presentation.testing.KomiTestTags
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.core.presentation.utils.toLabel
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.feed_empty_reset
import zed.rainxch.githubstore.core.presentation.res.feed_empty_subtitle
import zed.rainxch.githubstore.core.presentation.res.feed_empty_title
import zed.rainxch.githubstore.core.presentation.res.feed_masthead_subtitle
import zed.rainxch.githubstore.core.presentation.res.feed_masthead_title
import zed.rainxch.githubstore.core.presentation.res.feed_masthead_title_accent

@Composable
fun FeedRoot(
    onNavigateToDetails: (repoId: Long) -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToProfile: () -> Unit,
    viewModel: FeedViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberKomiToastState()
    val listState = rememberLazyStaggeredGridState()
    val coroutineScope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is FeedEvent.OnMessage -> toastState.show(event.message)
            FeedEvent.OnScrollToTop -> coroutineScope.launch { listState.scrollToItem(0) }
        }
    }

    FeedScreen(
        state = state,
        toastState = toastState,
        listState = listState,
        onAction = { action ->
            when (action) {
                FeedAction.OnSearchClick -> onNavigateToSearch()
                FeedAction.OnProfileClick -> onNavigateToProfile()
                is FeedAction.OnRepoClick -> onNavigateToDetails(action.repo.id)
                else -> viewModel.onAction(action)
            }
        },
    )
}

@Composable
private fun FeedScreen(
    state: FeedState,
    toastState: KomiToastState,
    listState: LazyStaggeredGridState,
    onAction: (FeedAction) -> Unit,
) {
    // The Desktop sidebar holds the categories and the platform.
    val desktop = isDesktop()
    val chips =
        if (desktop) {
            persistentListOf<DiscoveryChip>()
        } else {
            state.categories.map { DiscoveryChip(id = it.name, label = it.toLabel()) }.toImmutableList()
        }

    KomiScaffold(
        modifier = Modifier.testTag(KomiTestTags.SCREEN_EXPLORE),
        topBar = {
            KomiTopBar(
                title = stringResource(Res.string.feed_masthead_title),
                titleAccent = stringResource(Res.string.feed_masthead_title_accent),
                subtitle = if (LocalPersonality.current.usesDecor) stringResource(Res.string.feed_masthead_subtitle) else null,
                actions =
                    if (desktop) {
                        null
                    } else {
                        {
                            if (LocalTabCardSlot.current == null) {
                                DiscoverySearchButton(onClick = { onAction(FeedAction.OnSearchClick) })
                            }
                            DiscoveryPlatformButton(
                                platform = state.selectedPlatform,
                                onClick = { onAction(FeedAction.OnPlatformPickerOpen) },
                            )
                        }
                    },
            )
        },
        toastState = toastState,
    ) { innerPadding ->
        DiscoveryFeed(
            chips = chips,
            selectedChip = state.selectedCategory.name,
            status =
                DiscoveryFeedStatus(
                    isLoading = state.isLoading,
                    isRefreshing = state.isRefreshing,
                    isLoadingMore = state.isLoadingMore,
                    hasMore = state.hasMore,
                    isEmpty = state.repos.isEmpty(),
                    errorMessage = state.errorMessage,
                    isOffline = state.isOffline,
                ),
            listState = listState,
            gridLayout = state.isGridLayout,
            bottomInset = innerPadding.calculateBottomPadding(),
            onSelectChip = { id -> onAction(FeedAction.OnCategorySelected(FeedCategory.valueOf(id))) },
            onRefresh = { onAction(FeedAction.OnRefresh) },
            onRetry = { onAction(FeedAction.OnRetry) },
            onLoadMore = { onAction(FeedAction.OnLoadMore) },
            modifier = Modifier.padding(innerPadding.withoutBottom()),
            empty = {
                FeedEmpty(
                    category = state.selectedCategory,
                    platform = state.selectedPlatform,
                    onReset = { onAction(FeedAction.OnResetFilters) },
                )
            },
        ) {
            items(
                state.repos,
                key = { card -> "feed_${card.repository.id}" },
            ) { card ->
                DiscoveryRepoCard(
                    discoveryRepositoryUi = card,
                    onClick = { onAction(FeedAction.OnRepoClick(card.repository)) },
                    onShareClick = { onAction(FeedAction.OnShareClick(card.repository)) },
                    onHideClick = { onAction(FeedAction.OnHideRepository(card.repository)) },
                    onToggleSeen = {
                        if (card.isSeen) {
                            onAction(FeedAction.OnMarkAsUnseen(card.repository.id))
                        } else {
                            onAction(FeedAction.OnMarkAsSeen(card.repository))
                        }
                    },
                    feed = if (state.isGridLayout) KomiRepoCardFeed.Plain else KomiRepoCardFeed.Release,
                    compact = state.isGridLayout,
                    modifier = Modifier.fillMaxWidth().animateItem(),
                )
            }
        }
    }

    if (state.isPlatformPickerVisible) {
        DiscoveryPlatformPicker(
            selected = state.selectedPlatform,
            onSelect = { onAction(FeedAction.OnPlatformSelected(it)) },
            onDismiss = { onAction(FeedAction.OnPlatformPickerDismiss) },
        )
    }
}

@Composable
private fun FeedEmpty(
    category: FeedCategory,
    platform: DiscoveryPlatform,
    onReset: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KomiText(
                text = stringResource(Res.string.feed_empty_title),
                role = KomiTextRole.SectionTitle,
                textAlign = TextAlign.Center,
            )

            KomiText(
                text =
                    stringResource(
                        Res.string.feed_empty_subtitle,
                        category.toLabel(),
                        platform.toLabel(),
                    ),
                role = KomiTextRole.Body,
                color = LocalPersonality.current.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            KomiButton(
                onClick = onReset,
                label = stringResource(Res.string.feed_empty_reset),
                variant = KomiButtonVariant.Outline,
            )
        }
    }
}
