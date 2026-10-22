package zed.rainxch.search.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.rounded.FilterList
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.badge.KomiBadgedBox
import zed.rainxch.core.presentation.components.badge.KomiBadgeTone
import zed.rainxch.core.presentation.components.badge.KomiBadge
import androidx.compose.material.icons.rounded.Search
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.presentation.components.ScrollbarContainer
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiFab
import zed.rainxch.core.presentation.components.cards.DiscoveryRepoCard
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.overlays.KomiToastState
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.layout.CardGridSpec
import zed.rainxch.core.presentation.layout.rememberWidthCappedStaggeredCells
import zed.rainxch.core.presentation.locals.LocalScrollbarEnabled
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.core.presentation.utils.arrowKeyScroll
import zed.rainxch.core.presentation.utils.toIcon
import zed.rainxch.core.presentation.utils.toLabel
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.bottom_nav_search_title
import zed.rainxch.githubstore.core.presentation.res.clipboard_link_detected
import zed.rainxch.githubstore.core.presentation.res.detected_links
import zed.rainxch.githubstore.core.presentation.res.dismiss
import zed.rainxch.githubstore.core.presentation.res.fetch_more_from_github
import zed.rainxch.githubstore.core.presentation.res.fetching_from_github
import zed.rainxch.githubstore.core.presentation.res.no_more_github_results
import zed.rainxch.githubstore.core.presentation.res.no_repositories_found
import zed.rainxch.githubstore.core.presentation.res.open_github_link
import zed.rainxch.githubstore.core.presentation.res.open_in_app
import zed.rainxch.githubstore.core.presentation.res.results_found
import zed.rainxch.githubstore.core.presentation.res.retry
import zed.rainxch.githubstore.core.presentation.res.search_clear_filter_cd
import zed.rainxch.githubstore.core.presentation.res.search_filters_button
import zed.rainxch.githubstore.core.presentation.res.search_repositories_hint
import zed.rainxch.githubstore.core.presentation.res.search_results_hidden_by_seen_filter
import zed.rainxch.githubstore.core.presentation.res.searching_for_unseen_repos
import zed.rainxch.githubstore.core.presentation.res.show_all_results
import zed.rainxch.search.presentation.components.LanguageFilterBottomSheet
import zed.rainxch.search.presentation.components.SearchFiltersSheet
import zed.rainxch.search.presentation.components.SearchHistorySection
import zed.rainxch.search.presentation.components.SortByBottomSheet
import zed.rainxch.search.presentation.model.ParsedGithubLink
import zed.rainxch.search.presentation.model.ProgrammingLanguageUi
import zed.rainxch.search.presentation.model.SearchSourceUi
import zed.rainxch.search.presentation.model.SortByUi
import zed.rainxch.search.presentation.utils.label
import kotlin.time.Duration.Companion.milliseconds
import zed.rainxch.core.presentation.layout.withoutBottom
import zed.rainxch.core.domain.isDesktop

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

    if (state.isFiltersSheetVisible) {
        SearchFiltersSheet(
            selectedSource = state.selectedSource,
            availableSources = state.availableSources,
            selectedPlatform = state.selectedSearchPlatform,
            selectedLanguage = state.selectedLanguage,
            selectedSortBy = state.selectedSortBy,
            onSourceSelected = { viewModel.onAction(SearchAction.OnSourceSelected(it)) },
            onPlatformSelected = { viewModel.onAction(SearchAction.OnPlatformTypeSelected(it)) },
            onOpenLanguagePicker = {
                viewModel.onAction(SearchAction.OnToggleFiltersSheet)
                viewModel.onAction(SearchAction.OnToggleLanguageSheetVisibility)
            },
            onOpenSortPicker = {
                viewModel.onAction(SearchAction.OnToggleFiltersSheet)
                viewModel.onAction(SearchAction.OnToggleSortByDialogVisibility)
            },
            onReset = {
                viewModel.onAction(SearchAction.OnLanguageSelected(ProgrammingLanguageUi.All))
                viewModel.onAction(SearchAction.OnPlatformTypeSelected(DiscoveryPlatform.All))
                viewModel.onAction(SearchAction.OnSortBySelected(SortByUi.BestMatch))
            },
            onDismiss = {
                viewModel.onAction(SearchAction.OnToggleFiltersSheet)
            },
        )
    }

    if (state.isLanguageSheetVisible) {
        LanguageFilterBottomSheet(
            selectedLanguage = state.selectedLanguage,
            onLanguageSelected = { language ->
                viewModel.onAction(SearchAction.OnLanguageSelected(language))
            },
            onDismissRequest = {
                viewModel.onAction(SearchAction.OnToggleLanguageSheetVisibility)
            },
        )
    }

    if (state.isSortByDialogVisible) {
        SortByBottomSheet(
            selectedSortBy = state.selectedSortBy,
            selectedSortOrder = state.selectedSortOrder,
            onSortBySelected = { sortBy ->
                viewModel.onAction(SearchAction.OnSortBySelected(sortBy))
            },
            onSortOrderSelected = { sortOrder ->
                viewModel.onAction(SearchAction.OnSortOrderSelected(sortOrder))
            },
            onDismissRequest = {
                viewModel.onAction(SearchAction.OnToggleSortByDialogVisibility)
            },
        )
    }
}

