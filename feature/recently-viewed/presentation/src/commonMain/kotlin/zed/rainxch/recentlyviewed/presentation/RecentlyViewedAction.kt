package zed.rainxch.recentlyviewed.presentation

import zed.rainxch.recentlyviewed.presentation.model.RecentlyViewedRepo

sealed interface RecentlyViewedAction {
    data object OnNavigateBackClick : RecentlyViewedAction

    data class OnRepositoryClick(
        val repo: RecentlyViewedRepo,
    ) : RecentlyViewedAction

    data class OnDeveloperProfileClick(
        val username: String,
    ) : RecentlyViewedAction

    data class OnRemoveFromHistory(
        val repo: RecentlyViewedRepo,
    ) : RecentlyViewedAction

    data object OnClearAllHistory : RecentlyViewedAction
}
