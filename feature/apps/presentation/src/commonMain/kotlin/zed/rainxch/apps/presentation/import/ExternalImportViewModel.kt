package zed.rainxch.apps.presentation.import

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.apps.domain.repository.AppsRepository
import zed.rainxch.apps.presentation.import.model.CandidateUi
import zed.rainxch.apps.presentation.import.model.ImportPhase
import zed.rainxch.apps.presentation.import.model.PendingUndo
import zed.rainxch.apps.presentation.import.model.RepoSuggestionUi
import zed.rainxch.apps.presentation.import.model.SuggestionSource
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.installation.DeviceApp
import zed.rainxch.core.domain.model.repository.RepositorySource
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.ExternalAppCandidate
import zed.rainxch.core.domain.system.ExternalDecisionSnapshot
import zed.rainxch.core.domain.system.InstallerKind
import zed.rainxch.core.domain.system.RepoMatchResult
import zed.rainxch.core.domain.system.RepoMatchSource
import zed.rainxch.core.domain.system.RepoMatchSuggestion
import zed.rainxch.core.domain.utils.RepositoryUrlParser
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.external_import_error_link_failed
import zed.rainxch.githubstore.core.presentation.res.external_import_error_link_network
import zed.rainxch.githubstore.core.presentation.res.external_import_error_scan_failed_default
import zed.rainxch.githubstore.core.presentation.res.external_import_installer_browser
import zed.rainxch.githubstore.core.presentation.res.external_import_installer_fdroid
import zed.rainxch.githubstore.core.presentation.res.external_import_installer_obtainium
import zed.rainxch.githubstore.core.presentation.res.external_import_installer_self
import zed.rainxch.githubstore.core.presentation.res.external_import_installer_sideload
import zed.rainxch.githubstore.core.presentation.res.external_import_installer_unknown
import zed.rainxch.githubstore.core.presentation.res.external_import_search_error_default
import zed.rainxch.githubstore.core.presentation.res.external_import_undo_failed
import zed.rainxch.githubstore.core.presentation.res.external_import_undo_linked
import zed.rainxch.githubstore.core.presentation.res.external_import_undo_skipped

