package zed.rainxch.repopages.presentation.issues

sealed interface IssuesEvent {
    data class OnMessage(
        val message: String,
    ) : IssuesEvent
}
