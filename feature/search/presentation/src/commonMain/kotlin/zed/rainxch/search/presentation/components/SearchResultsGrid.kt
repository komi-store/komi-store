package zed.rainxch.search.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import zed.rainxch.core.presentation.components.ScrollbarContainer
import zed.rainxch.core.presentation.components.cards.DiscoveryRepoCard
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.layout.CardGridSpec
import zed.rainxch.core.presentation.layout.rememberWidthCappedStaggeredCells
import zed.rainxch.core.presentation.locals.LocalScrollbarEnabled
import zed.rainxch.core.presentation.model.DiscoveryRepositoryUi
import zed.rainxch.core.presentation.utils.arrowKeyScroll
import zed.rainxch.search.presentation.SearchAction
import zed.rainxch.search.presentation.SearchState

@Composable
internal fun SearchResultsGrid(
    repos: ImmutableList<DiscoveryRepositoryUi>,
    isGridLayout: Boolean,
    isLoadingMore: Boolean,
    showExplore: Boolean,
    exploreStatus: SearchState.ExploreStatus,
    listState: LazyStaggeredGridState,
    edgeInset: Dp,
    bottomPadding: Dp,
    onAction: (SearchAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isScrollbarEnabled = LocalScrollbarEnabled.current
    val maxCardWidth = if (isGridLayout) CardGridSpec.CompactMaxCardWidth else CardGridSpec.InfoMaxCardWidth
    ScrollbarContainer(
        gridState = listState,
        enabled = isScrollbarEnabled,
        modifier = modifier.fillMaxSize(),
    ) {
        LazyVerticalStaggeredGrid(
            columns =
                rememberWidthCappedStaggeredCells(
                    contentPadding = PaddingValues(start = edgeInset, end = edgeInset),
                    maxCardWidth = maxCardWidth,
                ),
            state = listState,
            verticalItemSpacing = CardGridSpec.GridItemSpacing,
            horizontalArrangement = CardGridSpec.GridArrangement,
            contentPadding =
                PaddingValues(
                    top = 12.dp,
                    bottom = 88.dp + bottomPadding,
                ),
            modifier =
                Modifier
                    .fillMaxSize()
                    .arrowKeyScroll(listState, autoFocus = false),
        ) {
            items(
                items = repos,
                key = { discoveryRepository -> discoveryRepository.repository.id },
            ) { discoveryRepository ->
                DiscoveryRepoCard(
                    discoveryRepositoryUi = discoveryRepository,
                    onClick = { onAction(SearchAction.OnRepositoryClick(discoveryRepository.repository)) },
                    onShareClick = { onAction(SearchAction.OnShareClick(discoveryRepository.repository)) },
                    onHideClick = { onAction(SearchAction.OnHideRepository(discoveryRepository.repository)) },
                    onToggleSeenClick = {
                        if (discoveryRepository.isSeen) {
                            onAction(SearchAction.OnMarkAsUnseen(discoveryRepository.repository.id))
                        } else {
                            onAction(SearchAction.OnMarkAsSeen(discoveryRepository.repository))
                        }
                    },
                    compact = isGridLayout,
                    modifier = Modifier.animateItem(),
                )
            }

            if (isLoadingMore) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        KomiCircularProgress(
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            if (showExplore) {
                item(span = StaggeredGridItemSpan.FullLine) {
                    ExploreFromGithubButton(
                        status = exploreStatus,
                        onExplore = { onAction(SearchAction.ExploreFromGithub) },
                    )
                }
            }
        }
    }
}
