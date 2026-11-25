package zed.rainxch.apps.presentation.import

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.apps.presentation.import.model.CandidateUi
import zed.rainxch.apps.presentation.import.model.ImportPhase
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.error.SystemPackagesException
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.ExternalDecisionSnapshot
import zed.rainxch.core.domain.system.RepoMatchResult
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.external_import_error_scan_failed_default

// The scan: find untracked apps, auto-link the confident matches and build review cards for the rest.
internal class ImportScan(
    private val externalImportRepository: ExternalImportRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val linker: ImportLinker,
    private val session: ImportSession,
    private val logger: KomiStoreLogger,
) {
    private var scanJob: Job? = null
    private var skipRevealJob: Job? = null

    fun onAction(
        holder: ImportStateHolder,
        action: ExternalImportAction.Scan,
    ) {
        when (action) {
            ExternalImportAction.OnStart -> {
                if (holder.current.phase == ImportPhase.Idle) startIfIdle(holder)
            }

            ExternalImportAction.OnRequestPermission -> {
                holder.update { it.copy(phase = ImportPhase.RequestingPermission) }
            }

            is ExternalImportAction.OnPermissionGranted -> {
                holder.update { it.copy(isPermissionDenied = false) }
                startIfIdle(holder, force = true)
            }

            is ExternalImportAction.OnPermissionDenied -> {
                holder.update { it.copy(isPermissionDenied = true) }
                startIfIdle(holder, force = true)
            }

            ExternalImportAction.OnSkipLongScan -> {
                skipLongScan(holder)
            }
        }
    }

    fun startIfIdle(
        holder: ImportStateHolder,
        force: Boolean = false,
    ) {
        if (!force && holder.current.phase != ImportPhase.Idle) return
        if (scanJob?.isActive == true) return

        skipRevealJob?.cancel()
        skipRevealJob =
            holder.scope.launch {
                delay(SKIP_REVEAL_DELAY_MS)
                holder.update { it.copy(isSkipAvailable = true) }
            }
        scanJob =
            holder.scope.launch {
                try {
                    scan(holder)
                } catch (e: LocalStoreException) {
                    scanFailed(holder, e)
                } catch (e: SystemPackagesException) {
                    scanFailed(holder, e)
                } catch (e: RateLimitException) {
                    scanFailed(holder, e)
                } finally {
                    skipRevealJob?.cancel()
                    skipRevealJob = null
                }
            }
    }

    private suspend fun scan(holder: ImportStateHolder) {
        holder.update {
            it.copy(
                phase = ImportPhase.Scanning,
                errorMessage = null,
                scanStartedAtMs = System.currentTimeMillis(),
                isSkipAvailable = false,
            )
        }

        externalImportRepository.runFullScan(includeUnverified = true)

        val candidates = externalImportRepository.pendingCandidatesFlow().first()
        session.candidatesByPackage = candidates.associateBy { it.packageName }
        holder.update { it.copy(phase = ImportPhase.AutoImporting, totalCandidates = candidates.size) }

        val matches = externalImportRepository.resolveMatches(candidates)
        session.lastResolvedMatches = matches
        val autoLinked = autoLink(matches)
        val autoLinkedPackages = autoLinked.toSet()
        val matchesByPackage = matches.associateBy { it.packageName }
        val cards =
            candidates
                .filter { it.packageName !in autoLinkedPackages }
                .map { candidateCard(it, matchesByPackage[it.packageName]) }
                .toImmutableList()

        skipRevealJob?.cancel()
        skipRevealJob = null
        showScanResult(holder, autoLinked, cards)
    }

    private fun showScanResult(
        holder: ImportStateHolder,
        autoLinked: List<String>,
        cards: List<CandidateUi>,
    ) {
        holder.update {
            when {
                autoLinked.isNotEmpty() -> {
                    it.copy(
                        phase = ImportPhase.AutoImportSummary,
                        cards = cards.toImmutableList(),
                        autoImported = autoLinked.size,
                        autoLinkedPackages = autoLinked.toPersistentList(),
                        autoLinkedLabels = autoLinked.mapNotNull { pkg -> session.candidatesByPackage[pkg]?.appLabel }.toPersistentList(),
                    )
                }

                cards.isEmpty() -> {
                    it.copy(
                        phase = ImportPhase.Done,
                        cards = persistentListOf(),
                        autoImported = 0,
                        showCompletionToast = true,
                        confettiPlayCount = it.confettiPlayCount + 1,
                    )
                }

                else -> {
                    it.copy(phase = ImportPhase.AwaitingReview, cards = cards.toImmutableList(), autoImported = 0)
                }
            }.copy(scanStartedAtMs = null, isSkipAvailable = false)
        }
    }

    private suspend fun scanFailed(
        holder: ImportStateHolder,
        cause: Exception,
    ) {
        logger.error("External import scan failed: ${cause.message}")
        holder.update {
            it.copy(
                phase = ImportPhase.Idle,
                errorMessage = cause.message,
                scanStartedAtMs = null,
                isSkipAvailable = false,
            )
        }
        holder.send(
            ExternalImportEvent.ShowError(cause.message ?: getString(Res.string.external_import_error_scan_failed_default)),
        )
    }

    // Cards from whatever had resolved when the user stopped waiting.
    private fun skipLongScan(holder: ImportStateHolder) {
        val active = scanJob ?: return
        if (!active.isActive) return
        scanJob = null
        skipRevealJob?.cancel()
        skipRevealJob = null
        active.cancel()

        val partialCandidates = session.candidatesByPackage.values.toList()
        val partialMatchesByPackage = session.lastResolvedMatches.associateBy { it.packageName }
        holder.scope.launch {
            val cards =
                partialCandidates
                    .map { candidateCard(it, partialMatchesByPackage[it.packageName]) }
                    .toImmutableList()
            holder.update {
                it.copy(
                    phase = if (cards.isEmpty()) ImportPhase.Done else ImportPhase.AwaitingReview,
                    cards = cards,
                    autoImported = 0,
                    showCompletionToast = cards.isEmpty(),
                    scanStartedAtMs = null,
                    isSkipAvailable = false,
                    errorMessage = null,
                )
            }
        }
    }

    // Links every candidate whose top match clears the bar, remembering what each looked like so Undo all can restore it.
    private suspend fun autoLink(matches: List<RepoMatchResult>): List<String> {
        val linked = mutableListOf<String>()
        val hadInstalledRow = mutableMapOf<String, Boolean>()
        val preSnapshots = mutableMapOf<String, ExternalDecisionSnapshot?>()
        for (result in matches) {
            val top = result.topSuggestion?.takeIf { it.confidence >= AUTO_LINK_THRESHOLD }
            val candidate = session.candidatesByPackage[result.packageName]
            val before = if (top != null && candidate != null) readBefore(result.packageName) else null
            if (top != null && candidate != null && before != null) {
                val target =
                    ImportLinker.LinkTarget(
                        owner = top.owner,
                        repo = top.repo,
                        source = "auto-${top.source.name.lowercase()}",
                        sourceHost = top.sourceHost,
                    )
                if (linker.link(candidate, target)) {
                    linked += result.packageName
                    hadInstalledRow[result.packageName] = before.hadInstalledRow
                    preSnapshots[result.packageName] = before.snapshot
                }
            }
        }
        session.autoLinkedHadInstalledRow = hadInstalledRow.toMap()
        session.autoLinkedPreSnapshots = preSnapshots.toMap()
        return linked
    }

    private class Before(
        val snapshot: ExternalDecisionSnapshot?,
        val hadInstalledRow: Boolean,
    )

    // Null when the decision can't be read, which skips the auto-link: it couldn't be undone.
    private suspend fun readBefore(packageName: String): Before? {
        val snapshot =
            try {
                externalImportRepository.snapshotDecision(packageName)
            } catch (e: LocalStoreException) {
                logger.warn("Skip auto-link for $packageName: pre-link snapshot read failed (${e.message})")
                return null
            }
        val hadInstalledRow =
            try {
                installedAppsRepository.getAppByPackage(packageName) != null
            } catch (_: LocalStoreException) {
                false
            }
        return Before(snapshot, hadInstalledRow)
    }

    private companion object {
        const val AUTO_LINK_THRESHOLD = 0.85
        const val SKIP_REVEAL_DELAY_MS = 5_000L
    }
}
