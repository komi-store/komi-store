package zed.rainxch.apps.presentation.import

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
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
import zed.rainxch.core.presentation.components.overlays.KomiDropdown
import zed.rainxch.core.presentation.components.overlays.KomiMenuItem
import zed.rainxch.core.presentation.components.overlays.KomiToastState
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
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
    modifier: Modifier = Modifier,
    viewModel: ExternalImportViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberKomiToastState()
    val scope = rememberCoroutineScope()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            ExternalImportEvent.NavigateBack -> {
                onNavigateBack()
            }

            is ExternalImportEvent.NavigateToDetails -> {
                onNavigateToDetails(event.repoId)
            }

            ExternalImportEvent.NavigateBackAndOpenManualLink -> {
                onAddManually()
            }

            is ExternalImportEvent.ShowError -> {
                scope.launch { toastState.danger(event.message) }
            }

            is ExternalImportEvent.ShowUndoSnackbar -> {
                toastState.toasts.clear()
                scope.launch {
                    toastState.show(
                        message = event.message,
                        actionLabel = getString(Res.string.external_import_undo_action),
                        dismissible = true,
                        onAction = { viewModel.onAction(ExternalImportAction.OnUndoLast) },
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

    CompositionLocalProvider(LocalReducedMotion provides rememberSystemReducedMotion()) {
        ExternalImportScreen(
            state = state,
            onAction = viewModel::onAction,
            toastState = toastState,
            modifier = modifier,
        )
    }
}

@Composable
private fun ExternalImportScreen(
    state: ExternalImportState,
    onAction: (ExternalImportAction) -> Unit,
    toastState: KomiToastState,
    modifier: Modifier = Modifier,
) {
    KomiScaffold(
        modifier = modifier,
        topBar = {
            KomiTopBar(
                title = stringResource(Res.string.external_import_top_bar_title),
                size = KomiTopBarSize.Compact,
                onBack = { onAction(ExternalImportAction.OnExit) },
                actions =
                    if (state.phase == ImportPhase.AwaitingReview && state.cards.size > 1) {
                        { SkipRemainingMenu(onAction = onAction) }
                    } else {
                        null
                    },
            )
        },
        toastState = toastState,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            ImportPhaseContent(state = state, onAction = onAction)

            if (state.phase == ImportPhase.Done && state.confettiPlayCount > 0) {
                key(state.confettiPlayCount) {
                    ConfettiOverlay(enabled = true)
                }
            }
        }
    }
}

@Composable
private fun SkipRemainingMenu(onAction: (ExternalImportAction) -> Unit) {
    KomiDropdown(
        entries =
            persistentListOf(
                KomiMenuItem(
                    id = SKIP_REMAINING_ID,
                    label = stringResource(Res.string.external_import_overflow_skip_remaining),
                ),
            ),
        onSelect = { item ->
            if (item.id == SKIP_REMAINING_ID) onAction(ExternalImportAction.OnSkipRemaining)
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

@Composable
private fun ImportPhaseContent(
    state: ExternalImportState,
    onAction: (ExternalImportAction) -> Unit,
) {
    when (state.phase) {
        ImportPhase.Idle, ImportPhase.Scanning, ImportPhase.AutoImporting -> {
            ImportProgressScreen(
                phase = state.phase,
                totalCandidates = state.totalCandidates,
                canSkip = state.isSkipAvailable && state.phase != ImportPhase.Idle,
                onSkip = { onAction(ExternalImportAction.OnSkipLongScan) },
            )
        }

        ImportPhase.RequestingPermission -> {
            PermissionRationaleScreen(onAction = onAction)
        }

        ImportPhase.AutoImportSummary -> {
            AutoImportSummaryScreen(
                autoLinkedCount = state.autoImported,
                autoLinkedLabels = state.autoLinkedLabels,
                cardsRemaining = state.cards.size,
                onContinue = { onAction(ExternalImportAction.OnAutoSummaryContinue) },
                onUndoAll = { onAction(ExternalImportAction.OnAutoSummaryUndoAll) },
            )
        }

        ImportPhase.AwaitingReview -> {
            if (state.cards.isEmpty()) NothingToImport(state, onAction) else ReviewCards(state, onAction)
        }

        ImportPhase.Done -> {
            if (state.cards.isEmpty() && state.trackedCount == 0) {
                NothingToImport(state, onAction)
            } else {
                CompletionToast(
                    trackedCount = state.trackedCount,
                    skipped = state.skipped,
                    onExit = { onAction(ExternalImportAction.OnExit) },
                )
            }
        }
    }
}

@Composable
private fun NothingToImport(
    state: ExternalImportState,
    onAction: (ExternalImportAction) -> Unit,
) {
    EmptyStateScreen(
        isPermissionDenied = state.isPermissionDenied,
        onRequestPermission = { onAction(ExternalImportAction.OnRequestPermission) },
        onExit = { onAction(ExternalImportAction.OnExit) },
        onAddManually = { onAction(ExternalImportAction.OnAddManually) },
    )
}

@Composable
private fun ReviewCards(
    state: ExternalImportState,
    onAction: (ExternalImportAction) -> Unit,
) {
    WizardList(
        cards = state.cards,
        expandedPackages = state.expandedPackages,
        activeSearchPackage = state.activeSearchPackage,
        searchQuery = state.searchQuery,
        searchResults = state.searchResults,
        isSearching = state.isSearching,
        searchError = state.searchError,
        onToggleExpand = { pkg -> onAction(ExternalImportAction.OnToggleCardExpanded(pkg)) },
        onPick = { pkg, suggestion -> onAction(ExternalImportAction.OnPickSuggestion(pkg, suggestion)) },
        onSkip = { pkg -> onAction(ExternalImportAction.OnSkipCard(pkg)) },
        onLink = { pkg -> onAction(ExternalImportAction.OnLinkCard(pkg)) },
        onSearchQueryChange = { pkg, query -> onAction(ExternalImportAction.OnSearchOverrideChanged(pkg, query)) },
        onSearchSubmit = { pkg -> onAction(ExternalImportAction.OnSearchOverrideSubmit(pkg)) },
        onAddManually = { onAction(ExternalImportAction.OnAddManually) },
    )
}

private const val SKIP_REMAINING_ID = "skip_remaining"

const val EXTERNAL_IMPORT_OPEN_LINK_SHEET_KEY = "external_import_open_link_sheet"
