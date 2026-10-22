package zed.rainxch.core.presentation.components.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.isAndroid
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.progress.KomiLoadingIndicator
import zed.rainxch.core.presentation.components.refresh.KomiPullToRefresh
import zed.rainxch.core.presentation.components.refresh.drivesPullToRefresh
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.layout.CardGridSpec
import zed.rainxch.core.presentation.layout.rememberWidthCappedStaggeredCells
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.usesDecor
import zed.rainxch.core.presentation.testing.KomiTestTags
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.feed_empty_title
import zed.rainxch.githubstore.core.presentation.res.feed_end_cap
import zed.rainxch.githubstore.core.presentation.res.feed_failed_to_load
import zed.rainxch.githubstore.core.presentation.res.feed_loading
import zed.rainxch.githubstore.core.presentation.res.feed_offline
import zed.rainxch.githubstore.core.presentation.res.home_retry

// One filter chip above a discovery feed: a category on Explore, a chart on Top Charts.
@Immutable
data class DiscoveryChip(
    val id: String,
    val label: String,
)

// Where a discovery feed stands; the cards themselves come from the screen.
@Immutable
data class DiscoveryFeedStatus(
    val isLoading: Boolean,
    val isRefreshing: Boolean,
    val isLoadingMore: Boolean,
    val hasMore: Boolean,
    val isEmpty: Boolean,
    val errorMessage: String? = null,
    val isOffline: Boolean = false,
)

// Explore and Top Charts: filter chips over a grid of repository cards, with the same loading,
// error, empty, load-more and end states, and pull to refresh on Android. Each screen brings its
// cards (what a long press does differs) and, if it has one, its own empty state.
@Composable
fun DiscoveryFeed(
    chips: ImmutableList<DiscoveryChip>,
    selectedChip: String,
    status: DiscoveryFeedStatus,
    listState: LazyStaggeredGridState,
    gridLayout: Boolean,
    bottomInset: Dp,
    onSelectChip: (String) -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
    empty: @Composable () -> Unit = { DiscoveryEmpty() },
    cards: LazyStaggeredGridScope.() -> Unit,
) {
    LoadMoreAtEnd(listState = listState, status = status, onLoadMore = onLoadMore)
    val grid: @Composable (Modifier) -> Unit = { gridModifier ->
        DiscoveryGrid(
            chips = chips,
            selectedChip = selectedChip,
            status = status,
            listState = listState,
            gridLayout = gridLayout,
            bottomInset = bottomInset,
            onSelectChip = onSelectChip,
            onRetry = onRetry,
            empty = empty,
            cards = cards,
            modifier = gridModifier,
        )
    }
    if (isAndroid()) {
        KomiPullToRefresh(
            isRefreshing = status.isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier.fillMaxSize(),
        ) {
            grid(Modifier.fillMaxSize())
        }
    } else {
        grid(modifier.fillMaxSize())
    }
}

@Composable
private fun LoadMoreAtEnd(
    listState: LazyStaggeredGridState,
    status: DiscoveryFeedStatus,
    onLoadMore: () -> Unit,
) {
    val currentOnLoadMore by rememberUpdatedState(onLoadMore)
    val reachedEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastIndex = info.visibleItemsInfo.maxOfOrNull { it.index } ?: -1
            lastIndex >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(reachedEnd, status.hasMore, status.isLoadingMore, status.isLoading) {
        val idle = !status.isLoadingMore && !status.isLoading
        if (reachedEnd && status.hasMore && idle) currentOnLoadMore()
    }
}

