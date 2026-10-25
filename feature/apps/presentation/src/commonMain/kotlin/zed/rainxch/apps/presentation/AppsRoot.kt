@file:OptIn(ExperimentalTime::class)

package zed.rainxch.apps.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.apps.presentation.components.AdvancedAppSettingsBottomSheet
import zed.rainxch.apps.presentation.components.AppsSectionHeader
import zed.rainxch.apps.presentation.components.AppsTopbar
import zed.rainxch.apps.presentation.components.ImportSummarySheet
import zed.rainxch.apps.presentation.components.LinkAppBottomSheet
import zed.rainxch.apps.presentation.components.PendingDiscardSheet
import zed.rainxch.apps.presentation.components.PendingUninstallSheet
import zed.rainxch.apps.presentation.components.UpdatesBanner
import zed.rainxch.apps.presentation.components.VariantPickerDialog
import zed.rainxch.apps.presentation.import.components.ImportProposalBanner
import zed.rainxch.apps.presentation.model.AppGroup
import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.core.presentation.components.ScrollbarContainer
import zed.rainxch.core.presentation.components.buttons.KomiFab
import zed.rainxch.core.presentation.components.bars.LocalTabCardSlot
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.overlays.KomiToastState
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.progress.KomiLoadingIndicator
import zed.rainxch.core.presentation.components.refresh.KomiPullToRefresh
import zed.rainxch.core.presentation.components.refresh.drivesPullToRefresh
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.layout.CardGridSpec
import zed.rainxch.core.presentation.layout.rememberWidthCappedStaggeredCells
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.locals.LocalScrollbarEnabled
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.core.presentation.utils.arrowKeyScroll
import zed.rainxch.core.presentation.utils.formatLastChecked
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.add_by_link
import zed.rainxch.githubstore.core.presentation.res.apps_section_in_progress
import zed.rainxch.githubstore.core.presentation.res.apps_section_pending_installs
import zed.rainxch.githubstore.core.presentation.res.apps_section_up_to_date
import zed.rainxch.githubstore.core.presentation.res.cancel
import zed.rainxch.githubstore.core.presentation.res.checking_for_updates
import zed.rainxch.githubstore.core.presentation.res.confirm_discard_pending_message
import zed.rainxch.githubstore.core.presentation.res.confirm_discard_pending_title
import zed.rainxch.githubstore.core.presentation.res.confirm_uninstall_message
import zed.rainxch.githubstore.core.presentation.res.confirm_uninstall_title
import zed.rainxch.githubstore.core.presentation.res.discard_pending_install
import zed.rainxch.githubstore.core.presentation.res.last_checked
import zed.rainxch.githubstore.core.presentation.res.no_apps_found
import zed.rainxch.githubstore.core.presentation.res.search_your_apps
import zed.rainxch.githubstore.core.presentation.res.uninstall
import kotlin.time.ExperimentalTime
import zed.rainxch.core.presentation.layout.withoutBottom
import zed.rainxch.apps.presentation.components.LibraryAppRow
import zed.rainxch.apps.presentation.components.LibraryDownloadRow
import zed.rainxch.apps.presentation.components.LibraryGroup
import zed.rainxch.apps.presentation.components.LibraryGroupDivider

