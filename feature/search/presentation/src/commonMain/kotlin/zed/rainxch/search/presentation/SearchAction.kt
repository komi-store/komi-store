package zed.rainxch.search.presentation

import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.model.GithubRepoSummaryUi
import zed.rainxch.search.presentation.model.ProgrammingLanguageUi
import zed.rainxch.search.presentation.model.SearchSourceUi
import zed.rainxch.search.presentation.model.SortByUi
import zed.rainxch.search.presentation.model.SortOrderUi

sealed interface SearchAction {
    data class OnSearchChange(
        val query: String,
    ) : SearchAction

    data class OnPlatformTypeSelected(
        val searchPlatform: DiscoveryPlatform,
    ) : SearchAction

    data class OnSourceSelected(
        val source: SearchSourceUi,
    ) : SearchAction

    data class OnLanguageSelected(
        val language: ProgrammingLanguageUi,
    ) : SearchAction

    data class OnSortBySelected(
        val sortBy: SortByUi,
    ) : SearchAction

    data class OnSortOrderSelected(
        val sortOrder: SortOrderUi,
    ) : SearchAction

    data class OnRepositoryClick(
        val repository: GithubRepoSummaryUi,
    ) : SearchAction

    data class OnRepositoryDeveloperClick(
        val username: String,
    ) : SearchAction

    data class OnShareClick(
        val repo: GithubRepoSummaryUi,
    ) : SearchAction

    data class OpenGithubLink(
        val owner: String,
        val repo: String,
    ) : SearchAction

    data object OnSearchImeClick : SearchAction

    data object OnNavigateBackClick : SearchAction

    data object LoadMore : SearchAction

    data object OnClearClick : SearchAction

    data object Retry : SearchAction

    data object OnToggleLanguageSheetVisibility : SearchAction

    data object OnToggleSortByDialogVisibility : SearchAction

    data object OnFabClick : SearchAction

    data object OnToggleFiltersSheet : SearchAction

    data object DismissClipboardBanner : SearchAction

    data class OnHistoryItemClick(
        val query: String,
    ) : SearchAction

    data class OnRemoveHistoryItem(
        val query: String,
    ) : SearchAction

    data object OnClearAllHistory : SearchAction

    data object ExploreFromGithub : SearchAction

    data object OnDisableHideSeenForResults : SearchAction

    data class OnHideRepository(
        val repo: GithubRepoSummaryUi,
    ) : SearchAction

    data class OnUndoHideRepository(
        val repoId: Long,
    ) : SearchAction

    data class OnMarkAsSeen(
        val repo: GithubRepoSummaryUi,
    ) : SearchAction

    data class OnMarkAsUnseen(
        val repoId: Long,
    ) : SearchAction
}
