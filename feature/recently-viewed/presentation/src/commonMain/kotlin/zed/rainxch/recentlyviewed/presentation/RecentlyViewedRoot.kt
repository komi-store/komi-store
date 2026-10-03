package zed.rainxch.recentlyviewed.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.domain.model.repository.SeenRepo
import zed.rainxch.core.presentation.components.ScrollbarContainer
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.bars.KomiTopBarSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.locals.LocalScrollbarEnabled
import zed.rainxch.core.presentation.utils.arrowKeyScroll
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.recentlyviewed.presentation.components.RecentlyViewedItem

@Composable
fun RecentlyViewedRoot(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (repo: SeenRepo) -> Unit,
    onNavigateToDeveloperProfile: (username: String) -> Unit,
    viewModel: RecentlyViewedViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    RecentlyViewedScreen(
        state = state,
        onAction = { action ->
            when (action) {
                RecentlyViewedAction.OnNavigateBackClick -> {
                    onNavigateBack()
                }

                is RecentlyViewedAction.OnRepositoryClick -> {
                    onNavigateToDetails(action.repo)
                }

                is RecentlyViewedAction.OnDeveloperProfileClick -> {
                    onNavigateToDeveloperProfile(action.username)
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        },
    )
}

@Composable
fun RecentlyViewedScreen(
    state: RecentlyViewedState,
    onAction: (RecentlyViewedAction) -> Unit,
) {
    KomiScaffold(
        topBar = {
            KomiTopBar(
                title = stringResource(Res.string.recently_viewed),
                size = KomiTopBarSize.Compact,
                leading = {
                    KomiIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(Res.string.navigate_back),
                        onClick = { onAction(RecentlyViewedAction.OnNavigateBackClick) },
                        variant = KomiButtonVariant.Tonal,
                    )
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            val gridState = rememberLazyStaggeredGridState()

            ScrollbarContainer(
                gridState = gridState,
                enabled = LocalScrollbarEnabled.current,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyVerticalStaggeredGrid(
                    state = gridState,
                    columns =
                        StaggeredGridCells.Adaptive(
                            350.dp,
                        ),
                    verticalItemSpacing = 12.dp,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    modifier = Modifier.fillMaxSize().arrowKeyScroll(gridState, autoFocus = true),
                ) {
                    items(
                        items = state.repositories,
                        key = { it.repoId },
                    ) { repo ->
                        RecentlyViewedItem(
                            repo = repo,
                            onItemClick = {
                                onAction(RecentlyViewedAction.OnRepositoryClick(repo))
                            },
                            onRemoveClick = {
                                onAction(RecentlyViewedAction.OnRemoveFromHistory(repo))
                            },
                            onDevProfileClick = {
                                onAction(RecentlyViewedAction.OnDeveloperProfileClick(repo.repoOwner))
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }

            if (state.isLoading) {
                KomiCircularProgress(
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}
