package zed.rainxch.repopages.presentation.issuedetail

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import zed.rainxch.repopages.presentation.model.IssueDetailUi

data class IssueDetailUiState(
    val isLoading: Boolean = false,
    val detail: IssueDetailUi? = null,
    val errorMessage: String? = null,
    val issueNumber: Int = 0,
    val isLoggedIn: Boolean = false,
    val commentText: String = "",
    val isPostingComment: Boolean = false,
    val isReactingIssue: Boolean = false,
    val reactingCommentIds: PersistentSet<Long> = persistentSetOf(),
)
