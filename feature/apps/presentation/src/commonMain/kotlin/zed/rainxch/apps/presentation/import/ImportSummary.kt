package zed.rainxch.apps.presentation.import

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.apps.presentation.import.model.ImportPhase
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.external_import_undo_failed

// The auto-link summary: carry on to the review cards, or undo every auto-link and review those apps too.
internal class ImportSummary(
    private val externalImportRepository: ExternalImportRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val session: ImportSession,
    private val logger: KomiStoreLogger,
) {
    fun onAction(
        holder: ImportStateHolder,
        action: ExternalImportAction.Summary,
    ) {
        when (action) {
            ExternalImportAction.OnAutoSummaryContinue -> continueToReview(holder)
            ExternalImportAction.OnAutoSummaryUndoAll -> undoAll(holder)
            ExternalImportAction.OnDismissCompletionToast -> holder.update { it.copy(showCompletionToast = false) }
        }
    }

    private fun continueToReview(holder: ImportStateHolder) {
        val current = holder.current
        if (current.phase != ImportPhase.AutoImportSummary) return

        session.forgetAutoLinks()
        if (current.cards.isNotEmpty()) {
            holder.update { it.copy(phase = ImportPhase.AwaitingReview) }
        } else {
            holder.update {
                it.copy(
                    phase = ImportPhase.Done,
                    showCompletionToast = true,
                    confettiPlayCount = it.confettiPlayCount + 1,
                )
            }
        }
    }

    private fun undoAll(holder: ImportStateHolder) {
        val current = holder.current
        if (current.phase != ImportPhase.AutoImportSummary) return
        val packages = current.autoLinkedPackages.toList()
        if (packages.isEmpty()) {
            holder.update { it.copy(phase = ImportPhase.AwaitingReview) }
            return
        }

        val hadInstalledRow = session.autoLinkedHadInstalledRow
        val preSnapshots = session.autoLinkedPreSnapshots
        holder.scope.launch {
            try {
                packages.forEach { pkg ->
                    if (hadInstalledRow[pkg] != true) installedAppsRepository.deleteInstalledApp(pkg)
                    val preSnapshot = preSnapshots[pkg]
                    if (preSnapshot != null) {
                        externalImportRepository.restoreDecision(preSnapshot)
                    } else {
                        externalImportRepository.unlink(pkg)
                    }
                }
            } catch (e: LocalStoreException) {
                logger.error("Bulk undo failed: ${e.message}")
                holder.send(ExternalImportEvent.ShowError(getString(Res.string.external_import_undo_failed)))
                return@launch
            }

            session.pendingUndo = null
            session.forgetAutoLinks()
            restoreCards(holder, packages)
        }
    }

    private suspend fun restoreCards(
        holder: ImportStateHolder,
        packages: List<String>,
    ) {
        val matchesByPackage = session.lastResolvedMatches.associateBy { it.packageName }
        val restoredCards =
            packages.mapNotNull { pkg ->
                session.candidatesByPackage[pkg]?.let { candidateCard(it, matchesByPackage[pkg]) }
            }
        holder.update { state ->
            val merged = (restoredCards + state.cards).distinctBy { it.packageName }.toImmutableList()
            state.copy(
                phase = if (merged.isEmpty()) ImportPhase.Done else ImportPhase.AwaitingReview,
                cards = merged,
                autoImported = 0,
                autoLinkedPackages = persistentListOf(),
                autoLinkedLabels = persistentListOf(),
                showCompletionToast = merged.isEmpty(),
                confettiPlayCount = if (merged.isEmpty()) state.confettiPlayCount + 1 else state.confettiPlayCount,
            )
        }
    }
}
