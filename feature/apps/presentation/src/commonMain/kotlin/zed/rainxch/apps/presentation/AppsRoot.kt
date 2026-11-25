@file:OptIn(ExperimentalTime::class)

package zed.rainxch.apps.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.apps.presentation.components.AdvancedAppSettingsBottomSheet
import zed.rainxch.apps.presentation.components.AppsTopbar
import zed.rainxch.apps.presentation.components.ImportSummarySheet
import zed.rainxch.apps.presentation.components.LinkAppBottomSheet
import zed.rainxch.apps.presentation.components.PendingDiscardSheet
import zed.rainxch.apps.presentation.components.PendingUninstallSheet
import zed.rainxch.apps.presentation.components.VariantPickerDialog
import zed.rainxch.core.presentation.components.bars.LocalTabCardSlot
import zed.rainxch.core.presentation.components.buttons.KomiFab
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
import zed.rainxch.core.presentation.layout.withoutBottom
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.core.presentation.utils.formatLastChecked
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.add_by_link
import zed.rainxch.githubstore.core.presentation.res.checking_for_updates
import zed.rainxch.githubstore.core.presentation.res.last_checked
import zed.rainxch.githubstore.core.presentation.res.no_apps_found
import zed.rainxch.githubstore.core.presentation.res.search_your_apps
import kotlin.time.ExperimentalTime

@Composable
fun AppsRoot(
    state: AppsState,
    onNavigateBack: () -> Unit,
    onNavigateToRepo: (repoId: Long, sourceHost: String?, owner: String?, repo: String?, packageName: String?) -> Unit,
    onNavigateToExternalImport: () -> Unit,
    onNavigateToStarredPicker: () -> Unit,
    viewModel: AppsViewModel = koinViewModel(),
) {
    val toastState = rememberKomiToastState()
    val coroutineScope = rememberCoroutineScope()

    OnLifecycleResume { viewModel.onAction(AppsAction.OnLifecycleResume) }

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is AppsEvent.NavigateToRepo -> {
                onNavigateToRepo(event.repoId, event.sourceHost, event.owner, event.repo, event.packageName)
            }

            is AppsEvent.ShowError -> {
                coroutineScope.launch { toastState.danger(event.message) }
            }

            is AppsEvent.ShowSuccess -> {
                coroutineScope.launch { toastState.success(event.message) }
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
                AppsAction.OnNavigateBackClick -> onNavigateBack()
                AppsAction.OnAddFromStarredClick -> onNavigateToStarredPicker()
                else -> viewModel.onAction(action)
            }
        },
        toastState = toastState,
    )

    AppsOverlays(state = state, onAction = viewModel::onAction)
}

@Composable
private fun OnLifecycleResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResume by rememberUpdatedState(onResume)
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) currentOnResume()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
private fun AppsOverlays(
    state: AppsState,
    onAction: (AppsAction) -> Unit,
) {
    if (state.showLinkSheet) {
        LinkAppBottomSheet(state = state, onAction = onAction)
    }

    if (state.advancedSettingsApp != null) {
        AdvancedAppSettingsBottomSheet(state = state, onAction = onAction)
    }

    if (state.variantPickerApp != null) {
        VariantPickerDialog(state = state, onAction = onAction)
    }

    state.importSummary?.let { summary ->
        ImportSummarySheet(
            summary = summary,
            expandedBuckets = state.expandedImportBuckets,
            onToggleBucket = { onAction(AppsAction.OnToggleImportSummaryBucket(it)) },
            onDismiss = { onAction(AppsAction.OnDismissImportSummary) },
        )
    }

    state.appPendingUninstall?.let { app ->
        PendingUninstallSheet(app = app, onAction = onAction)
    }

    state.appPendingDiscard?.let { app ->
        PendingDiscardSheet(app = app, onAction = onAction)
    }
}

@Composable
private fun AppsScreen(
    state: AppsState,
    onAction: (AppsAction) -> Unit,
    toastState: KomiToastState,
    modifier: Modifier = Modifier,
) {
    // Classic's tab card holds the search field; elsewhere it stays above the list.
    val searchInCard = LocalTabCardSlot.current != null
    KomiScaffold(
        modifier = modifier,
        topBar = {
            AppsTopbar(
                onAction = onAction,
                sortRule = state.sortRule,
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
                onClick = { onAction(AppsAction.OnAddByLinkClick) },
            )
        },
        toastState = toastState,
    ) { innerPadding ->
        KomiPullToRefresh(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(AppsAction.OnRefresh) },
            modifier =
                Modifier
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

                    UpdateCheckStatus(
                        isChecking = state.isCheckingForUpdates,
                        lastCheckedTimestamp = state.lastCheckedTimestamp,
                    )

                    AppsContent(
                        state = state,
                        onAction = onAction,
                        bottomInset = innerPadding.calculateBottomPadding(),
                    )
                }
            }
        }
    }
}

@Composable
private fun UpdateCheckStatus(
    isChecking: Boolean,
    lastCheckedTimestamp: Long?,
) {
    val colors = LocalPersonality.current.colors
    if (isChecking) {
        Row(
            modifier =
                Modifier
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
    } else if (lastCheckedTimestamp != null) {
        KomiText(
            text = stringResource(Res.string.last_checked, formatLastChecked(lastCheckedTimestamp)),
            role = KomiTextRole.Body,
            color = colors.onSurfaceVariant,
            modifier =
                Modifier
                    .drivesPullToRefresh()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AppsContent(
    state: AppsState,
    onAction: (AppsAction) -> Unit,
    bottomInset: Dp,
) {
    val listState = rememberLazyStaggeredGridState()
    KeepAtTopWhenSectionsChange(
        gridState = listState,
        sections =
            listOf(
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
                    color = LocalPersonality.current.colors.onBackground,
                )
            }
        }

        else -> {
            AppsGrid(
                state = state,
                onAction = onAction,
                listState = listState,
                contentPadding =
                    PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = 88.dp + bottomInset,
                    ),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

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

// A lazy grid keeps its first visible item in place, so a section appearing above it at the top would land offscreen.
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
