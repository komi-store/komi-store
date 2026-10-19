package zed.rainxch.apps.presentation.starred

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.apps.domain.repository.AppsRepository
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.StarredRepository
import zed.rainxch.core.domain.repository.UserSessionRepository

class StarredPickerViewModel(
    private val userSessionRepository: UserSessionRepository,
    private val starredRepository: StarredRepository,
    private val installedAppsRepository: InstalledAppsRepository,
    private val appsRepository: AppsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(StarredPickerState())
    val state =
        _state
            .map { it.withDerived() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = StarredPickerState(),
            )

    private fun StarredPickerState.withDerived(): StarredPickerState {
        val query = searchQuery.trim().lowercase()
        val filtered =
            candidates.filter { candidate ->
                if (!showWithoutApk && !candidate.hasApkRelease) return@filter false
                if (query.isBlank()) return@filter true
                candidate.owner.lowercase().contains(query) ||
                    candidate.name.lowercase().contains(query) ||
                    (candidate.description?.lowercase()?.contains(query) == true)
            }
        val sorted =
            when (sortRule) {
                StarredPickerSortRule.RecentlyStarred -> filtered.sortedByDescending { it.starredAt ?: 0L }
                StarredPickerSortRule.Alphabetical -> filtered.sortedBy { "${it.owner}/${it.name}".lowercase() }
                StarredPickerSortRule.MostStars -> filtered.sortedByDescending { it.stargazersCount }
            }
        return copy(
            apkCount = candidates.count { it.hasApkRelease },
            trackedCount = candidates.count { it.isAlreadyTracked },
            visibleCandidates = sorted.toImmutableList(),
        )
    }

    private val _events = Channel<StarredPickerEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var scanJob: Job? = null

    init {
        bootstrap()
    }

    fun onAction(action: StarredPickerAction) {
        when (action) {
            StarredPickerAction.OnNavigateBack -> {
                viewModelScope.launch { _events.send(StarredPickerEvent.NavigateBack) }
            }

            StarredPickerAction.OnRetry -> {
                bootstrap()
            }

            StarredPickerAction.OnResume -> {
                resumeScan()
            }

            is StarredPickerAction.OnSearchChange -> {
                _state.update { it.copy(searchQuery = action.query) }
            }

            is StarredPickerAction.OnSortRuleSelected -> {
                _state.update { it.copy(sortRule = action.rule) }
            }

            is StarredPickerAction.OnToggleWithoutApk -> {
                _state.update { it.copy(showWithoutApk = action.show) }
            }

            is StarredPickerAction.OnCandidateClick -> {
                viewModelScope.launch {
                    _events.send(
                        StarredPickerEvent.NavigateToDetails(
                            repoId = action.candidate.repoId,
                            owner = action.candidate.owner,
                            repo = action.candidate.name,
                        ),
                    )
                }
            }
        }
    }

    private fun bootstrap() {
        scanJob?.cancel()
        scanJob =
            viewModelScope.launch {
                val isAuthenticated = userSessionRepository.isCurrentlyUserLoggedIn()
                _state.update {
                    it.copy(
                        phase = StarredPickerState.Phase.LoadingStars,
                        isAuthenticated = isAuthenticated,
                        errorMessage = null,
                        rateLimited = false,
                    )
                }
                if (!isAuthenticated) {
                    _state.update { it.copy(phase = StarredPickerState.Phase.Empty) }
                    return@launch
                }

                runCatching { starredRepository.syncStarredRepos(forceRefresh = false) }

                val starred = starredRepository.getAllStarred().first()
                val tracked =
                    installedAppsRepository
                        .getAllInstalledApps()
                        .first()
                        .map { "${it.repoOwner.lowercase()}/${it.repoName.lowercase()}" }
                        .toSet()

                val seedCandidates =
                    starred.map { repo ->
                        StarredCandidateUi(
                            repoId = repo.repoId,
                            owner = repo.repoOwner,
                            name = repo.repoName,
                            ownerAvatarUrl = repo.repoOwnerAvatarUrl,
                            description = repo.repoDescription,
                            stargazersCount = repo.stargazersCount,
                            starredAt = repo.starredAt,
                            hasApkRelease = false,
                            isAlreadyTracked = "${repo.repoOwner.lowercase()}/${repo.repoName.lowercase()}" in tracked,
                            latestReleaseTag = null,
                        )
                    }

                _state.update {
                    it.copy(
                        phase =
                            if (seedCandidates.isEmpty()) {
                                StarredPickerState.Phase.Empty
                            } else {
                                StarredPickerState.Phase.ScanningReleases
                            },
                        totalStarred = seedCandidates.size,
                        candidates = seedCandidates.toImmutableList(),
                        scanProgress = 0,
                        scanTotal = seedCandidates.size,
                    )
                }

                if (seedCandidates.isNotEmpty()) {
                    scanReleases(seedCandidates)
                }
            }
    }

    private fun resumeScan() {
        val pending = _state.value.candidates.filter { !it.hasApkRelease && it.latestReleaseTag == null }
        if (pending.isEmpty()) {
            _state.update { it.copy(phase = StarredPickerState.Phase.Ready, rateLimited = false) }
            return
        }
        scanJob?.cancel()
        scanJob =
            viewModelScope.launch {
                _state.update {
                    it.copy(
                        phase = StarredPickerState.Phase.ScanningReleases,
                        rateLimited = false,
                        errorMessage = null,
                    )
                }
                scanReleases(pending)
            }
    }

    private suspend fun scanReleases(toScan: List<StarredCandidateUi>) {
        var processed = _state.value.scanProgress
        for (candidate in toScan) {
            try {
                val release = appsRepository.getLatestRelease(candidate.owner, candidate.name)
                val hasApk =
                    release?.assets?.any { asset ->
                        asset.name.endsWith(".apk", ignoreCase = true) ||
                            asset.name.endsWith(".apks", ignoreCase = true) ||
                            asset.name.endsWith(".xapk", ignoreCase = true) ||
                            asset.name.endsWith(".aab", ignoreCase = true)
                    } == true
                _state.update { current ->
                    val updated =
                        current.candidates.map { c ->
                            if (c.repoId == candidate.repoId) {
                                c.copy(
                                    hasApkRelease = hasApk,
                                    latestReleaseTag = release?.tagName,
                                )
                            } else {
                                c
                            }
                        }
                    current.copy(
                        candidates = updated.toImmutableList(),
                        scanProgress = ++processed,
                    )
                }
            } catch (e: RateLimitException) {
                _state.update {
                    it.copy(rateLimited = true, errorMessage = e.message)
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { current ->
                    current.copy(scanProgress = ++processed)
                }
            }
        }
        _state.update {
            it.copy(phase = StarredPickerState.Phase.Ready, rateLimited = false)
        }
    }
}