@Composable
fun AppsRoot(
    onNavigateBack: () -> Unit,
    onNavigateToRepo: (repoId: Long, sourceHost: String?, owner: String?, repo: String?, packageName: String?) -> Unit,
    onNavigateToExternalImport: () -> Unit,
    onNavigateToStarredPicker: () -> Unit,
    viewModel: AppsViewModel = koinViewModel(),
    state: AppsState,
) {
    val toastState = rememberKomiToastState()
    val coroutineScope = rememberCoroutineScope()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    viewModel.onAction(AppsAction.OnLifecycleResume)
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is AppsEvent.NavigateToRepo -> {
                onNavigateToRepo(event.repoId, event.sourceHost, event.owner, event.repo, event.packageName)
            }

            is AppsEvent.ShowError -> {
                coroutineScope.launch {
                    toastState.danger(event.message)
                }
            }

            is AppsEvent.ShowSuccess -> {
                coroutineScope.launch {
                    toastState.success(event.message)
                }
            }

            AppsEvent.NavigateToExternalImport -> {
                onNavigateToExternalImport()
            }
        }
    }

    AppsScreen(
        state = state,
        onAction = { action ->
            when (action) {
                AppsAction.OnNavigateBackClick -> {
                    onNavigateBack()
                }

                AppsAction.OnAddFromStarredClick -> {
                    onNavigateToStarredPicker()
                }

                else -> {
                    viewModel.onAction(action)
                }
            }
        },
        toastState = toastState,
    )

    if (state.showLinkSheet) {
        LinkAppBottomSheet(
            state = state,
            onAction = viewModel::onAction,
        )
    }

    if (state.advancedSettingsApp != null) {
        AdvancedAppSettingsBottomSheet(
            state = state,
            onAction = viewModel::onAction,
        )
    }

    if (state.variantPickerApp != null) {
        VariantPickerDialog(
            state = state,
            onAction = viewModel::onAction,
        )
    }

    state.importSummary?.let { summary ->
        ImportSummarySheet(
            summary = summary,
            expandedBuckets = state.expandedImportBuckets,
            onToggleBucket = { viewModel.onAction(AppsAction.OnToggleImportSummaryBucket(it)) },
            onDismiss = {
                viewModel.onAction(AppsAction.OnDismissImportSummary)
            },
        )
    }

    state.appPendingUninstall?.let { app ->
        PendingUninstallSheet(
            app = app,
            onAction = viewModel::onAction,
        )
    }

    state.appPendingDiscard?.let { app ->
        PendingDiscardSheet(
            app = app,
            onAction = viewModel::onAction
        )
    }
}

