package zed.rainxch.repopages.presentation.model

import kotlinx.collections.immutable.ImmutableList
import zed.rainxch.repopages.domain.model.IssueState

data class RepoIssueUi(
    val issueId: Int,
    val title: String,
    val state: IssueState,
    val authorLogin: String,
    val labels: ImmutableList<IssueLabelUi>,
)
