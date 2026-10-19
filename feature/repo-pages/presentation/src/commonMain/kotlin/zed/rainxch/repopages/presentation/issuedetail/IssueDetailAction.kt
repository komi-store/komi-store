package zed.rainxch.repopages.presentation.issuedetail

sealed interface IssueDetailAction {
    data object OnBackClick : IssueDetailAction

    data object OnRetryClick : IssueDetailAction

    data class OnCommentChange(
        val comment: String,
    ) : IssueDetailAction

    data object OnPostComment : IssueDetailAction

    data object OnReactIssue : IssueDetailAction

    data class OnReactComment(
        val id: Long,
    ) : IssueDetailAction
}
