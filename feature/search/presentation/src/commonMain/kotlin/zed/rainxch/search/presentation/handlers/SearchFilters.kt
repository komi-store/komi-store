package zed.rainxch.search.presentation.handlers

import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.core.domain.isDesktop
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.repository.BrowseFilterStore
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.search.presentation.SearchAction
import zed.rainxch.search.presentation.SearchState
import zed.rainxch.search.presentation.model.SearchSourceUi

// The filters and display settings that shape the results; a changed filter starts a new search through onChange.
class SearchFilters(
    private val tweaksRepository: TweaksRepository,
    private val browseFilterStore: BrowseFilterStore,
) {
    internal fun observe(
        store: SearchStore,
        onChange: () -> Unit,
    ) {
        store.observe(tweaksRepository.getHideSeenEnabled()) { copy(isHideSeenEnabled = it) }
        store.observe(tweaksRepository.getRepoGridLayout()) { copy(isGridLayout = it) }
        store.observe(tweaksRepository.getCustomForgeHosts()) { withCustomForges(it) }
        if (isDesktop()) {
            store.launch { browseFilterStore.platform.collect { selectPlatform(store, it, onChange) } }
        }
    }

    internal fun onAction(
        store: SearchStore,
        action: SearchAction.Filters,
        onChange: () -> Unit,
    ) {
        when (action) {
            is SearchAction.OnPlatformTypeSelected -> selectPlatform(store, action.searchPlatform, onChange)
            is SearchAction.OnSourceSelected -> change(store, onChange) { it.copy(selectedSource = action.source) }
            is SearchAction.OnLanguageSelected -> change(store, onChange) { it.copy(selectedLanguage = action.language) }
            is SearchAction.OnSortBySelected -> change(store, onChange) { it.copy(selectedSortBy = action.sortBy) }
            is SearchAction.OnSortOrderSelected -> change(store, onChange) { it.copy(selectedSortOrder = action.sortOrder) }
            SearchAction.OnToggleLanguageSheetVisibility -> store.update { it.copy(isLanguageSheetVisible = !it.isLanguageSheetVisible) }
            SearchAction.OnToggleSortByDialogVisibility -> store.update { it.copy(isSortByDialogVisible = !it.isSortByDialogVisible) }
            SearchAction.OnToggleFiltersSheet -> store.update { it.copy(isFiltersSheetVisible = !it.isFiltersSheetVisible) }
            SearchAction.OnDisableHideSeenForResults -> store.launch { tweaksRepository.setHideSeenEnabled(false) }
        }
    }

    private fun selectPlatform(
        store: SearchStore,
        platform: DiscoveryPlatform,
        onChange: () -> Unit,
    ) = change(store, onChange) { it.copy(selectedSearchPlatform = platform) }

    private fun change(
        store: SearchStore,
        onChange: () -> Unit,
        transform: (SearchState) -> SearchState,
    ) {
        if (transform(store.current) == store.current) return
        store.update(transform)
        onChange()
    }

    // A custom forge that has since been removed falls back to GitHub.
    private fun SearchState.withCustomForges(hosts: Set<String>): SearchState {
        val extra =
            hosts
                .filter { it.isNotBlank() && !it.equals(CODEBERG_HOST, ignoreCase = true) }
                .sorted()
                .map { SearchSourceUi.CustomForge(it) }
        val all = (listOf(SearchSourceUi.GitHub, SearchSourceUi.Codeberg) + extra).toImmutableList()
        return copy(
            availableSources = all,
            selectedSource = if (selectedSource in all) selectedSource else SearchSourceUi.GitHub,
        )
    }

    private companion object {
        const val CODEBERG_HOST = "codeberg.org"
    }
}
