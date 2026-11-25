package zed.rainxch.search.presentation

import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.model.GithubRepoSummaryUi
import zed.rainxch.search.presentation.model.ProgrammingLanguageUi
import zed.rainxch.search.presentation.model.SearchSourceUi
import zed.rainxch.search.presentation.model.SortByUi
import zed.rainxch.search.presentation.model.SortOrderUi

sealed interface SearchAction {
    sealed interface Results : SearchAction

    sealed interface Filters : SearchAction

    sealed interface Links : SearchAction

    sealed interface RepoCard : SearchAction

    sealed interface Navigation : SearchAction

    data class OnSearchChange(
        val query: String,
    ) : Results

    data class OnPlatformTypeSelected(
        val searchPlatform: DiscoveryPlatform,
    ) : Filters

    data class OnSourceSelected(
        val source: SearchSourceUi,
    ) : Filters

    data class OnLanguageSelected(
        val language: ProgrammingLanguageUi,
    ) : Filters

    data class OnSortBySelected(
        val sortBy: SortByUi,
    ) : Filters

    data class OnSortOrderSelected(
        val sortOrder: SortOrderUi,
    ) : Filters

    data class OnRepositoryClick(
        val repository: GithubRepoSummaryUi,
    ) : Navigation

    data class OnRepositoryDeveloperClick(
        val username: String,
    ) : Navigation

    data class OnShareClick(
        val repo: GithubRepoSummaryUi,
    ) : RepoCard

    data class OpenGithubLink(
        val owner: String,
        val repo: String,
    ) : Links

    data object OnSearchImeClick : Results

    data object OnNavigateBackClick : Navigation

    data object LoadMore : Results

    data object OnClearClick : Results

    data object Retry : Results

    data object OnToggleLanguageSheetVisibility : Filters

    data object OnToggleSortByDialogVisibility : Filters

    data object OnFabClick : Links

    data object OnToggleFiltersSheet : Filters

    data object DismissClipboardBanner : Links

    data class OnHistoryItemClick(
        val query: String,
    ) : Results

    data class OnRemoveHistoryItem(
        val query: String,
    ) : SearchAction

    data object OnClearAllHistory : SearchAction

    data object ExploreFromGithub : Results

    data object OnDisableHideSeenForResults : Filters

    data class OnHideRepository(
        val repo: GithubRepoSummaryUi,
    ) : RepoCard

    data class OnUndoHideRepository(
        val repoId: Long,
    ) : RepoCard

    data class OnMarkAsSeen(
        val repo: GithubRepoSummaryUi,
    ) : RepoCard

    data class OnMarkAsUnseen(
        val repoId: Long,
    ) : RepoCard
}
