package zed.rainxch.repopages.presentation.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.core.presentation.utils.toUserMessage
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_load
import zed.rainxch.repopages.domain.repository.RepoPagesRepository

class SecurityViewModel(
    private val owner: String,
    private val repo: String,
    private val repository: RepoPagesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SecurityUiState())
    val state = _state.asStateFlow()

    init {
        load()
    }

    fun onAction(action: SecurityAction) {
        when (action) {
            SecurityAction.OnBackClick -> Unit
            SecurityAction.OnRetry -> load()
        }
    }

    private fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            repository
                .getSecurityOverview(owner, repo)
                .onSuccess { overview ->
                    _state.update { it.copy(isLoading = false, overview = overview, errorMessage = null) }
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
}