class ExternalImportViewModel(
    private val externalImportRepository: ExternalImportRepository,
    private val appsRepository: AppsRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val logger: KomiStoreLogger,
    private val tweaksRepository: TweaksRepository,
) : ViewModel() {
    private var candidatesByPackage: Map<String, ExternalAppCandidate> = emptyMap()

    private var lastResolvedMatches: List<RepoMatchResult> = emptyList()

    private var autoLinkedHadInstalledRow: Map<String, Boolean> = emptyMap()

    private var autoLinkedPreSnapshots: Map<String, ExternalDecisionSnapshot?> = emptyMap()
    private var hasStarted = false
    private var scanJob: Job? = null
    private var searchJob: Job? = null
    private var pendingUndo: PendingUndo? = null

    private val _state = MutableStateFlow(ExternalImportState())
    val state =
        _state
            .onStart {
                if (!hasStarted) {
                    hasStarted = true
                    startScanIfIdle()
                }
            }.map { it.copy(trackedCount = it.autoImported + it.manuallyLinked) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ExternalImportState(),
            )

    private val _events = Channel<ExternalImportEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onAction(action: ExternalImportAction) {
        when (action) {
            ExternalImportAction.OnStart -> {
                if (_state.value.phase == ImportPhase.Idle) startScanIfIdle()
            }

            ExternalImportAction.OnRequestPermission -> {
                _state.update { it.copy(phase = ImportPhase.RequestingPermission) }
            }

            is ExternalImportAction.OnPermissionGranted -> {
                _state.update { it.copy(isPermissionDenied = false) }
                startScanIfIdle(force = true)
            }

            is ExternalImportAction.OnPermissionDenied -> {
                _state.update { it.copy(isPermissionDenied = true) }
                startScanIfIdle(force = true)
            }

            is ExternalImportAction.OnSkipCard -> {
                skipPackage(action.packageName, neverAsk = false)
            }

            is ExternalImportAction.OnSkipForever -> {
                skipPackage(action.packageName, neverAsk = true)
            }

            ExternalImportAction.OnSkipRemaining -> {
                skipRemaining()
            }

            ExternalImportAction.OnSkipLongScan -> {
                skipLongScan()
            }

            is ExternalImportAction.OnPickSuggestion -> {
                pickSuggestion(action.packageName, action.suggestion)
            }

            is ExternalImportAction.OnLinkCard -> {
                linkCardWithPreselected(action.packageName)
            }

            is ExternalImportAction.OnToggleCardExpanded -> {
                toggleCardExpanded(action.packageName)
            }

            is ExternalImportAction.OnSearchOverrideChanged -> {
                _state.update {
                    if (it.activeSearchPackage != action.packageName) {
                        it.copy(
                            activeSearchPackage = action.packageName,
                            searchQuery = action.query,
                            searchResults = persistentListOf(),
                            isSearching = false,
                            searchError = null,
                        )
                    } else {
                        it.copy(searchQuery = action.query)
                    }
                }
            }

            is ExternalImportAction.OnSearchOverrideSubmit -> {
                submitSearchOverride(action.packageName)
            }

            ExternalImportAction.OnUndoLast -> {
                undoLast()
            }

            ExternalImportAction.OnExit -> {
                viewModelScope.launch {
                    _events.send(ExternalImportEvent.NavigateBack)
                }
            }

            ExternalImportAction.OnDismissCompletionToast -> {
                _state.update { it.copy(showCompletionToast = false) }
            }

            ExternalImportAction.OnAutoSummaryContinue -> {
                autoSummaryContinue()
            }

            ExternalImportAction.OnAutoSummaryUndoAll -> {
                autoSummaryUndoAll()
            }

            ExternalImportAction.OnAddManually -> {
                viewModelScope.launch {
                    _events.send(ExternalImportEvent.NavigateBackAndOpenManualLink)
                }
            }
        }
    }

    private fun toggleCardExpanded(packageName: String) {
        _state.update { current ->
            val nextSet =
                if (packageName in current.expandedPackages) {
                    current.expandedPackages.toPersistentSet().removing(packageName)
                } else {
                    current.expandedPackages.toPersistentSet().adding(packageName)
                }

            val keepSearch = current.activeSearchPackage == packageName && packageName in nextSet
            current.copy(
                expandedPackages = nextSet,
                activeSearchPackage = if (keepSearch) current.activeSearchPackage else null,
                searchQuery = if (keepSearch) current.searchQuery else "",
                searchResults = if (keepSearch) current.searchResults else persistentListOf(),
                isSearching = if (keepSearch) current.isSearching else false,
                searchError = if (keepSearch) current.searchError else null,
            )
        }
    }

    private var skipRevealJob: Job? = null

    private fun startScanIfIdle(force: Boolean = false) {
        if (!force && _state.value.phase != ImportPhase.Idle) return
        if (scanJob?.isActive == true) return

        skipRevealJob?.cancel()
        skipRevealJob =
            viewModelScope.launch {
                kotlinx.coroutines.delay(SKIP_REVEAL_DELAY_MS)
                _state.update { it.copy(isSkipAvailable = true) }
            }
        scanJob =
            viewModelScope.launch {
                try {
                    _state.update {
                        it.copy(
                            phase = ImportPhase.Scanning,
                            errorMessage = null,
                            scanStartedAtMs = System.currentTimeMillis(),
                            isSkipAvailable = false,
                        )
                    }

                    externalImportRepository.runFullScan(includeUnverified = true)

                    val candidates = externalImportRepository.pendingCandidatesFlow().first()
                    candidatesByPackage = candidates.associateBy { it.packageName }

                    _state.update {
                        it.copy(
                            phase = ImportPhase.AutoImporting,
                            totalCandidates = candidates.size,
                        )
                    }

                    val matches = externalImportRepository.resolveMatches(candidates)
                    lastResolvedMatches = matches
                    val autoLinked = autoMaterialize(matches)
                    val autoLinkedPackages = autoLinked.toSet()

                    val reviewCandidates =
                        candidates.filter { it.packageName !in autoLinkedPackages }
                    val reviewMatchesByPkg =
                        matches.associateBy { it.packageName }

                    val cards =
                        reviewCandidates
                            .mapNotNull { candidate ->
                                val match = reviewMatchesByPkg[candidate.packageName]
                                buildCard(candidate, match)
                            }.toImmutableList()

                    skipRevealJob?.cancel()
                    skipRevealJob = null

                    if (autoLinked.isNotEmpty()) {
                        val autoLinkedLabels =
                            autoLinked.mapNotNull { pkg ->
                                candidatesByPackage[pkg]?.appLabel
                            }
                        _state.update {
                            it.copy(
                                phase = ImportPhase.AutoImportSummary,
                                cards = cards,
                                autoImported = autoLinked.size,
                                autoLinkedPackages = autoLinked.toPersistentList(),
                                autoLinkedLabels = autoLinkedLabels.toPersistentList(),
                                scanStartedAtMs = null,
                                isSkipAvailable = false,
                            )
                        }
                    } else if (cards.isEmpty()) {
                        _state.update {
                            it.copy(
                                phase = ImportPhase.Done,
                                cards = persistentListOf(),
                                autoImported = 0,
                                showCompletionToast = true,
                                scanStartedAtMs = null,
                                isSkipAvailable = false,
                                confettiPlayCount = it.confettiPlayCount + 1,
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                phase = ImportPhase.AwaitingReview,
                                cards = cards,
                                autoImported = 0,
                                scanStartedAtMs = null,
                                isSkipAvailable = false,
                            )
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("External import scan failed: ${e.message}")
                    _state.update {
                        it.copy(
                            phase = ImportPhase.Idle,
                            errorMessage = e.message,
                            scanStartedAtMs = null,
                            isSkipAvailable = false,
                        )
                    }
                    _events.send(
                        ExternalImportEvent.ShowError(
                            e.message ?: getString(Res.string.external_import_error_scan_failed_default),
                        ),
                    )
                } finally {
                    skipRevealJob?.cancel()
                    skipRevealJob = null
                }
            }
    }

    private fun skipLongScan() {
        val active = scanJob ?: return
        if (!active.isActive) return
        scanJob = null
        skipRevealJob?.cancel()
        skipRevealJob = null
        active.cancel()

        val partialCandidates = candidatesByPackage.values.toList()
        val partialMatchesByPkg = lastResolvedMatches.associateBy { it.packageName }
        viewModelScope.launch {
            val cards =
                partialCandidates
                    .mapNotNull { candidate ->
                        buildCard(candidate, partialMatchesByPkg[candidate.packageName])
                    }.toImmutableList()
            _state.update {
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

    private suspend fun buildCard(
        candidate: ExternalAppCandidate,
        match: RepoMatchResult?,
    ): CandidateUi? {
        val suggestionsDomain = match?.suggestions.orEmpty()
        val top = suggestionsDomain.maxByOrNull { it.confidence }
        val preselected =
            if (top != null && top.confidence in PRESELECT_MIN..PRESELECT_MAX) top.toUi() else null

        return CandidateUi(
            packageName = candidate.packageName,
            appLabel = candidate.appLabel,
            versionName = candidate.versionName,
            installerLabel = candidate.installerKind.toUiLabel(),
            suggestions = suggestionsDomain.take(3).map { it.toUi() }.toImmutableList(),
            preselectedSuggestion = preselected,
        )
    }

    private fun submitSearchOverride(packageName: String) {
        val current = _state.value

        if (current.activeSearchPackage != packageName) return

        val query = current.searchQuery.trim()
        if (query.isEmpty()) {
            searchJob?.cancel()
            _state.update {
                it.copy(
                    isSearching = false,
                    searchError = null,
                    searchResults = persistentListOf(),
                )
            }
            return
        }

        searchJob?.cancel()
        _state.update { it.copy(isSearching = true, searchError = null) }
        searchJob =
            viewModelScope.launch {
                val customHosts =
                    runCatching {
                        tweaksRepository.getCustomForgeHosts().first()
                    }.getOrElse { emptySet() }
                val parsed = RepositoryUrlParser.parse(query, customHosts)
                if (parsed != null) {
                    val sourceHost =
                        when (val src = parsed.source) {
                            RepositorySource.GitHub -> null
                            is RepositorySource.Forgejo -> src.host
                        }
                    _state.update {
                        if (it.activeSearchPackage != packageName) {
                            it
                        } else {
                            it.copy(
                                isSearching = false,
                                searchError = null,
                                searchResults =
                                    persistentListOf(
                                        RepoSuggestionUi(
                                            owner = parsed.owner,
                                            repo = parsed.repo,
                                            confidence = 1.0,
                                            source = SuggestionSource.MANUAL,
                                            stars = null,
                                            description = null,
                                            sourceHost = sourceHost,
                                        ),
                                    ),
                            )
                        }
                    }
                    return@launch
                }

                val result =
                    runCatching { externalImportRepository.searchRepos(query) }
                        .getOrElse { e ->
                            if (e is CancellationException) throw e
                            Result.failure(e)
                        }
                result.fold(
                    onSuccess = { suggestions ->
                        _state.update {
                            if (it.activeSearchPackage != packageName) {
                                it
                            } else {
                                it.copy(
                                    isSearching = false,
                                    searchError = null,
                                    searchResults =
                                        suggestions.map { s -> s.toUi() }.toImmutableList(),
                                )
                            }
                        }
                    },
                    onFailure = { e ->
                        if (e is CancellationException) throw e
                        logger.error("Search override failed for '$query': ${e.message}")
                        val fallback = getString(Res.string.external_import_search_error_default)
                        _state.update {
                            if (it.activeSearchPackage != packageName) {
                                it
                            } else {
                                it.copy(
                                    isSearching = false,
                                    searchError = e.message ?: fallback,
                                    searchResults = persistentListOf(),
                                )
                            }
                        }
                    },
                )
            }
    }

    private fun skipPackage(
        packageName: String,
        neverAsk: Boolean,
    ) {
        val card = _state.value.cards.firstOrNull { it.packageName == packageName } ?: return
        viewModelScope.launch {
            val snapshotResult =
                runCatching {
                    externalImportRepository.snapshotDecision(packageName)
                }
            if (snapshotResult.isFailure) {
                logger.error("Snapshot read failed for $packageName: ${snapshotResult.exceptionOrNull()?.message}")
                _events.send(
                    ExternalImportEvent.ShowError(
                        getString(Res.string.external_import_error_link_failed),
                    ),
                )
                return@launch
            }
            val snapshot = snapshotResult.getOrNull()
            val hadInstalledRow = installedAppsRepository.getAppByPackage(packageName) != null

            val ok =
                try {
                    externalImportRepository.skipPackage(packageName, neverAsk = neverAsk)
                    true
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Skip failed for $packageName: ${e.message}")
                    false
                }
            if (!ok) {
                _events.send(
                    ExternalImportEvent.ShowError(
                        getString(Res.string.external_import_error_link_failed),
                    ),
                )
                return@launch
            }

            removeCardFromState(packageName) { it.copy(skipped = it.skipped + 1) }

            pendingUndo =
                PendingUndo(
                    card = card,
                    snapshot = snapshot,
                    hadInstalledAppRowBefore = hadInstalledRow,
                    kind = PendingUndo.Kind.Skip,
                )
            _events.send(
                ExternalImportEvent.ShowUndoSnackbar(
                    getString(Res.string.external_import_undo_skipped, card.appLabel),
                ),
            )
        }
    }

    private fun pickSuggestion(
        packageName: String,
        suggestion: RepoSuggestionUi,
    ) {
        val card = _state.value.cards.firstOrNull { it.packageName == packageName } ?: return
        val preselected = card.preselectedSuggestion
        val source = if (suggestion == preselected) "preselected" else "alternative"
        val candidate = candidatesByPackage[packageName]

        viewModelScope.launch {
            if (candidate == null) {
                logger.error("Cannot materialize $packageName: candidate missing from snapshot")
                _events.send(
                    ExternalImportEvent.ShowError(
                        getString(Res.string.external_import_error_link_failed),
                    ),
                )
                return@launch
            }

            val snapshotResult =
                runCatching {
                    externalImportRepository.snapshotDecision(packageName)
                }
            if (snapshotResult.isFailure) {
                logger.error("Snapshot read failed for $packageName: ${snapshotResult.exceptionOrNull()?.message}")
                _events.send(
                    ExternalImportEvent.ShowError(
                        getString(Res.string.external_import_error_link_failed),
                    ),
                )
                return@launch
            }
            val snapshot = snapshotResult.getOrNull()
            val hadInstalledRow = installedAppsRepository.getAppByPackage(packageName) != null

            val materialized =
                materializeAndMark(
                    candidate,
                    suggestion.owner,
                    suggestion.repo,
                    source,
                    suggestion.sourceHost,
                )
            if (!materialized) {
                _events.send(
                    ExternalImportEvent.ShowError(
                        getString(Res.string.external_import_error_link_network),
                    ),
                )
                return@launch
            }
            removeCardFromState(packageName) { it.copy(manuallyLinked = it.manuallyLinked + 1) }

            pendingUndo =
                PendingUndo(
                    card = card,
                    snapshot = snapshot,
                    hadInstalledAppRowBefore = hadInstalledRow,
                    kind = PendingUndo.Kind.Link,
                )
            _events.send(
                ExternalImportEvent.ShowUndoSnackbar(
                    getString(Res.string.external_import_undo_linked, card.appLabel),
                ),
            )
        }
    }

    private fun linkCardWithPreselected(packageName: String) {
        val card = _state.value.cards.firstOrNull { it.packageName == packageName } ?: return
        val preselect = card.preselectedSuggestion
        if (preselect != null) {
            pickSuggestion(packageName, preselect)
        } else {
            toggleCardExpanded(packageName)
        }
    }

    private fun undoLast() {
        val undo = pendingUndo ?: return

        viewModelScope.launch {
            try {
                if (undo.kind == PendingUndo.Kind.Link && !undo.hadInstalledAppRowBefore) {
                    installedAppsRepository.deleteInstalledApp(undo.packageName)
                }

                if (undo.snapshot != null) {
                    externalImportRepository.restoreDecision(undo.snapshot)
                } else {
                    externalImportRepository.unlink(undo.packageName)
                }

                pendingUndo = null
                _state.update { current ->
                    if (current.cards.any { it.packageName == undo.packageName }) {
                        current
                    } else {
                        val tally =
                            when (undo.kind) {
                                PendingUndo.Kind.Link -> {
                                    current.copy(
                                        manuallyLinked = (current.manuallyLinked - 1).coerceAtLeast(0),
                                    )
                                }

                                PendingUndo.Kind.Skip -> {
                                    current.copy(
                                        skipped = (current.skipped - 1).coerceAtLeast(0),
                                    )
                                }
                            }
                        tally.copy(
                            cards = (listOf(undo.card) + current.cards).toImmutableList(),
                            phase = ImportPhase.AwaitingReview,
                            showCompletionToast = false,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Undo failed for ${undo.packageName}: ${e.message}")

                _events.send(
                    ExternalImportEvent.ShowError(
                        getString(Res.string.external_import_undo_failed),
                    ),
                )
            }
        }
    }

    private fun autoSummaryContinue() {
        val current = _state.value
        if (current.phase != ImportPhase.AutoImportSummary) return

        autoLinkedHadInstalledRow = emptyMap()
        autoLinkedPreSnapshots = emptyMap()
        if (current.cards.isNotEmpty()) {
            _state.update { it.copy(phase = ImportPhase.AwaitingReview) }
        } else {
            _state.update {
                it.copy(
                    phase = ImportPhase.Done,
                    showCompletionToast = true,
                    confettiPlayCount = it.confettiPlayCount + 1,
                )
            }
        }
    }

    private fun autoSummaryUndoAll() {
        val current = _state.value
        if (current.phase != ImportPhase.AutoImportSummary) return
        val packages = current.autoLinkedPackages.toList()
        if (packages.isEmpty()) {
            _state.update { it.copy(phase = ImportPhase.AwaitingReview) }
            return
        }

        val hadInstalledMap = autoLinkedHadInstalledRow
        val preSnapshots = autoLinkedPreSnapshots

        viewModelScope.launch {
            try {
                packages.forEach { pkg ->
                    val preSnapshot = preSnapshots[pkg]
                    val hadRowBefore = hadInstalledMap[pkg] == true
                    if (!hadRowBefore) {
                        installedAppsRepository.deleteInstalledApp(pkg)
                    }
                    if (preSnapshot != null) {
                        externalImportRepository.restoreDecision(preSnapshot)
                    } else {
                        externalImportRepository.unlink(pkg)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Bulk undo failed: ${e.message}")
                _events.send(
                    ExternalImportEvent.ShowError(
                        getString(Res.string.external_import_undo_failed),
                    ),
                )
                return@launch
            }

            pendingUndo = null
            autoLinkedHadInstalledRow = emptyMap()
            autoLinkedPreSnapshots = emptyMap()

            val matchesByPkg = lastResolvedMatches.associateBy { it.packageName }
            val restoredCards =
                packages.mapNotNull { pkg ->
                    val candidate = candidatesByPackage[pkg] ?: return@mapNotNull null
                    buildCard(candidate, matchesByPkg[pkg])
                }

            _state.update { state ->
                val merged =
                    (restoredCards + state.cards)
                        .distinctBy { it.packageName }
                        .toImmutableList()
                state.copy(
                    phase = if (merged.isEmpty()) ImportPhase.Done else ImportPhase.AwaitingReview,
                    cards = merged,
                    autoImported = 0,
                    autoLinkedPackages = persistentListOf(),
                    autoLinkedLabels = persistentListOf(),
                    showCompletionToast = merged.isEmpty(),
                    confettiPlayCount =
                        if (merged.isEmpty()) {
                            state.confettiPlayCount + 1
                        } else {
                            state.confettiPlayCount
                        },
                )
            }
        }
    }

    private fun skipRemaining() {
        val current = _state.value
        if (current.phase != ImportPhase.AwaitingReview) return
        val remaining = current.cards
        if (remaining.isEmpty()) return

        viewModelScope.launch {
            val successes = mutableSetOf<String>()
            val failures = mutableListOf<String>()
            remaining.forEach { card ->
                try {
                    externalImportRepository.skipPackage(card.packageName, neverAsk = false)
                    successes += card.packageName
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Skip-remaining failed for ${card.packageName}: ${e.message}")
                    failures += card.packageName
                }
            }

            pendingUndo = null

            val allSucceeded = failures.isEmpty()
            _state.update { state ->
                val keptCards = state.cards.filter { it.packageName !in successes }.toImmutableList()
                state.copy(
                    cards = keptCards,
                    expandedPackages = persistentSetOf(),
                    activeSearchPackage = null,
                    searchQuery = "",
                    searchResults = persistentListOf(),
                    isSearching = false,
                    searchError = null,
                    phase = if (allSucceeded && keptCards.isEmpty()) ImportPhase.Done else state.phase,
                    skipped = state.skipped + successes.size,
                    showCompletionToast = allSucceeded && keptCards.isEmpty(),
                    confettiPlayCount =
                        if (allSucceeded) {
                            state.confettiPlayCount + 1
                        } else {
                            state.confettiPlayCount
                        },
                )
            }

            if (!allSucceeded) {
                _events.send(
                    ExternalImportEvent.ShowError(
                        getString(Res.string.external_import_error_link_failed),
                    ),
                )
            }
        }
    }

    private suspend fun autoMaterialize(matches: List<RepoMatchResult>): List<String> {
        val linked = mutableListOf<String>()
        val hadInstalledRow = mutableMapOf<String, Boolean>()
        val preSnapshots = mutableMapOf<String, ExternalDecisionSnapshot?>()
        matches.forEach { result ->
            val top = result.topSuggestion ?: return@forEach
            if (top.confidence < AUTO_LINK_THRESHOLD) return@forEach
            val candidate = candidatesByPackage[result.packageName] ?: return@forEach

            val snapshotResult =
                runCatching {
                    externalImportRepository.snapshotDecision(result.packageName)
                }
            if (snapshotResult.isFailure) {
                logger.warn(
                    "Skip auto-link for ${result.packageName}: " +
                        "pre-link snapshot read failed (${snapshotResult.exceptionOrNull()?.message})",
                )
                return@forEach
            }
            val preSnapshot = snapshotResult.getOrNull()
            val pre =
                runCatching {
                    installedAppsRepository.getAppByPackage(result.packageName) != null
                }.getOrDefault(false)

            val ok =
                materializeAndMark(
                    candidate = candidate,
                    owner = top.owner,
                    repo = top.repo,
                    source = "auto-${top.source.name.lowercase()}",
                    sourceHost = top.sourceHost,
                )
            if (ok) {
                linked += result.packageName
                hadInstalledRow[result.packageName] = pre
                preSnapshots[result.packageName] = preSnapshot
            }
        }
        autoLinkedHadInstalledRow = hadInstalledRow.toMap()
        autoLinkedPreSnapshots = preSnapshots.toMap()
        return linked
    }

    private suspend fun materializeAndMark(
        candidate: ExternalAppCandidate,
        owner: String,
        repo: String,
        source: String,
        sourceHost: String? = null,
    ): Boolean {
        val repoInfo =
            try {
                appsRepository.fetchRepoInfo(owner, repo, sourceHost)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("fetchRepoInfo($owner/$repo) failed: ${e.message}")
                null
            }
        if (repoInfo == null) {
            logger.warn("Skipping link for ${candidate.packageName}: repo $owner/$repo not found")
            return false
        }

        val deviceApp = candidate.toDeviceApp()
        try {
            appsRepository.linkAppToRepo(deviceApp, repoInfo, sourceHost = sourceHost)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("linkAppToRepo failed for ${candidate.packageName}: ${e.message}")
            return false
        }

        val linkResult =
            try {
                externalImportRepository.linkManually(
                    packageName = candidate.packageName,
                    owner = owner,
                    repo = repo,
                    source = source,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        if (linkResult.isFailure) {
            logger.error(
                "external_links upsert failed for ${candidate.packageName}: " +
                    "${linkResult.exceptionOrNull()?.message}",
            )
        }
        return true
    }

    private fun ExternalAppCandidate.toDeviceApp(): DeviceApp =
        DeviceApp(
            packageName = packageName,
            appName = appLabel,
            versionName = versionName,
            versionCode = versionCode,
            signingFingerprint = signingFingerprint,
        )

    private suspend fun removeCardFromState(
        packageName: String,
        tally: (ExternalImportState) -> ExternalImportState,
    ) {
        _state.update { current ->
            val newCards = current.cards.filterNot { it.packageName == packageName }.toImmutableList()
            val tallied =
                tally(current).copy(
                    cards = newCards,
                    expandedPackages = current.expandedPackages.toPersistentSet().removing(packageName),
                    activeSearchPackage = if (current.activeSearchPackage == packageName) null else current.activeSearchPackage,
                    searchQuery = if (current.activeSearchPackage == packageName) "" else current.searchQuery,
                    searchResults = if (current.activeSearchPackage == packageName) persistentListOf() else current.searchResults,
                    isSearching = if (current.activeSearchPackage == packageName) false else current.isSearching,
                    searchError = if (current.activeSearchPackage == packageName) null else current.searchError,
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

    private fun RepoMatchSuggestion.toUi(): RepoSuggestionUi =
        RepoSuggestionUi(
            owner = owner,
            repo = repo,
            confidence = confidence,
            source =
                when (source) {
                    RepoMatchSource.MANIFEST -> SuggestionSource.MANIFEST
                    RepoMatchSource.SEARCH -> SuggestionSource.SEARCH
                    RepoMatchSource.FINGERPRINT -> SuggestionSource.FINGERPRINT
                    RepoMatchSource.MANUAL -> SuggestionSource.MANUAL
                    RepoMatchSource.FORGEJO_SEARCH -> SuggestionSource.SEARCH
                    RepoMatchSource.STARRED -> SuggestionSource.STARRED
                },
            stars = stars,
            description = description,
            sourceHost = sourceHost,
        )

    private suspend fun InstallerKind.toUiLabel(): String =

        when (this) {
            InstallerKind.STORE_OBTAINIUM -> getString(Res.string.external_import_installer_obtainium)

            InstallerKind.STORE_FDROID -> getString(Res.string.external_import_installer_fdroid)

            InstallerKind.BROWSER -> getString(Res.string.external_import_installer_browser)

            InstallerKind.SIDELOAD -> getString(Res.string.external_import_installer_sideload)

            InstallerKind.GITHUB_STORE_SELF -> getString(Res.string.external_import_installer_self)

            InstallerKind.UNKNOWN,
            InstallerKind.STORE_PLAY,
            InstallerKind.STORE_AURORA,
            InstallerKind.STORE_GALAXY,
            InstallerKind.STORE_OEM_OTHER,
            InstallerKind.SYSTEM,
            -> getString(Res.string.external_import_installer_unknown)
        }

    companion object {
        private const val AUTO_LINK_THRESHOLD = 0.85
        private const val PRESELECT_MIN = 0.5
        private const val PRESELECT_MAX = 0.85

        private const val SKIP_REVEAL_DELAY_MS = 5_000L
    }
}
