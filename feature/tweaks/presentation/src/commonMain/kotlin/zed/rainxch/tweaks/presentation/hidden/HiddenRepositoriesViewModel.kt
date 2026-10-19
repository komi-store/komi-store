package zed.rainxch.tweaks.presentation.hidden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.repository.HiddenReposRepository

class HiddenRepositoriesViewModel(
    private val hiddenReposRepository: HiddenReposRepository,
    private val logger: KomiStoreLogger,
) : ViewModel() {
    private val _state = MutableStateFlow(HiddenRepositoriesState())
    val state = _state.asStateFlow()

    private val _events = Channel<HiddenRepositoriesEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        hiddenReposRepository
            .getAllHiddenRepos()
            .onEach { repos ->
                _state.value =
                    HiddenRepositoriesState(
                        isLoading = false,
                        items =
                            repos
                                .map { repo ->
                                    HiddenRepoUi(
                                        repoId = repo.repoId,
                                        repoName = repo.repoName,
                                        repoOwner = repo.repoOwner,
                                        repoOwnerAvatarUrl = repo.repoOwnerAvatarUrl,
                                    )
                                }.toImmutableList(),
                    )
            }.launchIn(viewModelScope)
    }

    fun onAction(action: HiddenRepositoriesAction) {
        when (action) {
            is HiddenRepositoriesAction.OnUnhide -> unhide(action.repoId)
            HiddenRepositoriesAction.OnUnhideAll -> unhideAll()
        }
    }

    private fun unhide(repoId: Long) {
        val fullName =
            _state.value.items
                .firstOrNull { it.repoId == repoId }
                ?.fullName
                .orEmpty()
        viewModelScope.launch {
            try {
                hiddenReposRepository.unhide(repoId)
                _events.send(HiddenRepositoriesEvent.Unhidden(fullName))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.error("HiddenRepositoriesViewModel: failed to unhide $repoId", e)
                _events.send(HiddenRepositoriesEvent.Failure)
            }
        }
    }

    private fun unhideAll() {
        viewModelScope.launch {
            try {
                hiddenReposRepository.clearAll()
                _events.send(HiddenRepositoriesEvent.UnhiddenAll)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.error("HiddenRepositoriesViewModel: failed to unhide all", e)
                _events.send(HiddenRepositoriesEvent.Failure)
            }
        }
    }
}
