package zed.rainxch.repopages.presentation.issues

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
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
import zed.rainxch.githubstore.core.presentation.res.repo_pages_new_issue_created
import zed.rainxch.githubstore.core.presentation.res.repo_pages_new_issue_failed
import zed.rainxch.githubstore.core.presentation.res.repo_pages_new_issue_sign_in
import zed.rainxch.repopages.domain.model.IssueState
import zed.rainxch.repopages.domain.repository.RepoPagesRepository

class IssuesViewModel(
    private val owner: String,
    private val repo: String,
    private val repository: RepoPagesRepository,
    private val userSessionRepository: UserSessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(IssuesUiState())
    val state = _state.asStateFlow()

    private val _events = Channel<IssuesEvent>()
    val events = _events.receiveAsFlow()

    private var requestGeneration = 0

    init {
        load(IssueState.OPEN)
        viewModelScope.launch {
            _state.update { it.copy(isLoggedIn = userSessionRepository.isCurrentlyUserLoggedIn()) }
        }
    }

    fun onAction(action: IssuesAction) {
        when (action) {
            IssuesAction.OnBackClick -> Unit
            IssuesAction.OnDismissNewIssue -> dismissNewIssue()
            is IssuesAction.OnFilterChange -> setFilter(action.state)
            IssuesAction.OnLoadMore -> loadNextPage()
            is IssuesAction.OnNewIssueBodyChange -> onNewIssueBodyChange(action.body)
            is IssuesAction.OnNewIssueTitleChange -> onNewIssueTitleChange(action.title)
            is IssuesAction.OnOpenIssue -> Unit
            IssuesAction.OnOpenNewIssue -> openNewIssue()
            IssuesAction.OnRetry -> load(_state.value.filter)
            IssuesAction.OnSubmitNewIssue -> submitNewIssue()
        }
    }

    private fun setFilter(filter: IssueState) {
        if (_state.value.filter == filter && _state.value.errorMessage == null) return
        load(filter)
    }

    private fun loadNextPage() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || current.endReached || current.errorMessage != null) return
        loadPage(current.filter, current.page + 1, append = true, generation = requestGeneration)
    }

    private fun openNewIssue() {
        viewModelScope.launch {
            if (!userSessionRepository.isCurrentlyUserLoggedIn()) {
                _events.send(IssuesEvent.OnMessage(getString(Res.string.repo_pages_new_issue_sign_in)))
                return@launch
            }
            _state.update { it.copy(showNewIssueSheet = true) }
        }
    }

    private fun dismissNewIssue() {
        _state.update { it.copy(showNewIssueSheet = false) }
    }

    private fun onNewIssueTitleChange(text: String) {
        _state.update { it.copy(newIssueTitle = text) }
    }

    private fun onNewIssueBodyChange(text: String) {
        _state.update { it.copy(newIssueBody = text) }
    }

    private fun submitNewIssue() {
        val title = _state.value.newIssueTitle.trim()
        if (title.isEmpty() || _state.value.isCreatingIssue) return
        viewModelScope.launch {
            _state.update { it.copy(isCreatingIssue = true) }
            repository
                .createIssue(owner, repo, title, _state.value.newIssueBody.trim())
                .onSuccess {
                    _state.update {
                        it.copy(
                            isCreatingIssue = false,
                            showNewIssueSheet = false,
                            newIssueTitle = "",
                            newIssueBody = "",
                        )
                    }
                    _events.send(IssuesEvent.OnMessage(getString(Res.string.repo_pages_new_issue_created)))
                    load(IssueState.OPEN)
                }.onFailure { e ->
                    _state.update { it.copy(isCreatingIssue = false) }
                    _events.send(IssuesEvent.OnMessage(e.toUserMessage(Res.string.repo_pages_new_issue_failed)))
                }
        }
    }

    private fun load(filter: IssueState) {
        requestGeneration += 1
        val generation = requestGeneration
        _state.update {
            it.copy(
                filter = filter,
                issues = persistentListOf(),
                page = 1,
                endReached = false,
                errorMessage = null,
            )
        }
        loadPage(filter, page = 1, append = false, generation = generation)
    }

    private fun loadPage(
        filter: IssueState,
        page: Int,
        append: Boolean,
        generation: Int,
    ) {
        viewModelScope.launch {
            _state.update {
                if (append) it.copy(isLoadingMore = true) else it.copy(isLoading = true, errorMessage = null)
            }
            repository
                .getIssues(owner, repo, filter, page)
                .onSuccess { result ->
                    if (generation != requestGeneration) return@onSuccess
                    _state.update { st ->
                        val merged = if (append) st.issues + result.issues else result.issues
                        st.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            issues = merged.toPersistentList(),
                            page = page,
                            endReached = !result.hasMore,
                            errorMessage = null,
                        )
                    }
                }.onFailure { e ->
                    if (generation != requestGeneration) return@onFailure
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            errorMessage = e.toUserMessage(Res.string.failed_to_load),
                        )
                    }
                }
        }
    }
}
