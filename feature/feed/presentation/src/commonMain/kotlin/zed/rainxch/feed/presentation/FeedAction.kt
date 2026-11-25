package zed.rainxch.feed.presentation

import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.repository.FeedCategory
import zed.rainxch.core.presentation.model.GithubRepoSummaryUi

sealed interface FeedAction {
    sealed interface Paging : FeedAction

    sealed interface Filters : FeedAction

    sealed interface RepoAction : FeedAction

    data object OnRefresh : Paging

    data object OnRetry : Paging

    data object OnLoadMore : Paging

    data object OnSearchClick : FeedAction

    data object OnProfileClick : FeedAction

    data object OnPlatformPickerOpen : Filters

    data object OnPlatformPickerDismiss : Filters

    data object OnResetFilters : Filters

    data class OnPlatformSelected(
        val platform: DiscoveryPlatform,
    ) : Filters

    data class OnCategorySelected(
        val category: FeedCategory,
    ) : Filters

    data class OnRepoClick(
        val repo: GithubRepoSummaryUi,
    ) : FeedAction

    data class OnShareClick(
        val repo: GithubRepoSummaryUi,
    ) : RepoAction

    data class OnHideRepository(
        val repo: GithubRepoSummaryUi,
    ) : RepoAction

    data class OnMarkAsSeen(
        val repo: GithubRepoSummaryUi,
    ) : RepoAction

    data class OnMarkAsUnseen(
        val repoId: Long,
    ) : RepoAction
}
