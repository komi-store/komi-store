package zed.rainxch.repopages.presentation.pulls

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.repopages.domain.model.IssueState
import zed.rainxch.repopages.presentation.model.RepoPullRequestUi

data class PullsUiState(
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val filter: IssueState = IssueState.OPEN,
    val pulls: ImmutableList<RepoPullRequestUi> = persistentListOf(),
    val page: Int = 1,
    val endReached: Boolean = false,
    val errorMessage: String? = null,
)
