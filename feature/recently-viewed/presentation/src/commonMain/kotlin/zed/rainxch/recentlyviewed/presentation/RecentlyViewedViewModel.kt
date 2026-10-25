package zed.rainxch.recentlyviewed.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.SeenReposRepository
import zed.rainxch.core.presentation.model.installsByRepo
import zed.rainxch.core.presentation.utils.TimeZoneChangeSignal
import zed.rainxch.recentlyviewed.presentation.mappers.toRecentlyViewedRepoUi
import zed.rainxch.recentlyviewed.presentation.model.HistoryDay
import zed.rainxch.recentlyviewed.presentation.model.HistorySection
import zed.rainxch.recentlyviewed.presentation.model.RecentlyViewedRepo
import kotlin.time.Clock
import kotlin.time.Instant

class RecentlyViewedViewModel(
    private val seenReposRepository: SeenReposRepository,
    private val installedAppsRepository: InstalledAppsRepository,
) : ViewModel() {
    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(RecentlyViewedState())
    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    loadRecentlyViewed()
                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = RecentlyViewedState(),
            )

    private fun loadRecentlyViewed() {
        viewModelScope.launch {
            combine(
                seenReposRepository.getAllSeenRepos(),
                installedAppsRepository.getAllInstalledApps(),
                TimeZoneChangeSignal.revision,
            ) { repos, apps, _ ->
                val installs = apps.installsByRepo()
                repos.map { it.toRecentlyViewedRepoUi(installs[it.repoId]) }.byDay()
            }.flowOn(Dispatchers.Default)
                .collect { sections ->
                    _state.update { it.copy(sections = sections) }
                }
        }
    }

    // Newest first within each day, as the history table returns them.
    private fun List<RecentlyViewedRepo>.byDay(): ImmutableList<HistorySection> {
        val zone = TimeZone.currentSystemDefault()
        val today = Clock.System.now().toLocalDateTime(zone).date
        val byDay = groupBy { repo -> historyDayOf(repo.viewedAt, today, zone) }
        return HistoryDay.entries
            .mapNotNull { day -> byDay[day]?.let { HistorySection(day, it.toImmutableList()) } }
            .toImmutableList()
    }

    private fun historyDayOf(
        viewedAt: Long,
        today: LocalDate,
        zone: TimeZone,
    ): HistoryDay {
        val daysAgo = Instant.fromEpochMilliseconds(viewedAt).toLocalDateTime(zone).date.daysUntil(today)
        return when {
            daysAgo <= 0 -> HistoryDay.Today
            daysAgo == 1 -> HistoryDay.Yesterday
            daysAgo < DAYS_IN_WEEK -> HistoryDay.ThisWeek
            else -> HistoryDay.Earlier
        }
    }

    fun onAction(action: RecentlyViewedAction) {
        when (action) {
            RecentlyViewedAction.OnNavigateBackClick -> {}

            is RecentlyViewedAction.OnRepositoryClick -> {}

            is RecentlyViewedAction.OnDeveloperProfileClick -> {}

            is RecentlyViewedAction.OnRemoveFromHistory -> {
                viewModelScope.launch {
                    seenReposRepository.removeFromHistory(action.repo.repoId)
                }
            }

            RecentlyViewedAction.OnClearAllHistory -> {
                viewModelScope.launch {
                    seenReposRepository.clearAll()
                }
            }
        }
    }

    private companion object {
        const val DAYS_IN_WEEK = 7
    }
}
