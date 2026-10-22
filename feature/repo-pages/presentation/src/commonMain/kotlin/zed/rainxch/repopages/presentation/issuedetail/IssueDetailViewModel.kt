package zed.rainxch.repopages.presentation.issuedetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.minus
import kotlinx.collections.immutable.plus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.presentation.utils.toUserMessage
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_load
import zed.rainxch.githubstore.core.presentation.res.repo_pages_comment_failed
import zed.rainxch.githubstore.core.presentation.res.repo_pages_comment_posted
import zed.rainxch.githubstore.core.presentation.res.repo_pages_comment_sign_in
import zed.rainxch.githubstore.core.presentation.res.repo_pages_react_failed
import zed.rainxch.githubstore.core.presentation.res.repo_pages_react_sign_in
import zed.rainxch.repopages.domain.model.RepoIssueDetail
import zed.rainxch.repopages.domain.repository.RepoPagesRepository

class IssueDetailViewModel(
    private val owner: String,
    private val repo: String,
    private val issueNumber: Int,
    private val repository: RepoPagesRepository,
    private val userSessionRepository: UserSessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(IssueDetailUiState(issueNumber = issueNumber))
    val state = _state.asStateFlow()

    private val _events = Channel<IssueDetailEvent>()
    val events = _events.receiveAsFlow()

    init {
        load()
        viewModelScope.launch {
            _state.update { it.copy(isLoggedIn = userSessionRepository.isCurrentlyUserLoggedIn()) }
        }
    }

    fun onAction(action: IssueDetailAction) {
        when (action) {
            IssueDetailAction.OnBackClick -> {}

            is IssueDetailAction.OnCommentChange -> {
                _state.update { it.copy(commentText = action.comment) }
            }

            IssueDetailAction.OnPostComment -> {
                postComment()
            }

            is IssueDetailAction.OnReactComment -> {
                reactToComment(action.id)
            }

            IssueDetailAction.OnReactIssue -> {
                reactToIssue()
            }

            IssueDetailAction.OnRetryClick -> {
                load()
            }
        }
    }

    private fun postComment() {
        val text = _state.value.commentText.trim()
        if (text.isEmpty() || _state.value.isPostingComment) return
        viewModelScope.launch {
            if (!userSessionRepository.isCurrentlyUserLoggedIn()) {
                _events.send(IssueDetailEvent.OnMessage(getString(Res.string.repo_pages_comment_sign_in)))
                return@launch
            }
            _state.update { it.copy(isPostingComment = true) }
            repository
                .addIssueComment(
                    owner = owner,
                    repo = repo,
                    number = issueNumber,
                    body = text,
                ).onSuccess { comment ->
                    _state.update { st ->
                        val detail = st.detail
                        st.copy(
                            isPostingComment = false,
                            commentText = "",
                            detail = detail?.copy(comments = detail.comments + comment),
                        )
                    }
                    _events.send(IssueDetailEvent.OnMessage(getString(Res.string.repo_pages_comment_posted)))
                }.onFailure { e ->
                    _state.update { it.copy(isPostingComment = false) }
                    _events.send(
                        IssueDetailEvent.OnMessage(
                            e.toUserMessage(Res.string.repo_pages_comment_failed),
                        ),
                    )
                }
        }
    }

    private fun reactToIssue() {
        if (_state.value.isReactingIssue) return
        viewModelScope.launch {
            if (!userSessionRepository.isCurrentlyUserLoggedIn()) {
                _events.send(IssueDetailEvent.OnMessage(getString(Res.string.repo_pages_react_sign_in)))
                return@launch
            }
            _state.update { st ->
                st.copy(
                    isReactingIssue = true,
                    detail = st.detail?.copy(reactionThumbsUp = st.detail.reactionThumbsUp + 1),
                )
            }
            repository
                .reactToIssue(owner = owner, repo = repo, number = issueNumber)
                .onFailure { e ->
                    _state.update { st ->
                        st.copy(
                            isReactingIssue = false,
                            detail =
                                st.detail?.copy(
                                    reactionThumbsUp = (st.detail.reactionThumbsUp - 1).coerceAtLeast(0),
                                ),
                        )
                    }
                    _events.send(
                        IssueDetailEvent.OnMessage(
                            e.toUserMessage(Res.string.repo_pages_react_failed),
                        ),
                    )
                }.onSuccess { created ->
                    _state.update { st ->
                        st.copy(
                            isReactingIssue = false,
                            detail =
                                if (created) {
                                    st.detail
                                } else {
                                    st.detail?.copy(
                                        reactionThumbsUp = (st.detail.reactionThumbsUp - 1).coerceAtLeast(0),
                                    )
                                },
                        )
                    }
                }
        }
    }

    private fun reactToComment(commentId: Long) {
        if (commentId in _state.value.reactingCommentIds) return
        viewModelScope.launch {
            if (!userSessionRepository.isCurrentlyUserLoggedIn()) {
                _events.send(IssueDetailEvent.OnMessage(getString(Res.string.repo_pages_react_sign_in)))
                return@launch
            }
            _state.update { st ->
                st.copy(
                    reactingCommentIds = st.reactingCommentIds + commentId,
                    detail = st.detail?.bumpComment(commentId, +1),
                )
            }
            repository
                .reactToComment(owner, repo, commentId)
                .onFailure { e ->
                    _state.update { st ->
                        st.copy(
                            reactingCommentIds = st.reactingCommentIds - commentId,
                            detail = st.detail?.bumpComment(commentId, -1),
                        )
                    }
                    _events.send(
                        IssueDetailEvent.OnMessage(
                            e.toUserMessage(Res.string.repo_pages_react_failed),
                        ),
                    )
                }.onSuccess { created ->
                    _state.update { st ->
                        st.copy(
                            reactingCommentIds = st.reactingCommentIds - commentId,
                            detail = if (created) st.detail else st.detail?.bumpComment(commentId, -1),
                        )
                    }
                }
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            repository
                .getIssueDetail(owner, repo, issueNumber)
                .onSuccess { detail ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            detail = detail,
                            errorMessage = null,
                            issueNumber = issueNumber,
                        )
                    }
                }.onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.toUserMessage(Res.string.failed_to_load),
                        )
                    }
                }
        }
    }

    private fun RepoIssueDetail.bumpComment(
        commentId: Long,
        delta: Int,
    ): RepoIssueDetail =
        copy(
            comments =
                comments.map { c ->
                    if (c.id == commentId) {
                        c.copy(reactionThumbsUp = (c.reactionThumbsUp + delta).coerceAtLeast(0))
                    } else {
                        c
                    }
                },
        )
}
