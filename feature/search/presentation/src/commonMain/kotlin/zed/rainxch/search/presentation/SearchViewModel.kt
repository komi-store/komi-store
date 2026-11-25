package zed.rainxch.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.search.domain.repository.SearchHistoryRepository
import zed.rainxch.search.presentation.handlers.ClipboardLinks
import zed.rainxch.search.presentation.handlers.RepoCardActions
import zed.rainxch.search.presentation.handlers.SearchFilters
import zed.rainxch.search.presentation.handlers.SearchResults
import zed.rainxch.search.presentation.handlers.SearchStore

class SearchViewModel(
    private val results: SearchResults,
    private val filters: SearchFilters,
    private val links: ClipboardLinks,
    private val cardActions: RepoCardActions,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val initialPlatform: DiscoveryPlatform? = null,
) : ViewModel() {
    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(SearchState())
    private val _events = Channel<SearchEvent>(capacity = Channel.BUFFERED)
    private val store = SearchStore(_state, _events, viewModelScope)

    val state: StateFlow<SearchState> =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    initialPlatform?.let { platform ->
                        _state.update { it.copy(selectedSearchPlatform = platform) }
                    }
                    results.observe(store)
                    cardActions.observe(store)
                    filters.observe(store, onChange = { results.newSearch(store) })
                    links.observe(store)
                    store.observe(searchHistoryRepository.getRecentSearches()) { copy(recentSearches = it.toImmutableList()) }
                    hasLoadedInitialData = true
                }
            }.map { it.withDerived() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = SearchState(),
            )

    val events: Flow<SearchEvent> = _events.receiveAsFlow()

    fun onAction(action: SearchAction) {
        when (action) {
            is SearchAction.Results -> results.onAction(store, action)
            is SearchAction.Filters -> filters.onAction(store, action, onChange = { results.newSearch(store) })
            is SearchAction.Links -> links.onAction(store, action)
            is SearchAction.RepoCard -> cardActions.onAction(store, action)
            is SearchAction.OnRemoveHistoryItem -> store.launch { searchHistoryRepository.removeSearch(action.query) }
            SearchAction.OnClearAllHistory -> store.launch { searchHistoryRepository.clearAll() }
            is SearchAction.Navigation -> Unit
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
