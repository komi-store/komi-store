package zed.rainxch.home.presentation

import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.model.GithubRepoSummaryUi
import zed.rainxch.home.presentation.model.ChartTab

sealed interface HomeAction {
    data object OnRetry : HomeAction

    data object OnRefresh : HomeAction

    data object OnLoadMore : HomeAction

    data class OnChartSelected(
        val chart: ChartTab,
    ) : HomeAction

    data object OnPlatformPopupOpen : HomeAction

    data object OnPlatformPopupDismiss : HomeAction

    data class OnPlatformSelected(
        val platform: DiscoveryPlatform,
    ) : HomeAction

    data class OnRepoClick(
        val repo: GithubRepoSummaryUi,
    ) : HomeAction

    data class OnRepoLongClick(
        val repoId: Long,
    ) : HomeAction

    data object OnActionSheetDismiss : HomeAction

    data class OnShareClick(
        val repo: GithubRepoSummaryUi,
    ) : HomeAction

    data class OnHideRepository(
        val repo: GithubRepoSummaryUi,
    ) : HomeAction

    data class OnMarkAsSeen(
        val repo: GithubRepoSummaryUi,
    ) : HomeAction

    data class OnMarkAsUnseen(
        val repoId: Long,
    ) : HomeAction
}
