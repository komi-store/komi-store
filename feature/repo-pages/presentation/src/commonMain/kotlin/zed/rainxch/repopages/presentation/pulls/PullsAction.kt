package zed.rainxch.repopages.presentation.pulls

import zed.rainxch.repopages.domain.model.IssueState

sealed interface PullsAction {
    data object OnBackClick : PullsAction

    data object OnRetry : PullsAction

    data class OnFilterChange(
        val state: IssueState,
    ) : PullsAction

    data object OnLoadMore : PullsAction

    data class OnOpenPull(
        val pullNumber: Int,
    ) : PullsAction
}
