package zed.rainxch.search.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.components.buttons.KomiFab
import zed.rainxch.core.presentation.components.overlays.KomiToastState
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.layout.withoutBottom
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.open_github_link
import zed.rainxch.githubstore.core.presentation.res.results_found
import zed.rainxch.search.presentation.components.ActiveFiltersStrip
import zed.rainxch.search.presentation.components.ClipboardBanner
import zed.rainxch.search.presentation.components.DetectedLinksSection
import zed.rainxch.search.presentation.components.HiddenBySeenMessage
import zed.rainxch.search.presentation.components.LanguageFilterBottomSheet
import zed.rainxch.search.presentation.components.NoResultsMessage
import zed.rainxch.search.presentation.components.PlatformPicker
import zed.rainxch.search.presentation.components.SearchErrorMessage
import zed.rainxch.search.presentation.components.SearchFiltersSheet
import zed.rainxch.search.presentation.components.SearchHistorySection
import zed.rainxch.search.presentation.components.SearchLoadMoreEffects
import zed.rainxch.search.presentation.components.SearchLoadingMessage
import zed.rainxch.search.presentation.components.SearchResultsGrid
import zed.rainxch.search.presentation.components.SearchTopbar
import zed.rainxch.search.presentation.components.SearchingUnseenMessage
import zed.rainxch.search.presentation.components.SortByBottomSheet
import zed.rainxch.search.presentation.model.ProgrammingLanguageUi
import zed.rainxch.search.presentation.model.SortByUi

private val GridEdgeInset = 12.dp