@Composable
private fun DiscoveryGrid(
    chips: ImmutableList<DiscoveryChip>,
    selectedChip: String,
    status: DiscoveryFeedStatus,
    listState: LazyStaggeredGridState,
    gridLayout: Boolean,
    bottomInset: Dp,
    onSelectChip: (String) -> Unit,
    onRetry: () -> Unit,
    empty: @Composable () -> Unit,
    cards: LazyStaggeredGridScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 32.dp + bottomInset)
    val cells =
        rememberWidthCappedStaggeredCells(
            contentPadding = gridPadding,
            maxCardWidth = if (gridLayout) CardGridSpec.CompactMaxCardWidth else CardGridSpec.InfoMaxCardWidth,
        )
    Column(modifier = modifier) {
        if (chips.isNotEmpty()) DiscoveryChipRow(chips = chips, selected = selectedChip, onSelect = onSelectChip)
        LazyVerticalStaggeredGrid(
            columns = cells,
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = gridPadding,
            verticalItemSpacing = CardGridSpec.GridItemSpacing,
            horizontalArrangement = CardGridSpec.GridArrangement,
        ) {
            when {
                status.isLoading && status.isEmpty -> {
                    item(key = "discovery_loading", span = StaggeredGridItemSpan.FullLine) { DiscoveryLoading() }
                }

                status.errorMessage != null && status.isEmpty -> {
                    item(key = "discovery_error", span = StaggeredGridItemSpan.FullLine) {
                        DiscoveryError(message = status.errorMessage, onRetry = onRetry)
                    }
                }

                else -> {
                    feedItems(status = status, empty = empty, cards = cards)
                }
            }
        }
    }
}

private fun LazyStaggeredGridScope.feedItems(
    status: DiscoveryFeedStatus,
    empty: @Composable () -> Unit,
    cards: LazyStaggeredGridScope.() -> Unit,
) {
    if (status.isOffline) {
        item(key = "discovery_offline", span = StaggeredGridItemSpan.FullLine) { DiscoveryOffline() }
    }
    if (status.isEmpty) {
        item(key = "discovery_empty", span = StaggeredGridItemSpan.FullLine) { empty() }
        return
    }
    cards()
    if (status.isLoadingMore) {
        item(key = "discovery_loading_more", span = StaggeredGridItemSpan.FullLine) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                KomiCircularProgress(modifier = Modifier.size(28.dp))
            }
        }
    } else if (!status.hasMore) {
        item(key = "discovery_end_cap", span = StaggeredGridItemSpan.FullLine) { DiscoveryEndCap() }
    }
}

@Composable
private fun DiscoveryChipRow(
    chips: ImmutableList<DiscoveryChip>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.background)
                .padding(top = 6.dp)
                .drivesPullToRefresh()
                .testTag(KomiTestTags.DISCOVERY_CHIPS),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(chips, key = { _, chip -> chip.id }) { index, chip ->
                KomiChip(
                    label = chip.label,
                    kind = KomiChipKind.Filter,
                    selected = chip.id == selected,
                    index = index,
                    onClick = { onSelect(chip.id) },
                )
            }
        }
        if (LocalPersonality.current is MangaPersonality) {
            KomiHorizontalDivider(thickness = 3.dp, color = colors.outline)
        }
    }
}

@Composable
private fun DiscoveryOffline() {
    KomiText(
        text = stringResource(Res.string.feed_offline),
        role = KomiTextRole.Overline,
        color = LocalPersonality.current.colors.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    )
}

@Composable
private fun DiscoveryEndCap() {
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
private fun DiscoveryLoading() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KomiLoadingIndicator()

            KomiText(
                text = stringResource(Res.string.feed_loading),
                role = KomiTextRole.Body,
                color = LocalPersonality.current.colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DiscoveryError(
    message: String,
    onRetry: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 96.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KomiText(
                text = stringResource(Res.string.feed_failed_to_load),
                role = KomiTextRole.SectionTitle,
                textAlign = TextAlign.Center,
            )

            KomiText(
                text = message,
                role = KomiTextRole.Body,
                color = LocalPersonality.current.colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            KomiButton(
                onClick = onRetry,
                label = stringResource(Res.string.home_retry),
                variant = KomiButtonVariant.Outline,
            )
        }
    }
}

@Composable
private fun DiscoveryEmpty() {
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
