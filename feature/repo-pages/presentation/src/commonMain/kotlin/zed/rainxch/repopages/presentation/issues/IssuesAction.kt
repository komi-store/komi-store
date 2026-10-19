package zed.rainxch.repopages.presentation.issues

import zed.rainxch.repopages.domain.model.IssueState

sealed interface IssuesAction {
    data object OnBackClick : IssuesAction

    data object OnRetry : IssuesAction

    data class OnFilterChange(
        val state: IssueState,
    ) : IssuesAction

    data object OnLoadMore : IssuesAction

    data class OnOpenIssue(
        val issueNumber: Int,
    ) : IssuesAction

    data object OnOpenNewIssue : IssuesAction

    data object OnDismissNewIssue : IssuesAction

    data class OnNewIssueTitleChange(
        val title: String,
    ) : IssuesAction

    data class OnNewIssueBodyChange(
        val body: String,
    ) : IssuesAction

    data object OnSubmitNewIssue : IssuesAction
}