@Composable
fun SearchRoot(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (repoId: Long, owner: String, repo: String, sourceHost: String?) -> Unit,
    onNavigateToDetailsFromLink: (owner: String, repo: String) -> Unit,
    onNavigateToDeveloperProfile: (username: String) -> Unit,
    viewModel: SearchViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val toastState = rememberKomiToastState()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is SearchEvent.OnMessage -> {
                scope.launch {
                    toastState.show(event.message)
                }
            }

            is SearchEvent.NavigateToRepo -> {
                onNavigateToDetailsFromLink(event.owner, event.repo)
            }
        }
    }

    SearchScreen(
        state = state,
        toastState = toastState,
        onAction = { action ->
            when (action) {
                is SearchAction.OnRepositoryClick -> {
                    onNavigateToDetails(
                        action.repository.id,
                        action.repository.owner.login,
                        action.repository.name,
                        action.repository.sourceHost,
                    )
                }

                SearchAction.OnNavigateBackClick -> {
                    onNavigateBack()
                }

                is SearchAction.OnRepositoryDeveloperClick -> {
                    onNavigateToDeveloperProfile(action.username)
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        },
    )

    SearchSheets(
        state = state,
        onAction = viewModel::onAction,
    )
}

@Composable
private fun SearchSheets(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
) {
    if (state.isFiltersSheetVisible) {
        SearchFiltersSheet(
            selectedSource = state.selectedSource,
            availableSources = state.availableSources,
            selectedPlatform = state.selectedSearchPlatform,
            selectedLanguage = state.selectedLanguage,
            selectedSortBy = state.selectedSortBy,
            onSourceSelect = { onAction(SearchAction.OnSourceSelected(it)) },
            onPlatformSelect = { onAction(SearchAction.OnPlatformTypeSelected(it)) },
            onOpenLanguagePicker = {
                onAction(SearchAction.OnToggleFiltersSheet)
                onAction(SearchAction.OnToggleLanguageSheetVisibility)
            },
            onOpenSortPicker = {
                onAction(SearchAction.OnToggleFiltersSheet)
                onAction(SearchAction.OnToggleSortByDialogVisibility)
            },
            onReset = {
                onAction(SearchAction.OnLanguageSelected(ProgrammingLanguageUi.All))
                onAction(SearchAction.OnPlatformTypeSelected(DiscoveryPlatform.All))
                onAction(SearchAction.OnSortBySelected(SortByUi.BestMatch))
            },
            onDismiss = {
                onAction(SearchAction.OnToggleFiltersSheet)
            },
        )
    }

    if (state.isLanguageSheetVisible) {
        LanguageFilterBottomSheet(
            selectedLanguage = state.selectedLanguage,
            onLanguageSelect = { language ->
                onAction(SearchAction.OnLanguageSelected(language))
            },
            onDismissRequest = {
                onAction(SearchAction.OnToggleLanguageSheetVisibility)
            },
        )
    }

    if (state.isSortByDialogVisible) {
        SortByBottomSheet(
            selectedSortBy = state.selectedSortBy,
            selectedSortOrder = state.selectedSortOrder,
            onSortBySelect = { sortBy ->
                onAction(SearchAction.OnSortBySelected(sortBy))
            },
            onSortOrderSelect = { sortOrder ->
                onAction(SearchAction.OnSortOrderSelected(sortOrder))
            },
            onDismissRequest = {
                onAction(SearchAction.OnToggleSortByDialogVisibility)
            },
        )
    }
}

@Composable
private fun SearchScreen(
    state: SearchState,
    toastState: KomiToastState,
    onAction: (SearchAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyStaggeredGridState()

    SearchLoadMoreEffects(
        state = state,
        listState = listState,
        onAction = onAction,
    )

    LaunchedEffect(Unit) {
        if (state.query.isEmpty()) {
            focusRequester.requestFocus()
        }
    }

    KomiScaffold(
        modifier = modifier,
        topBar = {
            SearchTopbar(
                query = state.query,
                activeFilterCount = state.activeFilterCount,
                focusRequester = focusRequester,
                onAction = onAction,
            )
        },
        toastState = toastState,
        floatingActionButton = {
            KomiFab(
                onClick = {
                    onAction(SearchAction.OnFabClick)
                },
                icon = Icons.Default.Link,
                contentDescription = stringResource(Res.string.open_github_link),
                label = stringResource(Res.string.open_github_link),
            )
        },
    ) { innerPadding ->
        SearchBody(
            state = state,
            listState = listState,
            innerPadding = innerPadding,
            onAction = onAction,
        )
    }
}

@Composable
private fun SearchBody(
    state: SearchState,
    listState: LazyStaggeredGridState,
    innerPadding: PaddingValues,
    onAction: (SearchAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize().padding(innerPadding.withoutBottom()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .padding(horizontal = GridEdgeInset),
        ) {
            AnimatedVisibility(
                visible = state.isClipboardBannerVisible && state.clipboardLinks.isNotEmpty(),
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
            ) {
                ClipboardBanner(
                    links = state.clipboardLinks,
                    onOpenLink = { link ->
                        onAction(SearchAction.OpenGithubLink(link.owner, link.repo))
                    },
                    onDismiss = {
                        onAction(SearchAction.DismissClipboardBanner)
                    },
                )
            }

            AnimatedVisibility(
                visible = state.detectedLinks.isNotEmpty(),
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut(),
            ) {
                DetectedLinksSection(
                    links = state.detectedLinks,
                    onOpenLink = { link ->
                        onAction(SearchAction.OpenGithubLink(link.owner, link.repo))
                    },
                )
            }

            PlatformPicker(
                selectedPlatform = state.selectedSearchPlatform,
                onPlatformSelect = { onAction(SearchAction.OnPlatformTypeSelected(it)) },
            )

            ActiveFiltersStrip(
                selectedSource = state.selectedSource,
                selectedLanguage = state.selectedLanguage,
                selectedSortBy = state.selectedSortBy,
                onAction = onAction,
            )

            Spacer(Modifier.height(6.dp))

            if (state.totalCount != null) {
                KomiText(
                    text =
                        stringResource(
                            Res.string.results_found,
                            state.totalCount,
                        ),
                    role = KomiTextRole.LabelSmall,
                    color = LocalPersonality.current.colors.onSurfaceVariant,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                )
            }

            if (state.isHistoryVisible) {
                SearchHistorySection(
                    recentSearches = state.recentSearches,
                    onHistoryItemClick = { query ->
                        onAction(SearchAction.OnHistoryItemClick(query))
                    },
                    onRemoveItem = { query ->
                        onAction(SearchAction.OnRemoveHistoryItem(query))
                    },
                    onClearAll = {
                        onAction(SearchAction.OnClearAllHistory)
                    },
                )
            }

            SearchResultsArea(
                state = state,
                listState = listState,
                bottomPadding = innerPadding.calculateBottomPadding(),
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun SearchResultsArea(
    state: SearchState,
    listState: LazyStaggeredGridState,
    bottomPadding: Dp,
    onAction: (SearchAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        if (state.isLoading && state.repositories.isEmpty()) {
            SearchLoadingMessage()
        }

        if (state.errorMessage != null && state.repositories.isEmpty()) {
            SearchErrorMessage(
                message = state.errorMessage,
                onRetry = { onAction(SearchAction.Retry) },
            )
        }

        if (state.isNoResultsVisible) {
            NoResultsMessage(
                canExplore = state.passthroughAttempted != true,
                exploreStatus = state.exploreStatus,
                onExplore = { onAction(SearchAction.ExploreFromGithub) },
            )
        }

        if (state.areResultsHiddenBySeen && state.hasMorePages) {
            SearchingUnseenMessage()
        }

        if (state.areResultsHiddenBySeen && !state.hasMorePages) {
            HiddenBySeenMessage(onShowAll = { onAction(SearchAction.OnDisableHideSeenForResults) })
        }

        if (state.visibleRepos.isNotEmpty()) {
            SearchResultsGrid(
                repos = state.visibleRepos,
                isGridLayout = state.isGridLayout,
                isLoadingMore = state.isLoadingMore,
                showExplore = !state.isLoading && !state.isLoadingMore && state.query.isNotBlank(),
                exploreStatus = state.exploreStatus,
                listState = listState,
                edgeInset = GridEdgeInset,
                bottomPadding = bottomPadding,
                onAction = onAction,
            )
        }
    }
}

@Preview
@Composable
private fun Preview() {
    PersonalityPreview {
        SearchScreen(
            state = SearchState(),
            toastState = rememberKomiToastState(),
            onAction = { },
        )
    }
}
