package zed.rainxch.repopages.presentation.model

import zed.rainxch.repopages.domain.model.PullRequestState

data class RepoPullRequestUi(
    val number: Int,
    val title: String,
    val state: PullRequestState,
    val authorLogin: String,
    val isDraft: Boolean,
)
