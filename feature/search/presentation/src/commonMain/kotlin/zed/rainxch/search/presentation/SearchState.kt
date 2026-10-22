package zed.rainxch.search.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.model.DiscoveryRepositoryUi
import zed.rainxch.search.presentation.model.ParsedGithubLink
import zed.rainxch.search.presentation.model.ProgrammingLanguageUi
import zed.rainxch.search.presentation.model.SearchSourceUi
import zed.rainxch.search.presentation.model.SortByUi
import zed.rainxch.search.presentation.model.SortOrderUi

data class SearchState(
    val query: String = "",
    val repositories: ImmutableList<DiscoveryRepositoryUi> = persistentListOf(),
    val visibleRepos: ImmutableList<DiscoveryRepositoryUi> = persistentListOf(),
    val selectedSearchPlatform: DiscoveryPlatform = DiscoveryPlatform.All,
    val selectedSortBy: SortByUi = SortByUi.BestMatch,
    val selectedSortOrder: SortOrderUi = SortOrderUi.Descending,
    val selectedLanguage: ProgrammingLanguageUi = ProgrammingLanguageUi.All,
    val isLoading: Boolean = false,
    val isHideSeenEnabled: Boolean = false,
    val seenRepoIds: Set<Long> = emptySet(),
    val hiddenRepoIds: Set<Long> = emptySet(),
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val hasMorePages: Boolean = true,
    val totalCount: Int? = null,
    val isLanguageSheetVisible: Boolean = false,
    val isSortByDialogVisible: Boolean = false,
    val detectedLinks: ImmutableList<ParsedGithubLink> = persistentListOf(),
    val clipboardLinks: ImmutableList<ParsedGithubLink> = persistentListOf(),
    val isClipboardBannerVisible: Boolean = false,
    val autoDetectClipboardEnabled: Boolean = true,
    val recentSearches: ImmutableList<String> = persistentListOf(),
    val exploreStatus: ExploreStatus = ExploreStatus.IDLE,
    val passthroughAttempted: Boolean? = null,
    val selectedSource: SearchSourceUi = SearchSourceUi.GitHub,
    val availableSources: ImmutableList<SearchSourceUi> =
        persistentListOf(SearchSourceUi.GitHub, SearchSourceUi.Codeberg),
    val isFiltersSheetVisible: Boolean = false,
    val activeFilterCount: Int = 0,
    val isGridLayout: Boolean = false,
) {
    enum class ExploreStatus {
        IDLE,
        LOADING,
        EXHAUSTED,
    }
}
