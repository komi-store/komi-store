package zed.rainxch.favourites.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.domain.model.repository.FavoriteRepo
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.bars.KomiTopBarSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.overlays.KomiDropdown
import zed.rainxch.core.presentation.components.overlays.KomiMenuEntry
import zed.rainxch.core.presentation.components.overlays.KomiMenuItem
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.components.ScrollbarContainer
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.locals.LocalScrollbarEnabled
import zed.rainxch.core.presentation.utils.arrowKeyScroll
import zed.rainxch.favourites.presentation.components.FavouriteRepositoryItem
import zed.rainxch.favourites.presentation.model.FavouritesSortRule
import zed.rainxch.githubstore.core.presentation.res.*

@Composable
fun FavouritesRoot(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (repo: FavoriteRepo) -> Unit,
    onNavigateToDeveloperProfile: (username: String) -> Unit,
    onNavigateToImportStars: () -> Unit,
    viewModel: FavouritesViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    FavouritesScreen(
        state = state,
        onAction = { action ->
            when (action) {
                FavouritesAction.OnNavigateBackClick -> {
                    onNavigateBack()
                }

                is FavouritesAction.OnRepositoryClick -> {
                    onNavigateToDetails(action.favouriteRepository)
                }

                is FavouritesAction.OnDeveloperProfileClick -> {
                    onNavigateToDeveloperProfile(action.username)
                }

                FavouritesAction.OnImportStarsClick -> {
                    onNavigateToImportStars()
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        },
    )
}

@Composable
fun FavouritesScreen(
    state: FavouritesState,
    onAction: (FavouritesAction) -> Unit,
) {
    KomiScaffold(
        topBar = {
            FavouritesTopbar(
                sortRule = state.sortRule,
                hasRepos = state.favouriteRepositories.isNotEmpty(),
                onAction = onAction,
            )
        },
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            if (state.favouriteRepositories.isNotEmpty()) {
                FavouritesSearchBar(
                    query = state.searchQuery,
                    onQueryChange = { onAction(FavouritesAction.OnSearchChange(it)) },
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                val gridState = rememberLazyStaggeredGridState()
                val isScrollbarEnabled = LocalScrollbarEnabled.current

                ScrollbarContainer(
                    gridState = gridState,
                    enabled = isScrollbarEnabled,
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
                            items = state.filteredRepositories,
                            key = { it.repoId },
                        ) { repo ->
                            FavouriteRepositoryItem(
                                favouriteRepository = repo,
                                onToggleFavouriteClick = {
                                    onAction(FavouritesAction.OnToggleFavorite(repo))
                                },
                                onItemClick = {
                                    onAction(FavouritesAction.OnRepositoryClick(repo))
                                },
                                onDevProfileClick = {
                                    onAction(FavouritesAction.OnDeveloperProfileClick(repo.repoOwner))
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
}

@Composable
private fun FavouritesTopbar(
    sortRule: FavouritesSortRule,
    hasRepos: Boolean,
    onAction: (FavouritesAction) -> Unit,
) {
    KomiTopBar(
        title = stringResource(Res.string.favourites),
        size = KomiTopBarSize.Compact,
        leading = {
            KomiIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.navigate_back),
                onClick = { onAction(FavouritesAction.OnNavigateBackClick) },
                variant = KomiButtonVariant.Tonal,
            )
        },
        actions = {
            KomiIconButton(
                icon = Icons.Filled.PersonAdd,
                contentDescription = stringResource(Res.string.import_stars_entry),
                onClick = { onAction(FavouritesAction.OnImportStarsClick) },
                variant = KomiButtonVariant.Tonal,
            )
            if (hasRepos) {
                val sortContentDescription = stringResource(Res.string.sort_label)
                val sortEntries: ImmutableList<KomiMenuEntry> =
                    FavouritesSortRule.entries
                        .map { rule ->
                            KomiMenuItem(
                                id = rule.name,
                                label = stringResource(
                                    when (rule) {
                                        FavouritesSortRule.RecentlyAdded -> Res.string.sort_recently_added
                                        FavouritesSortRule.NameAsc -> Res.string.sort_name
                                    }
                                ),
                            )
                        }
                        .toImmutableList()
                KomiDropdown(
                    entries = sortEntries,
                    onSelect = { item ->
                        FavouritesSortRule.entries
                            .firstOrNull { it.name == item.id }
                            ?.let { onAction(FavouritesAction.OnSortRuleSelected(it)) }
                    },
                    value = sortRule.name,
                    trigger = { onClick ->
                        KomiIconButton(
                            icon = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = sortContentDescription,
                            onClick = onClick,
                            variant = KomiButtonVariant.Tonal,
                        )
                    },
                )
            }
        },
    )
}

@Composable
private fun FavouritesSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    KomiTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        placeholder = stringResource(Res.string.search_repositories_hint),
        leadingIcon = Icons.Filled.Search,
        trailing = {
            if (query.isNotEmpty()) {
                KomiIcon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.clear_search),
                    modifier = Modifier.clickable { onQueryChange("") },
                )
            }
        },
    )
}

@Preview
@Composable
private fun Preview() {
    PersonalityPreview {
        FavouritesScreen(
            state = FavouritesState(),
            onAction = {},
        )
    }
}
