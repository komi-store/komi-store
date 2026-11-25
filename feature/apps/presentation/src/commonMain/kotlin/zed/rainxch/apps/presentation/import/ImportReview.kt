package zed.rainxch.apps.presentation.import

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.apps.presentation.import.model.CandidateUi
import zed.rainxch.apps.presentation.import.model.ImportPhase
import zed.rainxch.apps.presentation.import.model.PendingUndo
import zed.rainxch.apps.presentation.import.model.RepoSuggestionUi
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.ExternalDecisionSnapshot
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.external_import_error_link_failed
import zed.rainxch.githubstore.core.presentation.res.external_import_error_link_network
import zed.rainxch.githubstore.core.presentation.res.external_import_undo_failed
import zed.rainxch.githubstore.core.presentation.res.external_import_undo_linked
import zed.rainxch.githubstore.core.presentation.res.external_import_undo_skipped

// The review cards: link a suggestion, skip, skip forever or skip the rest, and undo the last of these.
internal class ImportReview(
    private val externalImportRepository: ExternalImportRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val linker: ImportLinker,
    private val session: ImportSession,
    private val logger: KomiStoreLogger,
) {
    fun onAction(
        holder: ImportStateHolder,
        action: ExternalImportAction.Review,
    ) {
        when (action) {
            is ExternalImportAction.OnSkipCard -> skipPackage(holder, action.packageName, neverAsk = false)
            is ExternalImportAction.OnSkipForever -> skipPackage(holder, action.packageName, neverAsk = true)
            ExternalImportAction.OnSkipRemaining -> skipRemaining(holder)
            is ExternalImportAction.OnPickSuggestion -> pickSuggestion(holder, action.packageName, action.suggestion)
            is ExternalImportAction.OnLinkCard -> linkCardWithPreselected(holder, action.packageName)
            is ExternalImportAction.OnToggleCardExpanded -> toggleCardExpanded(holder, action.packageName)
            ExternalImportAction.OnUndoLast -> undoLast(holder)
        }
    }

    private fun toggleCardExpanded(
        holder: ImportStateHolder,
        packageName: String,
    ) {
        holder.update { current ->
            val expanded = current.expandedPackages.toPersistentSet()
            val nextSet = if (packageName in expanded) expanded.removing(packageName) else expanded.adding(packageName)
            val keepSearch = current.activeSearchPackage == packageName && packageName in nextSet
            val withSearch = if (keepSearch) current else current.withoutSearch()
            withSearch.copy(expandedPackages = nextSet)
        }
    }

    private fun skipPackage(
        holder: ImportStateHolder,
        packageName: String,
        neverAsk: Boolean,
    ) {
        val card = holder.current.cards.firstOrNull { it.packageName == packageName } ?: return
        holder.scope.launch {
            val snapshot = readSnapshot(holder, packageName) ?: return@launch
            val hadInstalledRow = installedAppsRepository.getAppByPackage(packageName) != null

            val skipped =
                try {
                    externalImportRepository.skipPackage(packageName, neverAsk = neverAsk)
                    true
                } catch (e: LocalStoreException) {
                    logger.error("Skip failed for $packageName: ${e.message}")
                    false
                }
            if (!skipped) {
                holder.send(ExternalImportEvent.ShowError(getString(Res.string.external_import_error_link_failed)))
                return@launch
            }

            removeCard(holder, packageName) { it.copy(skipped = it.skipped + 1) }
            session.pendingUndo = PendingUndo(card, snapshot.value, hadInstalledRow, PendingUndo.Kind.Skip)
            holder.send(ExternalImportEvent.ShowUndoSnackbar(getString(Res.string.external_import_undo_skipped, card.appLabel)))
        }
    }

    private fun pickSuggestion(
        holder: ImportStateHolder,
        packageName: String,
        suggestion: RepoSuggestionUi,
    ) {
        val card = holder.current.cards.firstOrNull { it.packageName == packageName } ?: return
        val source = if (suggestion == card.preselectedSuggestion) "preselected" else "alternative"
        val candidate = session.candidatesByPackage[packageName]

        holder.scope.launch {
            if (candidate == null) {
                logger.error("Cannot materialize $packageName: candidate missing from snapshot")
                holder.send(ExternalImportEvent.ShowError(getString(Res.string.external_import_error_link_failed)))
                return@launch
            }
            val snapshot = readSnapshot(holder, packageName) ?: return@launch
            val hadInstalledRow = installedAppsRepository.getAppByPackage(packageName) != null

            val target = ImportLinker.LinkTarget(suggestion.owner, suggestion.repo, source, suggestion.sourceHost)
            if (!linker.link(candidate, target)) {
                holder.send(ExternalImportEvent.ShowError(getString(Res.string.external_import_error_link_network)))
                return@launch
            }
            removeCard(holder, packageName) { it.copy(manuallyLinked = it.manuallyLinked + 1) }
            session.pendingUndo = PendingUndo(card, snapshot.value, hadInstalledRow, PendingUndo.Kind.Link)
            holder.send(ExternalImportEvent.ShowUndoSnackbar(getString(Res.string.external_import_undo_linked, card.appLabel)))
        }
    }

    private class SnapshotRead(
        val value: ExternalDecisionSnapshot?,
    )

    // Null when the decision can't be read, after telling the user: without it the action couldn't be undone.
    private suspend fun readSnapshot(
        holder: ImportStateHolder,
        packageName: String,
    ): SnapshotRead? =
        try {
            SnapshotRead(externalImportRepository.snapshotDecision(packageName))
        } catch (e: LocalStoreException) {
            logger.error("Snapshot read failed for $packageName: ${e.message}")
            holder.send(ExternalImportEvent.ShowError(getString(Res.string.external_import_error_link_failed)))
            null
        }

    private fun linkCardWithPreselected(
        holder: ImportStateHolder,
        packageName: String,
    ) {
        val card = holder.current.cards.firstOrNull { it.packageName == packageName } ?: return
        val preselect = card.preselectedSuggestion
        if (preselect != null) {
            pickSuggestion(holder, packageName, preselect)
        } else {
            toggleCardExpanded(holder, packageName)
        }
    }

    private fun undoLast(holder: ImportStateHolder) {
        val undo = session.pendingUndo ?: return
        holder.scope.launch {
            try {
                if (undo.kind == PendingUndo.Kind.Link && !undo.hadInstalledAppRowBefore) {
                    installedAppsRepository.deleteInstalledApp(undo.packageName)
                }
                if (undo.snapshot != null) {
                    externalImportRepository.restoreDecision(undo.snapshot)
                } else {
                    externalImportRepository.unlink(undo.packageName)
                }
            } catch (e: LocalStoreException) {
                logger.error("Undo failed for ${undo.packageName}: ${e.message}")
                holder.send(ExternalImportEvent.ShowError(getString(Res.string.external_import_undo_failed)))
                return@launch
            }
            session.pendingUndo = null
            holder.update { it.withCardRestored(undo) }
        }
    }

    private fun ExternalImportState.withCardRestored(undo: PendingUndo): ExternalImportState {
        if (cards.any { it.packageName == undo.packageName }) return this
        val tally =
            when (undo.kind) {
                PendingUndo.Kind.Link -> copy(manuallyLinked = (manuallyLinked - 1).coerceAtLeast(0))
                PendingUndo.Kind.Skip -> copy(skipped = (skipped - 1).coerceAtLeast(0))
            }
        return tally.copy(
            cards = (listOf(undo.card) + cards).toImmutableList(),
            phase = ImportPhase.AwaitingReview,
            showCompletionToast = false,
        )
    }

    private fun skipRemaining(holder: ImportStateHolder) {
        val current = holder.current
        if (current.phase != ImportPhase.AwaitingReview) return
        val remaining = current.cards
        if (remaining.isEmpty()) return

        holder.scope.launch {
            val successes = skipAll(remaining)
            session.pendingUndo = null
            val allSucceeded = successes.size == remaining.size
            holder.update { state ->
                val keptCards = state.cards.filter { it.packageName !in successes }.toImmutableList()
                state.withoutSearch().copy(
                    cards = keptCards,
                    expandedPackages = persistentSetOf(),
                    phase = if (allSucceeded && keptCards.isEmpty()) ImportPhase.Done else state.phase,
                    skipped = state.skipped + successes.size,
                    showCompletionToast = allSucceeded && keptCards.isEmpty(),
                    confettiPlayCount = if (allSucceeded) state.confettiPlayCount + 1 else state.confettiPlayCount,
                )
            }
            if (!allSucceeded) {
                holder.send(ExternalImportEvent.ShowError(getString(Res.string.external_import_error_link_failed)))
            }
        }
    }

    private suspend fun skipAll(cards: List<CandidateUi>): Set<String> {
        val successes = mutableSetOf<String>()
        cards.forEach { card ->
            try {
                externalImportRepository.skipPackage(card.packageName, neverAsk = false)
                successes += card.packageName
            } catch (e: LocalStoreException) {
                logger.error("Skip-remaining failed for ${card.packageName}: ${e.message}")
            }
        }
        return successes
    }

    private fun removeCard(
        holder: ImportStateHolder,
        packageName: String,
        tally: (ExternalImportState) -> ExternalImportState,
    ) {
        holder.update { current ->
            val newCards = current.cards.filterNot { it.packageName == packageName }.toImmutableList()
            val withSearch = if (current.activeSearchPackage == packageName) current.withoutSearch() else current
            val tallied =
                tally(withSearch).copy(
                    cards = newCards,
                    expandedPackages = current.expandedPackages.toPersistentSet().removing(packageName),
                )
            if (newCards.isEmpty()) {
                tallied.copy(
                    phase = ImportPhase.Done,
                    showCompletionToast = true,
                    confettiPlayCount = tallied.confettiPlayCount + 1,
                )
            } else {
                tallied
            }
        }
    }
}

internal fun ExternalImportState.withoutSearch(): ExternalImportState =
    copy(
        activeSearchPackage = null,
        searchQuery = "",
        searchResults = persistentListOf(),
        isSearching = false,
        searchError = null,
    )