@Composable
fun AppsScreen(
    state: AppsState,
    onAction: (AppsAction) -> Unit,
    toastState: KomiToastState,
) {
    val colors = LocalPersonality.current.colors
    // Classic's tab card holds the search field; elsewhere it stays above the list.
    val searchInCard = LocalTabCardSlot.current != null
    KomiScaffold(
        topBar = {
            AppsTopbar(
                onAction = onAction,
                state = state,
                search =
                    if (searchInCard) {
                        { AppsSearchField(query = state.searchQuery, onAction = onAction, modifier = Modifier.fillMaxWidth()) }
                    } else {
                        null
                    },
            )
        },
        floatingActionButton = {
            KomiFab(
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(Res.string.add_by_link),
                onClick = {
                    onAction(AppsAction.OnAddByLinkClick)
                },
            )
        },
        toastState = toastState,
    ) { innerPadding ->
        KomiPullToRefresh(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(AppsAction.OnRefresh) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding.withoutBottom()),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight(),
                ) {
                    if (!searchInCard) {
                        AppsSearchField(
                            query = state.searchQuery,
                            onAction = onAction,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .drivesPullToRefresh()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }

                    if (state.isCheckingForUpdates) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drivesPullToRefresh()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            KomiCircularProgress(
                                modifier = Modifier.size(14.dp),
                            )

                            KomiText(
                                text = stringResource(Res.string.checking_for_updates),
                                role = KomiTextRole.Body,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    } else if (state.lastCheckedTimestamp != null) {
                        KomiText(
                            text =
                                stringResource(
                                    Res.string.last_checked,
                                    formatLastChecked(state.lastCheckedTimestamp),
                                ),
                            role = KomiTextRole.Body,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier
                                .drivesPullToRefresh()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    val listState = rememberLazyStaggeredGridState()
                    KeepAtTopWhenSectionsChange(
                        gridState = listState,
                        sections = listOf(
                            state.showImportProposalBanner,
                            state.pendingApps.isNotEmpty(),
                            state.updateApps.isNotEmpty() || state.isUpdatingAll,
                            state.inProgressDownloads.isNotEmpty(),
                        ),
                    )

                    when {
                        state.isLoading -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                KomiLoadingIndicator()
                            }
                        }

                        state.filteredApps.isEmpty() && state.inProgressDownloads.isEmpty() -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                KomiText(
                                    text = stringResource(Res.string.no_apps_found),
                                    role = KomiTextRole.SectionTitle,
                                    color = colors.onBackground,
                                )
                            }
                        }

                        else -> {
                            val isScrollbarEnabled = LocalScrollbarEnabled.current

                            val onRowSelect: (InstalledAppUi) -> Unit =
                                { app ->
                                    onAction(
                                        AppsAction.OnNavigateToRepo(
                                            repoId = app.repoId,
                                            sourceHost = app.sourceHost,
                                            owner = app.repoOwner,
                                            repo = app.repoName,
                                            packageName = app.packageName,
                                        ),
                                    )
                                }

                            ScrollbarContainer(
                                gridState = listState,
                                enabled = isScrollbarEnabled,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                val appGridPadding =
                                    PaddingValues(
                                        start = 12.dp,
                                        end = 12.dp,
                                        top = 8.dp,
                                        bottom = 88.dp + innerPadding.calculateBottomPadding(),
                                    )
                                val appGridCells =
                                    rememberWidthCappedStaggeredCells(contentPadding = appGridPadding)

                                val installedVersions =
                                    remember(state.apps) {
                                        state.apps.associate {
                                            it.installedApp.packageName to it.installedApp.installedVersion
                                        }
                                    }

                                LazyVerticalStaggeredGrid(
                                    columns = appGridCells,
                                    state = listState,
                                    modifier = Modifier.fillMaxSize().arrowKeyScroll(listState),
                                    contentPadding = appGridPadding,
                                    verticalItemSpacing = CardGridSpec.GridItemSpacing,
                                    horizontalArrangement = CardGridSpec.GridArrangement,
                                ) {
                                    if (state.inProgressDownloads.isNotEmpty()) {
                                        item(
                                            key = "header-in-progress",
                                            span = StaggeredGridItemSpan.FullLine,
                                        ) {
                                            AppsSectionHeader(
                                                title = stringResource(Res.string.apps_section_in_progress),
                                                count = state.inProgressDownloads.size,
                                                isExpanded = true,
                                                collapsible = false,
                                                onToggle = {},
                                            )
                                        }

                                        items(
                                            state.inProgressDownloads,
                                            key = { download -> "transient-${download.id}" },
                                        ) { download ->
                                            val openRepo =
                                                download.repoId?.let { repoId ->
                                                    {
                                                        onAction(
                                                            AppsAction.OnNavigateToRepo(
                                                                repoId = repoId,
                                                                sourceHost = download.sourceHost,
                                                                owner = download.repoOwner,
                                                                repo = download.repoName,
                                                                packageName = download.packageName,
                                                            ),
                                                        )
                                                    }
                                                }
                                            LibraryDownloadRow(
                                                download = download,
                                                installedVersion =
                                                    installedVersions[download.packageName],
                                                onOpenRepo = openRepo,
                                                onPause = {
                                                    onAction(
                                                        AppsAction.OnCancelInProgressDownload(
                                                            download.packageName,
                                                        ),
                                                    )
                                                },
                                                onResume = {
                                                    onAction(
                                                        AppsAction.OnResumeInProgressDownload(
                                                            download.packageName,
                                                        ),
                                                    )
                                                },
                                                onRetry = {
                                                    onAction(
                                                        AppsAction.OnRetryInProgressDownload(
                                                            download.packageName,
                                                        ),
                                                    )
                                                },
                                                onDiscard = {
                                                    onAction(
                                                        AppsAction.OnDiscardInProgressDownload(
                                                            download.packageName,
                                                        ),
                                                    )
                                                },
                                                onInstall = {
                                                    onAction(
                                                        AppsAction.OnInstallInProgressDownload(
                                                            download.packageName,
                                                        ),
                                                    )
                                                },
                                                onDismiss = {
                                                    onAction(
                                                        AppsAction.OnDismissInProgressDownload(
                                                            download.packageName,
                                                        ),
                                                    )
                                                },
                                            )
                                        }
                                    }

                                    if (state.showImportProposalBanner) {
                                        item(key = "external-import-banner", span = StaggeredGridItemSpan.FullLine) {
                                            ImportProposalBanner(
                                                pendingCount = state.pendingExternalImportCount,
                                                onReview = { onAction(AppsAction.OnImportProposalReview) },
                                                onDismiss = { onAction(AppsAction.OnImportProposalDismiss) },
                                            )
                                        }
                                    }

                                    if (state.pendingApps.isNotEmpty()) {
                                        item(key = "header-pending-installs", span = StaggeredGridItemSpan.FullLine) {
                                            AppsSectionHeader(
                                                title = stringResource(Res.string.apps_section_pending_installs),
                                                count = state.pendingApps.size,
                                                isExpanded = true,
                                                collapsible = false,
                                                onToggle = {},
                                            )
                                        }

                                        items(
                                            state.pendingGroups,
                                            key = { group -> "pending-${group.key}" },
                                        ) { group ->
                                            LibraryGroupItem(
                                                group = group,
                                                onAction = onAction,
                                                onOpenRepo = onRowSelect,
                                            )
                                        }
                                    }

                                    if (state.updateApps.isNotEmpty() || state.isUpdatingAll) {
                                        item(key = "updates-banner", span = StaggeredGridItemSpan.FullLine) {
                                            UpdatesBanner(
                                                count = state.updateApps.size,
                                                isExpanded = state.isUpdatesSectionExpanded,
                                                isUpdatingAll = state.isUpdatingAll,
                                                updateAllProgress = state.updateAllProgress,
                                                updateAllEnabled = state.updateAllButtonEnabled,
                                                onUpdateAll = { onAction(AppsAction.OnUpdateAll) },
                                                onCancelUpdateAll = { onAction(AppsAction.OnCancelUpdateAll) },
                                                onToggleExpanded = { onAction(AppsAction.OnToggleUpdatesSection) },
                                            )
                                        }
                                    }

                                    if (state.updateApps.isNotEmpty() && state.isUpdatesSectionExpanded) {
                                        items(
                                            state.updateGroups,
                                            key = { group -> "rich-${group.key}" },
                                        ) { group ->
                                            LibraryGroupItem(
                                                group = group,
                                                onAction = onAction,
                                                onOpenRepo = onRowSelect,
                                            )
                                        }
                                    }

                                    if (state.idleApps.isNotEmpty()) {
                                        item(key = "header-up-to-date", span = StaggeredGridItemSpan.FullLine) {
                                            AppsSectionHeader(
                                                title = stringResource(Res.string.apps_section_up_to_date),
                                                count = state.idleApps.size,
                                                isExpanded = state.isUpToDateSectionExpanded,
                                                collapsible = true,
                                                onToggle = {
                                                    onAction(AppsAction.OnToggleUpToDateSection)
                                                },
                                            )
                                        }

                                        if (state.isUpToDateSectionExpanded) {
                                            items(
                                                state.idleGroups,
                                                key = { group -> "compact-${group.key}" },
                                            ) { group ->
                                                LibraryGroupItem(
                                                    group = group,
                                                    onAction = onAction,
                                                    onOpenRepo = onRowSelect,
                                                )
                                            }
                                        }
                                    }

                                    item(key = "trailing-spacer", span = StaggeredGridItemSpan.FullLine) {
                                        Spacer(Modifier.height(32.dp))
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

// A lazy grid keeps its first visible item in place, so a section that appears above it while
// the user is at the top (an update check finding updates) would land offscreen.
@Composable
private fun AppsSearchField(
    query: String,
    onAction: (AppsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    KomiTextField(
        value = query,
        onValueChange = { onAction(AppsAction.OnSearchChange(it)) },
        leadingIcon = Icons.Rounded.Search,
        clearable = true,
        placeholder = stringResource(Res.string.search_your_apps),
        modifier = modifier,
    )
}

@Composable
private fun KeepAtTopWhenSectionsChange(
    gridState: LazyStaggeredGridState,
    sections: List<Boolean>,
) {
    val previous = remember { arrayOf(sections) }
    SideEffect {
        val atTop = gridState.firstVisibleItemIndex == 0 && gridState.firstVisibleItemScrollOffset == 0
        if (previous[0] != sections && atTop) gridState.requestScrollToItem(0)
        previous[0] = sections
    }
}

// An app on its own card, or several apps from one repo on one card.
@Composable
private fun LibraryGroupItem(
    group: AppGroup,
    onAction: (AppsAction) -> Unit,
    onOpenRepo: (InstalledAppUi) -> Unit,
) {
    val single = group.items.singleOrNull()
    if (single != null) {
        LibraryAppRow(appItem = single, onAction = onAction, onOpenRepo = { onOpenRepo(single.installedApp) })
        return
    }
    val first = group.items.first()
    LibraryGroup(header = first.installedApp, ratingAverage = first.ratingAverage, ratingCount = first.ratingCount) {
        group.items.forEachIndexed { index, appItem ->
            if (index > 0) LibraryGroupDivider()
            LibraryAppRow(
                appItem = appItem,
                onAction = onAction,
                onOpenRepo = { onOpenRepo(appItem.installedApp) },
                framed = false,
            )
        }
    }
}

@Preview
@Composable
private fun Preview() {
    PersonalityPreview {
        AppsScreen(
            state = AppsState(),
            onAction = {},
            toastState = rememberKomiToastState(),
        )
    }
}
