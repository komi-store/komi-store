package zed.rainxch.home.presentation

import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.model.GithubRepoSummaryUi
import zed.rainxch.home.presentation.model.ChartTab

sealed interface HomeAction {
    sealed interface Charts : HomeAction

    sealed interface PlatformPicker : HomeAction

    sealed interface ActionSheet : HomeAction

    sealed interface RepoAction : HomeAction

    data object OnRetry : Charts

    data object OnRefresh : Charts

    data object OnLoadMore : Charts

    data object OnSearchClick : HomeAction

    data class OnChartSelected(
        val chart: ChartTab,
    ) : Charts

    data object OnPlatformPopupOpen : PlatformPicker

    data object OnPlatformPopupDismiss : PlatformPicker

    data class OnPlatformSelected(
        val platform: DiscoveryPlatform,
    ) : PlatformPicker

    data class OnRepoClick(
        val repo: GithubRepoSummaryUi,
    ) : HomeAction

    data class OnRepoLongClick(
        val repoId: Long,
    ) : ActionSheet

    data object OnActionSheetDismiss : ActionSheet

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
