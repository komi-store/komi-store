package zed.rainxch.repopages.presentation.model

import kotlinx.collections.immutable.ImmutableList

data class IssueDetailUi(
    val number: Int,
    val title: String,
    val authorLogin: String,
    val bodyMarkdown: String,
    val reactionThumbsUp: Int,
    val comments: ImmutableList<IssueCommentUi>,
)
