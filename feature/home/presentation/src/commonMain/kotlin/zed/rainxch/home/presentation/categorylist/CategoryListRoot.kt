package zed.rainxch.home.presentation.categorylist

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.home_section_hot_releases
import zed.rainxch.githubstore.core.presentation.res.home_section_most_popular
import zed.rainxch.githubstore.core.presentation.res.home_section_trending_now
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.bars.KomiTopBarSize
import zed.rainxch.core.presentation.components.cards.DiscoveryRepoCard
import zed.rainxch.core.presentation.components.cards.KomiRepoCardFeed
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.progress.KomiLoadingIndicator
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.home.domain.model.HomeCategory
import zed.rainxch.home.presentation.model.toDiscoveryUi
import zed.rainxch.core.presentation.layout.CardGridSpec
import zed.rainxch.core.presentation.layout.rememberWidthCappedStaggeredCells

@Composable
fun CategoryListRoot(
    category: HomeCategory,
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (Long) -> Unit,
    viewModel: CategoryListViewModel = koinViewModel { parametersOf(category) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is CategoryListEvent.NavigateToDetails -> onNavigateToDetails(event.repoId)
        }
    }

    CategoryListScreen(
        state = state,
        onAction = viewModel::onAction,
        onBack = onNavigateBack,
    )
}

@Composable
private fun CategoryListScreen(
    state: CategoryListState,
    onAction: (CategoryListAction) -> Unit,
    onBack: () -> Unit,
) {
    val listState = rememberLazyStaggeredGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val total = listState.layoutInfo.totalItemsCount
            val lastVisible = listState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
            total > 0 && lastVisible >= total - 4
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !state.isLoadingMore && state.hasMorePages) {
            onAction(CategoryListAction.OnLoadMore)
        }
    }

    KomiScaffold(
        topBar = {
            KomiTopBar(
                title = stringResource(
                    when (state.category) {
                        HomeCategory.HOT_RELEASE -> Res.string.home_section_hot_releases
                        HomeCategory.TRENDING -> Res.string.home_section_trending_now
                        HomeCategory.MOST_POPULAR -> Res.string.home_section_most_popular
                    },
                ),
                size = KomiTopBarSize.Compact,
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.isLoading && state.cards.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    KomiLoadingIndicator()
                }
            } else {
                val gridPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                LazyVerticalStaggeredGrid(
                    columns =
                        rememberWidthCappedStaggeredCells(
                            contentPadding = gridPadding,
                            maxCardWidth = if (state.isGridLayout) CardGridSpec.ChartCompactMaxCardWidth else CardGridSpec.InfoMaxCardWidth,
                        ),
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = gridPadding,
                    verticalItemSpacing = CardGridSpec.GridItemSpacing,
                    horizontalArrangement = CardGridSpec.GridArrangement,
                ) {
                    itemsIndexed(
                        items = state.cards,
                        key = { _, card -> card.id },
                    ) { index, card ->
                        DiscoveryRepoCard(
                            discoveryRepositoryUi = card.toDiscoveryUi(),
                            onClick = { onAction(CategoryListAction.OnRepoClick(card.id)) },
                            onShareClick = { },
                            rank = index + 1,
                            feed = if (state.category == HomeCategory.MOST_POPULAR) {
                                KomiRepoCardFeed.Popular
                            } else {
                                KomiRepoCardFeed.Plain
                            },
                            compact = state.isGridLayout,
                        )
                    }

                    if (state.isLoadingMore) {
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                KomiCircularProgress(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}