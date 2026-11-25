package zed.rainxch.repopages.presentation.model

data class IssueCommentUi(
    val id: Long,
    val authorLogin: String,
    val bodyMarkdown: String,
    val reactionThumbsUp: Int,
)
