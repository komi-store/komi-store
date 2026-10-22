package zed.rainxch.repopages.presentation.issuedetail

sealed interface IssueDetailEvent {
    data class OnMessage(
        val message: String,
    ) : IssueDetailEvent
}
