package zed.rainxch.home.presentation.categorylist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.failed_to_load
import zed.rainxch.home.domain.model.HomeCategory
import zed.rainxch.home.domain.repository.HomeRepository
import zed.rainxch.home.presentation.model.toHomeRepoCardUi

class CategoryListViewModel(
    private val category: HomeCategory,
    private val homeRepository: HomeRepository,
    private val tweaksRepository: TweaksRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CategoryListState(category = category))
    val state = _state.asStateFlow()

    private val _events = Channel<CategoryListEvent>()
    val events = _events.receiveAsFlow()

    private var nextPage = 1

    init {
        loadPage(initial = true)
        viewModelScope.launch {
            tweaksRepository.getRepoGridLayout().collect { grid ->
                _state.update { it.copy(isGridLayout = grid) }
            }
        }
    }

    fun onAction(action: CategoryListAction) {
        when (action) {
            CategoryListAction.OnLoadMore -> {
                if (!_state.value.isLoadingMore && _state.value.hasMorePages) loadPage(initial = false)
            }

            CategoryListAction.OnRefresh -> {
                nextPage = 1
                _state.update {
                    it.copy(
                        cards = persistentListOf(),
                        hasMorePages = true,
                        errorMessage = null,
                    )
                }
                loadPage(initial = true)
            }

            is CategoryListAction.OnRepoClick -> {
                viewModelScope.launch {
                    _events.send(CategoryListEvent.NavigateToDetails(action.repoId))
                }
            }

            CategoryListAction.OnNavigateBack -> {}
        }
    }

    private fun loadPage(initial: Boolean) {
        viewModelScope.launch {
            _state.update {
                if (initial) it.copy(isLoading = true) else it.copy(isLoadingMore = true)
            }
            val flow =
                when (category) {
                    HomeCategory.HOT_RELEASE -> homeRepository.getHotReleaseRepositories(emptySet(), nextPage)
                    HomeCategory.TRENDING -> homeRepository.getTrendingRepositories(emptySet(), nextPage)
                    HomeCategory.MOST_POPULAR -> homeRepository.getMostPopular(emptySet(), nextPage)
                }
            runCatching { flow.firstOrNull() }
                .onSuccess { paginated ->
                    if (paginated == null) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                isLoadingMore = false,
                                hasMorePages = false,
                            )
                        }
                        return@onSuccess
                    }
                    val existing = _state.value.cards
                    val incoming =
                        paginated.repos.map { repo ->
                            toHomeRepoCardUi(
                                repo = repo,
                                isInstalled = false,
                                isUpdateAvailable = false,
                                isFavourite = false,
                                isStarred = false,
                                isSeen = false,
                                isCurrentUserOwner = false,
                            )
                        }
                    val existingIds = existing.map { it.id }.toHashSet()
                    val merged = (existing + incoming.filter { it.id !in existingIds }).toImmutableList()
                    nextPage += 1
                    _state.update {
                        it.copy(
                            cards = merged,
                            isLoading = false,
                            isLoadingMore = false,
                            hasMorePages = incoming.isNotEmpty(),
                            errorMessage = null,
                        )
                    }
                }.onFailure { e ->
                    if (e is CancellationException) throw e
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            errorMessage = e.message ?: getString(Res.string.failed_to_load),
                        )
                    }
                }
        }
    }
}