@Composable
fun SearchScreen(
    state: SearchState,
    toastState: KomiToastState,
    onAction: (SearchAction) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyStaggeredGridState()

    val currentState by rememberUpdatedState(state)

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val visibleItems = layoutInfo.visibleItemsInfo

            if (totalItems == 0 ||
                currentState.isLoadingMore ||
                currentState.isLoading ||
                !currentState.hasMorePages
            ) {
                return@derivedStateOf false
            }

            val lastVisibleItem = visibleItems.maxByOrNull { it.index } ?: return@derivedStateOf false
            val viewportEndOffset = layoutInfo.viewportEndOffset

            val hasEmptySpaceAtBottom =
                lastVisibleItem.index == totalItems - 1 &&
                        lastVisibleItem.offset.y + lastVisibleItem.size.height < viewportEndOffset

            val threshold = (totalItems * 0.8f).toInt()
            val isNearEnd = lastVisibleItem.index >= threshold

            isNearEnd || hasEmptySpaceAtBottom
        }
    }

    val currentOnAction by rememberUpdatedState(onAction)

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            currentOnAction(SearchAction.LoadMore)
        }
    }

    LaunchedEffect(
        state.repositories.size,
        state.visibleRepos.size,
        state.isHideSeenEnabled,
        state.hasMorePages,
        state.isLoadingMore,
        state.isLoading,
    ) {
        if (state.repositories.isNotEmpty() &&
            state.visibleRepos.isEmpty() &&
            state.isHideSeenEnabled &&
            state.hasMorePages &&
            !state.isLoadingMore &&
            !state.isLoading
        ) {
            currentOnAction(SearchAction.LoadMore)
        }
    }

    LaunchedEffect(listState.layoutInfo.totalItemsCount, listState.layoutInfo.viewportEndOffset) {
        val layoutInfo = listState.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        val lastVisible = visibleItems.maxByOrNull { it.index }

        if (lastVisible != null &&
            layoutInfo.totalItemsCount > 0 &&
            !state.isLoadingMore &&
            !state.isLoading &&
            state.hasMorePages
        ) {
            val hasEmptySpace =
                lastVisible.index == layoutInfo.totalItemsCount - 1 &&
                        lastVisible.offset.y + lastVisible.size.height < layoutInfo.viewportEndOffset

            if (hasEmptySpace) {
                delay(100.milliseconds)
                currentOnAction(SearchAction.LoadMore)
            }
        }
    }

    LaunchedEffect(Unit) {
        if (state.query.isEmpty()) {
            focusRequester.requestFocus()
        }
    }

    KomiScaffold(
        topBar = {
            SearchTopbar(
                onAction = onAction,
                state = state,
                focusRequester = focusRequester,
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
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding.withoutBottom()),
            contentAlignment = Alignment.TopCenter,
        ) {
            val gridEdgeInset = 12.dp
            Column(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .padding(horizontal = gridEdgeInset),
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
                    state = state,
                    onAction = onAction
                )

                ActiveFiltersStrip(
                    state = state,
                    onAction = onAction
                )

                Spacer(Modifier.height(6.dp))

                if (state.totalCount != null) {
                    KomiText(
                        text = stringResource(
                            Res.string.results_found,
                            state.totalCount,
                        ),
                        role = KomiTextRole.LabelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp),
                    )
                }

                if (state.query.isBlank() &&
                    state.repositories.isEmpty() &&
                    state.recentSearches.isNotEmpty() &&
                    !state.isLoading
                ) {
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

                Box(Modifier.fillMaxSize()) {
                    if (state.isLoading && state.repositories.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().imePadding(),
                            contentAlignment = Alignment.Center,
                        ) {
                            KomiCircularProgress()
                        }
                    }

                    if (state.errorMessage != null && state.repositories.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                KomiText(
                                    text = state.errorMessage,
                                    role = KomiTextRole.Body,
                                )

                                Spacer(Modifier.height(8.dp))

                                KomiButton(
                                    label = stringResource(Res.string.retry),
                                    onClick = {
                                        onAction(SearchAction.Retry)
                                    },
                                )
                            }
                        }
                    }

                    if (!state.isLoading &&
                        !state.isLoadingMore &&
                        state.errorMessage == null &&
                        state.repositories.isEmpty() &&
                        state.query.isNotBlank() &&
                        !state.hasMorePages
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                KomiText(
                                    text = stringResource(Res.string.no_repositories_found),
                                    role = KomiTextRole.Body,
                                )

                                if (state.passthroughAttempted != true) {
                                    Spacer(Modifier.height(8.dp))
                                    ExploreFromGithubButton(
                                        status = state.exploreStatus,
                                        onExplore = { onAction(SearchAction.ExploreFromGithub) },
                                    )
                                }
                            }
                        }
                    }

                    if (state.repositories.isNotEmpty() &&
                        state.visibleRepos.isEmpty() &&
                        state.isHideSeenEnabled &&
                        state.hasMorePages
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                KomiCircularProgress()

                                Spacer(Modifier.height(8.dp))

                                KomiText(
                                    text = stringResource(Res.string.searching_for_unseen_repos),
                                    role = KomiTextRole.Body,
                                    color = colors.outline,
                                )
                            }
                        }
                    }

                    if (state.repositories.isNotEmpty() &&
                        state.visibleRepos.isEmpty() &&
                        state.isHideSeenEnabled &&
                        !state.hasMorePages
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                KomiText(
                                    text = stringResource(Res.string.search_results_hidden_by_seen_filter),
                                    role = KomiTextRole.Body,
                                )

                                Spacer(Modifier.height(8.dp))

                                KomiButton(
                                    label = stringResource(Res.string.show_all_results),
                                    onClick = {
                                        onAction(SearchAction.OnDisableHideSeenForResults)
                                    },
                                )
                            }
                        }
                    }

                    if (state.visibleRepos.isNotEmpty()) {
                        val isScrollbarEnabled = LocalScrollbarEnabled.current
                        ScrollbarContainer(
                            gridState = listState,
                            enabled = isScrollbarEnabled,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            LazyVerticalStaggeredGrid(
                                columns =
                                    rememberWidthCappedStaggeredCells(
                                        contentPadding =
                                            PaddingValues(start = gridEdgeInset, end = gridEdgeInset),
                                        maxCardWidth = if (state.isGridLayout) CardGridSpec.CompactMaxCardWidth else CardGridSpec.InfoMaxCardWidth,
                                    ),
                                state = listState,
                                verticalItemSpacing = CardGridSpec.GridItemSpacing,
                                horizontalArrangement = CardGridSpec.GridArrangement,
                                contentPadding =
                                    PaddingValues(
                                        top = 12.dp,
                                        bottom = 88.dp + innerPadding.calculateBottomPadding(),
                                    ),
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .arrowKeyScroll(listState, autoFocus = false),
                            ) {
                                items(
                                    items = state.visibleRepos,
                                    key = { discoveryRepository -> discoveryRepository.repository.id },
                                ) { discoveryRepository ->
                                    DiscoveryRepoCard(
                                        discoveryRepositoryUi = discoveryRepository,
                                        onClick = {
                                            onAction(
                                                SearchAction.OnRepositoryClick(
                                                    discoveryRepository.repository
                                                )
                                            )
                                        },
                                        onShareClick = {
                                            onAction(SearchAction.OnShareClick(discoveryRepository.repository))
                                        },
                                        onHideClick = {
                                            onAction(
                                                SearchAction.OnHideRepository(
                                                    discoveryRepository.repository
                                                )
                                            )
                                        },
                                        onToggleSeen = {
                                            if (discoveryRepository.isSeen) {
                                                onAction(
                                                    SearchAction.OnMarkAsUnseen(
                                                        discoveryRepository.repository.id
                                                    )
                                                )
                                            } else {
                                                onAction(
                                                    SearchAction.OnMarkAsSeen(
                                                        discoveryRepository.repository
                                                    )
                                                )
                                            }
                                        },
                                        compact = state.isGridLayout,
                                        modifier = Modifier.animateItem(),
                                    )
                                }

                                if (state.isLoadingMore) {
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

                                if (!state.isLoading && !state.isLoadingMore && state.query.isNotBlank()) {
                                    item(span = StaggeredGridItemSpan.FullLine) {
                                        ExploreFromGithubButton(
                                            status = state.exploreStatus,
                                            onExplore = { onAction(SearchAction.ExploreFromGithub) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlatformPicker(
    state: SearchState,
    onAction: (SearchAction) -> Unit
) {
    val colors = LocalPersonality.current.colors
    val shape = RoundedCornerShape(LocalPersonality.current.shape.cornerSmall)
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(DiscoveryPlatform.entries) { platform ->
            Row(
                modifier = Modifier
                    .clip(shape)
                    .background(
                        if (state.selectedSearchPlatform == platform) {
                            colors.primaryTint
                        } else colors.surface,
                        shape = shape
                    )
                    .border(1.dp, colors.primary.copy(alpha = 0.4f), shape)
                    .clickable(onClick = {
                        onAction(SearchAction.OnPlatformTypeSelected(platform))
                    })
                    .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val icon = platform.toIcon()
                if (icon != null) {
                    KomiIcon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = colors.primary,
                    )
                }

                KomiText(
                    text = platform.toLabel(),
                    role = KomiTextRole.LabelSmall,
                    color = colors.primary,
                )
            }
        }
    }
}

@Composable
private fun ClipboardBanner(
    links: ImmutableList<ParsedGithubLink>,
    onOpenLink: (ParsedGithubLink) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    KomiSurface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KomiText(
                    text = stringResource(Res.string.clipboard_link_detected),
                    role = KomiTextRole.Overline,
                    color = colors.onSurfaceVariant,
                )

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(shape.cornerSmall))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    KomiIcon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.dismiss),
                        modifier = Modifier.size(16.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            links.forEach { link ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(shape.cornerSmall))
                        .clickable { onOpenLink(link) }
                        .padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    KomiIcon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = colors.primary,
                    )

                    KomiText(
                        text = "${link.owner}/${link.repo}",
                        role = KomiTextRole.Label,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )

                    KomiIcon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = stringResource(Res.string.open_in_app),
                        modifier = Modifier.size(16.dp),
                        tint = colors.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DetectedLinksSection(
    links: ImmutableList<ParsedGithubLink>,
    onOpenLink: (ParsedGithubLink) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    ) {
        KomiText(
            text = stringResource(Res.string.detected_links),
            role = KomiTextRole.Overline,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )

        links.forEach { link ->
            KomiSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                onClick = { onOpenLink(link) },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    KomiIcon(
                        imageVector = Icons.Default.Link,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = colors.primary,
                    )

                    KomiText(
                        text = "${link.owner}/${link.repo}",
                        role = KomiTextRole.Label,
                        color = colors.onSurface,
                        modifier = Modifier.weight(1f),
                    )

                    KomiText(
                        text = stringResource(Res.string.open_in_app),
                        role = KomiTextRole.Overline,
                        color = colors.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchTopbar(
    onAction: (SearchAction) -> Unit,
    state: SearchState,
    focusRequester: FocusRequester,
) {
    KomiTopBar(
        title = stringResource(Res.string.bottom_nav_search_title),
        // The Desktop sidebar holds Search; on a phone it is pushed over a tab.
        onBack = if (isDesktop()) null else ({ onAction(SearchAction.OnNavigateBackClick) }),
        search = {
            KomiTextField(
                value = state.query,
                onValueChange = { value ->
                    onAction(SearchAction.OnSearchChange(value))
                },
                placeholder = stringResource(Res.string.search_repositories_hint),
                leadingIcon = Icons.Rounded.Search,
                onClear = { onAction(SearchAction.OnClearClick) },
                keyboardType = KeyboardType.Text,
                onCommit = { onAction(SearchAction.OnSearchImeClick) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                multiline = false
            )
        },
        actions = {
            FiltersButton(
                activeCount = state.activeFilterCount,
                onClick = { onAction(SearchAction.OnToggleFiltersSheet) },
            )
        },
    )
}

// The search field's filters on the core icon button (a press morph and tooltip in Classic, the
// ink box in Manga): filled once a filter is on, with how many as a badge.
@Composable
private fun FiltersButton(
    activeCount: Int,
    onClick: () -> Unit,
) {
    KomiBadgedBox(
        badge = {
            if (activeCount > 0) KomiBadge(count = activeCount, tone = KomiBadgeTone.Neutral)
        },
    ) {
        KomiIconButton(
            icon = Icons.Rounded.FilterList,
            contentDescription = stringResource(Res.string.search_filters_button),
            onClick = onClick,
            variant = if (activeCount > 0) KomiButtonVariant.Primary else KomiButtonVariant.Tonal,
            size = KomiIconButtonSize.Lg,
        )
    }
}

@Composable
private fun ActiveFiltersStrip(
    state: SearchState,
    onAction: (SearchAction) -> Unit,
) {
    val languageLabel = stringResource(state.selectedLanguage.label())
    val sortByLabel = stringResource(state.selectedSortBy.label())
    val items = buildList {
        if (state.selectedSource != SearchSourceUi.GitHub) {
            add(
                Triple(
                    first = state.selectedSource.label,
                    second = {
                        onAction(SearchAction.OnSourceSelected(SearchSourceUi.GitHub))
                    },
                    third = null
                )
            )
        }
        if (state.selectedLanguage != ProgrammingLanguageUi.All) {
            add(
                Triple(
                    first = languageLabel,
                    second = {
                        onAction(SearchAction.OnLanguageSelected(ProgrammingLanguageUi.All))
                    },
                    third = Icons.Outlined.KeyboardArrowDown,
                ),
            )
        }
        if (state.selectedSortBy != SortByUi.BestMatch) {
            add(
                Triple(
                    first = sortByLabel,
                    second = {
                        onAction(SearchAction.OnSortBySelected(SortByUi.BestMatch))
                    },
                    third = Icons.AutoMirrored.Filled.Sort,
                ),
            )
        }
    }
    if (items.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { (label, onRemove, leading) ->
            ActiveFilterChip(
                label = label,
                leadingIcon = leading,
                onRemove = onRemove
            )
        }
    }
}

@Composable
private fun ActiveFilterChip(
    label: String,
    leadingIcon: ImageVector?,
    onRemove: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = RoundedCornerShape(LocalPersonality.current.shape.cornerSmall)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(colors.primaryTint, shape)
            .border(1.dp, colors.primary.copy(alpha = 0.4f), shape)
            .clickable(onClick = onRemove)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingIcon != null) {
            KomiIcon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = colors.primary,
            )
        }

        KomiText(
            text = label,
            role = KomiTextRole.LabelSmall,
            color = colors.primary,
        )

        KomiIcon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(Res.string.search_clear_filter_cd),
            modifier = Modifier.size(14.dp),
            tint = colors.primary,
        )
    }
}

@Composable
private fun ExploreFromGithubButton(
    status: SearchState.ExploreStatus,
    onExplore: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            SearchState.ExploreStatus.IDLE -> {
                KomiButton(
                    onClick = onExplore,
                    label = stringResource(Res.string.fetch_more_from_github),
                    variant = KomiButtonVariant.Outline,
                    leadingIcon = Icons.Outlined.TravelExplore,
                )
            }

            SearchState.ExploreStatus.LOADING -> {
                KomiButton(
                    onClick = {},
                    label = stringResource(Res.string.fetching_from_github),
                    variant = KomiButtonVariant.Outline,
                    enabled = false,
                    loading = true,
                )
            }

            SearchState.ExploreStatus.EXHAUSTED -> {
                KomiText(
                    text = stringResource(Res.string.no_more_github_results),
                    role = KomiTextRole.Body,
                    color = LocalPersonality.current.colors.outline,
                )
            }
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
            onAction = { }
        )
    }
}
