package zed.rainxch.apps.presentation.import

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import zed.rainxch.core.presentation.components.overlays.KomiDropdown
import zed.rainxch.core.presentation.components.overlays.KomiMenuItem
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.apps.presentation.import.components.AutoImportSummaryScreen
import zed.rainxch.apps.presentation.import.components.CompletionToast
import zed.rainxch.apps.presentation.import.components.ConfettiOverlay
import zed.rainxch.apps.presentation.import.components.EmptyStateScreen
import zed.rainxch.apps.presentation.import.components.ImportProgressScreen
import zed.rainxch.apps.presentation.import.components.PermissionRationaleScreen
import zed.rainxch.apps.presentation.import.components.WizardList
import zed.rainxch.apps.presentation.import.model.ImportPhase
import zed.rainxch.apps.presentation.import.util.LocalReducedMotion
import zed.rainxch.apps.presentation.import.util.rememberSystemReducedMotion
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.bars.KomiTopBarSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.external_import_overflow_more
import zed.rainxch.githubstore.core.presentation.res.external_import_overflow_skip_remaining
import zed.rainxch.githubstore.core.presentation.res.external_import_top_bar_title
import zed.rainxch.githubstore.core.presentation.res.external_import_undo_action

@Composable
fun ExternalImportRoot(
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (repoId: Long) -> Unit,
    onAddManually: () -> Unit,
    viewModel: ExternalImportViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberKomiToastState()
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ExternalImportEvent.NavigateBack -> onNavigateBack()
            is ExternalImportEvent.NavigateToDetails -> onNavigateToDetails(event.repoId)
            ExternalImportEvent.NavigateBackAndOpenManualLink -> onAddManually()
            is ExternalImportEvent.ShowError -> {
                scope.launch { toastState.danger(event.message) }
            }

            is ExternalImportEvent.ShowUndoSnackbar -> {
                toastState.toasts.clear()
                scope.launch {
                    val undoLabel = getString(Res.string.external_import_undo_action)
                    toastState.show(
                        message = event.message,
                        actionLabel = undoLabel,
                        dismissible = true,
                        onAction = {
                            viewModel.onAction(ExternalImportAction.OnUndoLast)
                        },
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (state.phase == ImportPhase.Idle) {
            viewModel.onAction(ExternalImportAction.OnStart)
        }
    }

    val reducedMotion = rememberSystemReducedMotion()

    CompositionLocalProvider(LocalReducedMotion provides reducedMotion) {
        KomiScaffold(
            topBar = {
                KomiTopBar(
                    title = stringResource(Res.string.external_import_top_bar_title),
                    size = KomiTopBarSize.Compact,
                    onBack = { viewModel.onAction(ExternalImportAction.OnExit) },
                    actions = if (state.phase == ImportPhase.AwaitingReview && state.cards.size > 1) {
                        {
                            KomiDropdown(
                                entries = persistentListOf(
                                    KomiMenuItem(
                                        id = "skip_remaining",
                                        label = stringResource(Res.string.external_import_overflow_skip_remaining),
                                    ),
                                ),
                                onSelect = { item ->
                                    when (item.id) {
                                        "skip_remaining" ->
                                            viewModel.onAction(ExternalImportAction.OnSkipRemaining)
                                    }
                                },
                                trigger = { onClick ->
                                    KomiIconButton(
                                        icon = Icons.Rounded.MoreVert,
                                        contentDescription = stringResource(Res.string.external_import_overflow_more),
                                        onClick = onClick,
                                        variant = KomiButtonVariant.Tonal,
                                    )
                                },
                            )
                        }
                    } else {
                        null
                    },
                )
            },
            toastState = toastState,
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {
                when (state.phase) {
                    ImportPhase.Idle, ImportPhase.Scanning, ImportPhase.AutoImporting -> {
                        ImportProgressScreen(
                            phase = state.phase,
                            totalCandidates = state.totalCandidates,
                            canSkip = state.isSkipAvailable &&
                                    (state.phase == ImportPhase.Scanning ||
                                            state.phase == ImportPhase.AutoImporting),
                            onSkip = {
                                viewModel.onAction(ExternalImportAction.OnSkipLongScan)
                            },
                        )
                    }

                    ImportPhase.RequestingPermission -> {
                        PermissionRationaleScreen(
                            onAction = viewModel::onAction,
                        )
                    }

                    ImportPhase.AutoImportSummary -> {
                        AutoImportSummaryScreen(
                            autoLinkedCount = state.autoImported,
                            autoLinkedLabels = state.autoLinkedLabels,
                            cardsRemaining = state.cards.size,
                            onContinue = {
                                viewModel.onAction(ExternalImportAction.OnAutoSummaryContinue)
                            },
                            onUndoAll = {
                                viewModel.onAction(ExternalImportAction.OnAutoSummaryUndoAll)
                            },
                        )
                    }

                    ImportPhase.AwaitingReview -> {
                        if (state.cards.isEmpty()) {
                            EmptyStateScreen(
                                isPermissionDenied = state.isPermissionDenied,
                                onRequestPermission = {
                                    viewModel.onAction(ExternalImportAction.OnRequestPermission)
                                },
                                onExit = { viewModel.onAction(ExternalImportAction.OnExit) },
                                onAddManually = {
                                    viewModel.onAction(ExternalImportAction.OnAddManually)
                                },
                            )
                        } else {
                            WizardList(
                                cards = state.cards,
                                expandedPackages = state.expandedPackages,
                                activeSearchPackage = state.activeSearchPackage,
                                searchQuery = state.searchQuery,
                                searchResults = state.searchResults,
                                isSearching = state.isSearching,
                                searchError = state.searchError,
                                onToggleExpanded = { pkg ->
                                    viewModel.onAction(
                                        ExternalImportAction.OnToggleCardExpanded(pkg),
                                    )
                                },
                                onPick = { pkg, suggestion ->
                                    viewModel.onAction(
                                        ExternalImportAction.OnPickSuggestion(pkg, suggestion),
                                    )
                                },
                                onSkip = { pkg ->
                                    viewModel.onAction(ExternalImportAction.OnSkipCard(pkg))
                                },
                                onLink = { pkg ->
                                    viewModel.onAction(ExternalImportAction.OnLinkCard(pkg))
                                },
                                onSearchQueryChange = { pkg, query ->
                                    viewModel.onAction(
                                        ExternalImportAction.OnSearchOverrideChanged(pkg, query),
                                    )
                                },
                                onSearchSubmit = { pkg ->
                                    viewModel.onAction(
                                        ExternalImportAction.OnSearchOverrideSubmit(pkg),
                                    )
                                },
                                onAddManually = {
                                    viewModel.onAction(ExternalImportAction.OnAddManually)
                                },
                            )
                        }
                    }

                    ImportPhase.Done -> {
                        if (state.cards.isEmpty() && state.trackedCount == 0) {
                            EmptyStateScreen(
                                isPermissionDenied = state.isPermissionDenied,
                                onRequestPermission = {
                                    viewModel.onAction(ExternalImportAction.OnRequestPermission)
                                },
                                onExit = { viewModel.onAction(ExternalImportAction.OnExit) },
                                onAddManually = {
                                    viewModel.onAction(ExternalImportAction.OnAddManually)
                                },
                            )
                        } else {
                            CompletionToast(
                                trackedCount = state.trackedCount,
                                skipped = state.skipped,
                                onExit = { viewModel.onAction(ExternalImportAction.OnExit) },
                            )
                        }
                    }
                }

                if (state.phase == ImportPhase.Done && state.confettiPlayCount > 0) {
                    key(state.confettiPlayCount) {
                        ConfettiOverlay(enabled = true)
                    }
                }
            }
        }
    }
}

const val EXTERNAL_IMPORT_OPEN_LINK_SHEET_KEY = "external_import_open_link_sheet"
