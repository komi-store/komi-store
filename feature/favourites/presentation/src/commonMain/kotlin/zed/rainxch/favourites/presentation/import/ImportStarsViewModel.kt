@file:OptIn(ExperimentalTime::class)

package zed.rainxch.favourites.presentation.import

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.StarredRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class ImportStarsViewModel(
    private val starredRepository: StarredRepository,
    private val favouritesRepository: FavouritesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ImportStarsState())
    val state =
        _state
            .map { it.withDerived() }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = ImportStarsState(),
            )

    private val _events = Channel<ImportStarsEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var importJob: Job? = null

    private fun ImportStarsState.withDerived(): ImportStarsState {
        val query = searchQuery.trim().lowercase()
        val filtered =
            if (query.isBlank()) {
                candidates
            } else {
                candidates
                    .filter { candidate ->
                        candidate.name.lowercase().contains(query) ||
                            candidate.owner.lowercase().contains(query) ||
                            (candidate.description?.lowercase()?.contains(query) == true)
                    }.toImmutableList()
            }
        return copy(
            filteredCandidates = filtered,
            pendingCount = candidates.count { !it.isAlreadyFavourited },
        )
    }

    fun onAction(action: ImportStarsAction) {
        when (action) {
            ImportStarsAction.OnNavigateBack -> {
                viewModelScope.launch { _events.send(ImportStarsEvent.NavigateBack) }
            }

            is ImportStarsAction.OnUsernameQueryChange -> {
                _state.update { it.copy(usernameQuery = action.query, errorMessage = null) }
            }

            ImportStarsAction.OnImportClick -> {
                importByUsername()
            }

            ImportStarsAction.OnResetImport -> {
                importJob?.cancel()
                _state.update {
                    it.copy(
                        phase = ImportStarsState.Phase.UsernameInput,
                        candidates = persistentListOf(),
                        importedUsername = null,
                        searchQuery = "",
                        errorMessage = null,
                        isImporting = false,
                        isBulkAdding = false,
                    )
                }
            }

            is ImportStarsAction.OnSearchChange -> {
                _state.update { it.copy(searchQuery = action.query) }
            }

            ImportStarsAction.OnClearSearch -> {
                _state.update { it.copy(searchQuery = "") }
            }

            is ImportStarsAction.OnToggleFavourite -> {
                toggleFavourite(action.candidate)
            }

            ImportStarsAction.OnAddAll -> {
                addAll()
            }

            is ImportStarsAction.OnCandidateClick -> {
                viewModelScope.launch {
                    _events.send(
                        ImportStarsEvent.NavigateToDetails(
                            repoId = action.candidate.repoId,
                            owner = action.candidate.owner,
                            repo = action.candidate.name,
                        ),
                    )
                }
            }
        }
    }

    private fun importByUsername() {
        val username =
            _state.value.usernameQuery
                .trim()
                .trimStart('@')
        if (username.isEmpty()) return

        importJob?.cancel()
        importJob =
            viewModelScope.launch {
                _state.update { it.copy(isImporting = true, errorMessage = null) }

                val favouritedIds =
                    favouritesRepository
                        .getAllFavorites()
                        .first()
                        .map { it.repoId }
                        .toSet()

                val result = starredRepository.fetchStarredForUsername(username)

                ensureActive()

                result.fold(
                    onSuccess = { repos ->
                        showCandidates(username, repos.map { it.toImportCandidateUi(it.repoId in favouritedIds) })
                    },
                    onFailure = ::showImportFailure,
                )
            }
    }

    private fun showCandidates(
        username: String,
        candidates: List<ImportCandidateUi>,
    ) {
        _state.update {
            it.copy(
                isImporting = false,
                importedUsername = username,
                candidates = candidates.toImmutableList(),
                phase = ImportStarsState.Phase.Results,
            )
        }
    }

    private fun showImportFailure(error: Throwable) {
        if (error is CancellationException) throw error
        _state.update {
            it.copy(
                isImporting = false,
                errorMessage = error.message ?: "Failed to import stars",
            )
        }
    }

    private fun toggleFavourite(candidate: ImportCandidateUi) {
        viewModelScope.launch {
            if (!toggled(candidate, Clock.System.now().toEpochMilliseconds())) return@launch

            _state.update { current ->
                val updated =
                    current.candidates.map { candidateUi ->
                        if (candidateUi.repoId == candidate.repoId) {
                            candidateUi.copy(isAlreadyFavourited = !candidate.isAlreadyFavourited)
                        } else {
                            candidateUi
                        }
                    }
                current.copy(candidates = updated.toImmutableList())
            }
        }
    }

    private fun addAll() {
        viewModelScope.launch {
            val pending = _state.value.candidates.filter { !it.isAlreadyFavourited }

            if (pending.isEmpty()) return@launch

            _state.update { it.copy(isBulkAdding = true) }

            val now = Clock.System.now().toEpochMilliseconds()
            val addedIds = mutableSetOf<Long>()

            for (candidate in pending) {
                if (toggled(candidate, now)) addedIds += candidate.repoId
            }

            _state.update { current ->
                val updated =
                    current.candidates.map { candidateUi ->
                        if (candidateUi.repoId in addedIds) {
                            candidateUi.copy(isAlreadyFavourited = true)
                        } else {
                            candidateUi
                        }
                    }

                current.copy(
                    candidates = updated.toImmutableList(),
                    isBulkAdding = false,
                )
            }
        }
    }

    // False when Room refused the write; the candidate then keeps its old mark.
    private suspend fun toggled(
        candidate: ImportCandidateUi,
        now: Long,
    ): Boolean =
        try {
            favouritesRepository.toggleFavorite(candidate.toFavoriteRepo(now))
            true
        } catch (_: LocalStoreException) {
            false
        }
}
