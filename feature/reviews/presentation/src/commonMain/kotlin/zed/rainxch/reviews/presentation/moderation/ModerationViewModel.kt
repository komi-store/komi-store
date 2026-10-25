package zed.rainxch.reviews.presentation.moderation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.model.reviews.ModerationDecision
import zed.rainxch.core.domain.model.reviews.ModerationItem
import zed.rainxch.core.domain.model.reviews.ModerationStatus
import zed.rainxch.core.domain.repository.ReviewsRepository
import zed.rainxch.core.presentation.components.reviews.toUi
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.reviews_load_failed
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_banned_done
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_deleted_done
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_dismissed_done
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_hidden_done
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_unhidden_done
import zed.rainxch.reviews.presentation.utils.reviewMessage

class ModerationViewModel(
    private val repository: ReviewsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ModerationUiState())
    val state = _state.asStateFlow()

    private val _events = Channel<ModerationEvent>()
    val events = _events.receiveAsFlow()

    private var requestGeneration = 0

    init {
        load(ModerationStatus.REPORTED)
    }

    fun onAction(action: ModerationAction) {
        when (action) {
            ModerationAction.OnBackClick, is ModerationAction.OnOpenRepo -> {}

            ModerationAction.OnRetry -> {
                load(_state.value.status)
            }

            ModerationAction.OnLoadMore -> {
                loadNextPage()
            }

            is ModerationAction.OnStatusChange -> {
                if (action.status != _state.value.status) load(action.status)
            }

            is ModerationAction.OnDecision -> {
                if (action.decision == ModerationDecision.DELETE) {
                    _state.update { it.copy(confirm = ModerationConfirm.Delete(action.reviewId)) }
                } else {
                    decide(action.reviewId, action.decision)
                }
            }

            is ModerationAction.OnBanClick -> {
                askBan(action.reviewId)
            }

            ModerationAction.OnConfirm -> {
                confirm()
            }

            ModerationAction.OnDismissConfirm -> {
                _state.update { it.copy(confirm = null) }
            }
        }
    }

    private fun load(status: ModerationStatus) {
        requestGeneration += 1
        _state.update {
            it.copy(
                status = status,
                items = persistentListOf(),
                page = 1,
                endReached = false,
                loadMoreFailed = false,
                errorMessage = null,
            )
        }
        loadPage(page = 1, generation = requestGeneration, append = false)
    }

    // A decision takes its review out of the server's queue as well, so the first unseen review sits at
    // the position the list has reached, not after the last page fetched. Reviews already shown are
    // dropped from the answer.
    private fun loadNextPage() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || current.endReached || current.errorMessage != null) return
        _state.update { it.copy(loadMoreFailed = false) }
        loadPage(page = current.items.size / QUEUE_PAGE_SIZE + 1, generation = requestGeneration, append = true)
    }

    private fun loadPage(
        page: Int,
        generation: Int,
        append: Boolean,
    ) {
        viewModelScope.launch {
            _state.update { if (append) it.copy(isLoadingMore = true) else it.copy(isLoading = true) }
            repository
                .getModerationQueue(_state.value.status, page)
                .onSuccess { result ->
                    if (generation != requestGeneration) return@onSuccess
                    _state.update { st ->
                        val known = st.items.map { it.review.id }.toSet()
                        val fresh = result.items.filterNot { it.review.id in known }.map { it.toUi() }
                        st.copy(
                            items = (if (append) st.items + fresh else fresh).toImmutableList(),
                            page = result.page,
                            endReached = !result.hasMore,
                            isLoading = false,
                            isLoadingMore = false,
                            loadMoreFailed = false,
                        )
                    }
                }.onFailure { error ->
                    if (generation != requestGeneration) return@onFailure
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            loadMoreFailed = append,
                            errorMessage = if (append) null else getString(Res.string.reviews_load_failed),
                        )
                    }
                    if (append) message(error.reviewMessage())
                }
        }
    }

    private fun askBan(reviewId: String) {
        val item = _state.value.items.firstOrNull { it.review.id == reviewId } ?: return
        _state.update {
            it.copy(confirm = ModerationConfirm.Ban(reviewId, item.authorId, item.review.authorLogin))
        }
    }

    private fun confirm() {
        val confirm = _state.value.confirm ?: return
        _state.update { it.copy(confirm = null) }
        when (confirm) {
            is ModerationConfirm.Delete -> decide(confirm.reviewId, ModerationDecision.DELETE)
            is ModerationConfirm.Ban -> ban(confirm)
        }
    }

    // Every decision takes the review out of the queue it's in: hide and dismiss close its reports,
    // unhide moves it out of the hidden list, and delete removes it.
    private fun decide(
        reviewId: String,
        decision: ModerationDecision,
    ) {
        if (reviewId in _state.value.busyIds) return
        setBusy(reviewId, true)
        viewModelScope.launch {
            repository
                .moderate(reviewId, decision)
                .onSuccess {
                    _state.update { st -> st.copy(items = st.items.filterNot { it.review.id == reviewId }.toImmutableList()) }
                    message(decision.doneMessage())
                }.onFailure { message(it.reviewMessage()) }
            setBusy(reviewId, false)
            // Deciding the last loaded review empties the list while the queue may hold more.
            if (_state.value.items.isEmpty()) loadNextPage()
        }
    }

    private fun ban(confirm: ModerationConfirm.Ban) {
        if (confirm.reviewId in _state.value.busyIds) return
        setBusy(confirm.reviewId, true)
        viewModelScope.launch {
            repository
                .setBanned(confirm.authorId, banned = true)
                .onSuccess { message(Res.string.reviews_moderation_banned_done) }
                .onFailure { message(it.reviewMessage()) }
            setBusy(confirm.reviewId, false)
        }
    }

    private fun setBusy(
        reviewId: String,
        busy: Boolean,
    ) {
        _state.update { st ->
            st.copy(busyIds = (if (busy) st.busyIds + reviewId else st.busyIds - reviewId).toImmutableSet())
        }
    }

    private fun message(resource: StringResource) {
        viewModelScope.launch { _events.send(ModerationEvent.OnMessage(getString(resource))) }
    }

    private fun ModerationDecision.doneMessage(): StringResource =
        when (this) {
            ModerationDecision.HIDE -> Res.string.reviews_moderation_hidden_done
            ModerationDecision.UNHIDE -> Res.string.reviews_moderation_unhidden_done
            ModerationDecision.DISMISS -> Res.string.reviews_moderation_dismissed_done
            ModerationDecision.DELETE -> Res.string.reviews_moderation_deleted_done
        }

    private fun ModerationItem.toUi(): ModerationItemUi =
        ModerationItemUi(
            // The card's own hidden notice speaks to the author; the queue says it in its own words.
            review = review.toUi(viewerId = null, votes = emptyMap()).copy(isHidden = false),
            repo = repo,
            repoLabel = listOfNotNull(repo.host, repo.owner, repo.name).joinToString("/"),
            authorId = review.author.id,
            reportCount = reportCount,
            reports = reports.map { ModerationReportUi(reason = it.reason, note = it.note) }.toImmutableList(),
            hiddenReason = hiddenReason,
        )

    private companion object {
        // The queue's page size, fixed by the backend.
        const val QUEUE_PAGE_SIZE = 20
    }
}
