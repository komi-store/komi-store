package zed.rainxch.recentlyviewed.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.repository.SeenReposRepository
import zed.rainxch.core.presentation.utils.TimeZoneChangeSignal
import zed.rainxch.recentlyviewed.presentation.mappers.toRecentlyViewedRepoUi

class RecentlyViewedViewModel(
    private val seenReposRepository: SeenReposRepository,
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
                TimeZoneChangeSignal.revision,
            ) { repos, _ -> repos }
                .map { repos -> repos.map { it.toRecentlyViewedRepoUi() } }
                .flowOn(Dispatchers.Default)
                .collect { repos ->
                    _state.update {
                        it.copy(repositories = repos.toImmutableList())
                    }
                }
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
}
