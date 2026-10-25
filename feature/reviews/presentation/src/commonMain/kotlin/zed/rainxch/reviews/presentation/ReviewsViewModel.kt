package zed.rainxch.reviews.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.getPlatform
import zed.rainxch.core.domain.model.reviews.CannotReviewReason
import zed.rainxch.core.domain.model.reviews.REVIEW_RATING_RANGE
import zed.rainxch.core.domain.model.reviews.RatingSummary
import zed.rainxch.core.domain.model.reviews.Review
import zed.rainxch.core.domain.model.reviews.ReviewDraft
import zed.rainxch.core.domain.model.reviews.ReviewPlatform
import zed.rainxch.core.domain.model.reviews.ReviewSort
import zed.rainxch.core.domain.model.reviews.ReviewTarget
import zed.rainxch.core.domain.model.reviews.ReviewVote
import zed.rainxch.core.domain.repository.ReviewsRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.presentation.components.reviews.toUi
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.reviews_cannot_account_too_new
import zed.rainxch.githubstore.core.presentation.res.reviews_cannot_banned
import zed.rainxch.githubstore.core.presentation.res.reviews_cannot_own_repo
import zed.rainxch.githubstore.core.presentation.res.reviews_deleted
import zed.rainxch.githubstore.core.presentation.res.reviews_error_too_long
import zed.rainxch.githubstore.core.presentation.res.reviews_load_failed
import zed.rainxch.githubstore.core.presentation.res.reviews_posted
import zed.rainxch.githubstore.core.presentation.res.reviews_reply_posted
import zed.rainxch.githubstore.core.presentation.res.reviews_report_sent
import zed.rainxch.githubstore.core.presentation.res.reviews_saved
import zed.rainxch.reviews.presentation.utils.isReviewsUnavailable
import zed.rainxch.reviews.presentation.utils.reviewMessage

class ReviewsViewModel(
    owner: String,
    repo: String,
    sourceHost: String?,
    openComposer: Boolean,
    composerRating: Int,
    private val version: String?,
    private val appLabel: String?,
    private val repository: ReviewsRepository,
    private val userSessionRepository: UserSessionRepository,
) : ViewModel() {
    private val target = ReviewTarget(owner = owner, name = repo, host = sourceHost)

    private val _state = MutableStateFlow(ReviewsUiState())
    val state = _state.asStateFlow()

    private val _events = Channel<ReviewsEvent>()
    val events = _events.receiveAsFlow()

    private var loaded: List<Review> = emptyList()
    private var summary: RatingSummary? = null
    private var mine: Review? = null
    private var votes: Map<String, ReviewVote> = emptyMap()

    // The votes the server last accepted, which a failed tap falls back to.
    private var confirmedVotes: Map<String, ReviewVote> = emptyMap()

    // One lock per review sends its votes in tap order; the tap count tells which tap is the newest.
    private val voteLocks = mutableMapOf<String, Mutex>()
    private val voteTaps = mutableMapOf<String, Int>()

    // The token decides whether the viewer is signed in. The profile only supplies their id, and a
    // failed /user call must not read as signed out.
    private var signedIn = false
    private var viewerId: Long? = null
    private var requestGeneration = 0
    private var composerPending = openComposer

    // The rating tapped on Details; it fills the first composer only.
    private var pendingRating = composerRating

    // Until /mine answers, the viewer's own review is unknown, and a composer opened then would start a
    // new review over it.
    private var mineLoaded = false

    init {
        load()
        viewModelScope.launch {
            userSessionRepository
                .isUserLoggedIn()
                .distinctUntilChanged()
                .collectLatest { loggedIn ->
                    signedIn = loggedIn
                    viewerId =
                        if (loggedIn) {
                            userSessionRepository
                                .getUser()
                                .firstOrNull()
                                ?.id
                                ?.toLong()
                        } else {
                            null
                        }
                    _state.update { it.copy(isLoggedIn = loggedIn) }
                    loadMine()
                }
        }
    }

    fun onAction(action: ReviewsAction) {
        when (action) {
            ReviewsAction.OnBackClick, ReviewsAction.OnSignInClick, ReviewsAction.OnOpenModeration -> {}

            ReviewsAction.OnRetry -> {
                load()
            }

            ReviewsAction.OnLoadMore -> {
                loadNextPage()
            }

            is ReviewsAction.OnSortChange -> {
                if (action.sort != _state.value.sort) load(sort = action.sort)
            }

            is ReviewsAction.OnRatingFilterChange -> {
                if (action.rating != _state.value.ratingFilter) load(rating = action.rating)
            }

            ReviewsAction.OnOpenComposer -> {
                openComposer()
            }

            ReviewsAction.OnDismissComposer -> {
                _state.update { it.copy(composer = null) }
            }

            is ReviewsAction.OnComposerRatingChange -> {
                updateComposer { it.copy(rating = action.rating) }
            }

            is ReviewsAction.OnComposerTextChange -> {
                updateComposer { it.copy(text = action.text.take(MAX_TEXT)) }
            }

            ReviewsAction.OnSubmitReview -> {
                submit()
            }

            ReviewsAction.OnDeleteReview -> {
                _state.update { it.copy(isConfirmingDelete = true) }
            }

            ReviewsAction.OnDismissDelete -> {
                _state.update { it.copy(isConfirmingDelete = false) }
            }

            ReviewsAction.OnConfirmDelete -> {
                deleteMine()
            }

            is ReviewsAction.OnVote -> {
                vote(action.reviewId, action.vote)
            }

            is ReviewsAction.OnOpenReport -> {
                requireSignIn { _state.update { it.copy(report = ReportState(action.reviewId)) } }
            }

            is ReviewsAction.OnReportReasonChange -> {
                _state.update { it.copy(report = it.report?.copy(reason = action.reason)) }
            }

            is ReviewsAction.OnReportNoteChange -> {
                _state.update { it.copy(report = it.report?.copy(note = action.note.take(MAX_NOTE))) }
            }

            ReviewsAction.OnSendReport -> {
                sendReport()
            }

            ReviewsAction.OnDismissReport -> {
                _state.update { it.copy(report = null) }
            }

            is ReviewsAction.OnOpenReply -> {
                openReply(action.reviewId)
            }

            is ReviewsAction.OnReplyTextChange -> {
                _state.update { it.copy(reply = it.reply?.copy(text = action.text.take(MAX_TEXT))) }
            }

            ReviewsAction.OnSendReply -> {
                sendReply()
            }

            ReviewsAction.OnDismissReply -> {
                _state.update { it.copy(reply = null) }
            }

            is ReviewsAction.OnDeleteReply -> {
                deleteReply(action.reviewId)
            }
        }
    }

    private fun load(
        sort: ReviewSort = _state.value.sort,
        rating: Int? = _state.value.ratingFilter,
    ) {
        requestGeneration += 1
        loaded = emptyList()
        _state.update {
            it.copy(
                sort = sort,
                ratingFilter = rating,
                page = 1,
                endReached = false,
                loadMoreFailed = false,
                errorMessage = null,
                isUnavailable = false,
                reviews = persistentListOf(),
            )
        }
        loadPage(page = 1, generation = requestGeneration)
    }

    private fun loadNextPage() {
        val current = _state.value
        if (current.isLoading || current.isLoadingMore || current.endReached || current.errorMessage != null) return
        _state.update { it.copy(loadMoreFailed = false) }
        loadPage(page = current.page + 1, generation = requestGeneration)
    }

    private fun loadPage(
        page: Int,
        generation: Int,
    ) {
        val append = page > 1
        viewModelScope.launch {
            _state.update { if (append) it.copy(isLoadingMore = true) else it.copy(isLoading = true) }
            val current = _state.value
            repository
                .getReviews(target, current.sort, current.ratingFilter, page)
                .onSuccess { result ->
                    if (generation != requestGeneration) return@onSuccess
                    loaded = if (append) loaded + result.reviews.filterNot { r -> loaded.any { it.id == r.id } } else result.reviews
                    summary = result.summary
                    _state.update {
                        it.copy(
                            summary = result.summary.toUi(),
                            page = result.page,
                            endReached = !result.hasMore,
                            isLoading = false,
                            isLoadingMore = false,
                            loadMoreFailed = false,
                        )
                    }
                    publishReviews()
                }.onFailure { error ->
                    if (generation != requestGeneration) return@onFailure
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            loadMoreFailed = append,
                            isUnavailable = !append && error.isReviewsUnavailable(),
                            errorMessage = if (append) null else getString(Res.string.reviews_load_failed),
                        )
                    }
                    if (append) message(error.reviewMessage())
                }
        }
    }

    private suspend fun loadMine() {
        mineLoaded = false
        if (!signedIn) {
            mine = null
            votes = emptyMap()
            confirmedVotes = emptyMap()
            _state.update {
                it.copy(mine = null, canReview = false, cannotReviewReason = null, canReply = false, isModerator = false)
            }
            publishReviews()
            return
        }
        repository
            .getMine(target)
            .onSuccess { state ->
                mineLoaded = true
                mine = state.review
                votes = state.votes
                confirmedVotes = state.votes
                viewerId = viewerId ?: state.review?.author?.id
                _state.update {
                    it.copy(
                        canReview = state.canReview,
                        cannotReviewReason = state.cannotReviewReason,
                        canReply = state.canReply,
                        isModerator = state.isModerator,
                    )
                }
                publishReviews()
                if (composerPending) {
                    composerPending = false
                    openComposer()
                }
            }.onFailure { error ->
                if (composerPending) {
                    composerPending = false
                    message(error.reviewMessage())
                }
            }
    }

    // The viewer's own review is pinned above the list, and dropped from it, because the public list is a
    // CDN copy that can lag a few minutes behind a write.
    private fun publishReviews() {
        val own = mine
        _state.update {
            it.copy(
                mine = own?.toUi(viewerId, votes),
                reviews = loaded.filterNot { r -> r.id == own?.id }.map { r -> r.toUi(viewerId, votes) }.toImmutableList(),
            )
        }
    }

    private fun openComposer() {
        // Without sign-in or /mine yet, the composer opens by itself once both are in.
        if (!signedIn || !mineLoaded) composerPending = true
        requireSignIn {
            if (!mineLoaded) {
                composerPending = true
                return@requireSignIn
            }
            val reason = _state.value.cannotReviewReason
            if (reason != null) {
                message(reason.message())
                return@requireSignIn
            }
            val own = mine
            val rating = pendingRating.takeIf { it in REVIEW_RATING_RANGE } ?: own?.rating ?: 0
            pendingRating = 0
            _state.update {
                it.copy(
                    composer =
                        ComposerState(
                            rating = rating,
                            text = own?.text.orEmpty(),
                            version = version ?: own?.version,
                            isEditing = own != null,
                        ),
                )
            }
        }
    }

    private fun updateComposer(change: (ComposerState) -> ComposerState) {
        _state.update { st -> st.copy(composer = st.composer?.let(change)) }
    }

    private fun submit() {
        val composer = _state.value.composer?.takeIf { it.rating in REVIEW_RATING_RANGE && !it.isSubmitting } ?: return
        if (composer.text.length > MAX_TEXT) {
            message(Res.string.reviews_error_too_long)
            return
        }
        val previous = mine
        viewModelScope.launch {
            updateComposer { it.copy(isSubmitting = true) }
            val draft =
                ReviewDraft(
                    rating = composer.rating,
                    text = composer.text,
                    version = composer.version,
                    platform = ReviewPlatform.valueOf(getPlatform().name),
                    appLabel = appLabel ?: mine?.appLabel,
                )
            repository
                .submit(target, draft)
                .onSuccess { review ->
                    mine = review
                    if (!review.hidden) adjustSummary(removed = previous?.rating, added = review.rating)
                    _state.update { it.copy(composer = null) }
                    publishReviews()
                    message(if (composer.isEditing) Res.string.reviews_saved else Res.string.reviews_posted)
                }.onFailure { error ->
                    updateComposer { it.copy(isSubmitting = false) }
                    message(error.reviewMessage())
                }
        }
    }

    private fun deleteMine() {
        _state.update { it.copy(isConfirmingDelete = false) }
        val previous = mine
        viewModelScope.launch {
            repository
                .deleteMine(target)
                .onSuccess {
                    if (previous != null && !previous.hidden) adjustSummary(removed = previous.rating, added = null)
                    mine = null
                    loaded = loaded.filterNot { r -> r.id == previous?.id }
                    _state.update { it.copy(composer = null) }
                    publishReviews()
                    message(Res.string.reviews_deleted)
                }.onFailure { message(it.reviewMessage()) }
        }
    }

    // The public summary is a CDN copy that lags a write by minutes, so the viewer's own change is
    // applied to it here.
    private fun adjustSummary(
        removed: Int?,
        added: Int?,
    ) {
        val current = summary ?: return
        val histogram = current.histogram.toMutableMap()
        removed?.let { histogram[it] = ((histogram[it] ?: 0) - 1).coerceAtLeast(0) }
        added?.let { histogram[it] = (histogram[it] ?: 0) + 1 }
        val count = histogram.values.sum()
        val average = if (count == 0) null else histogram.entries.sumOf { (stars, n) -> stars * n }.toDouble() / count
        val adjusted = RatingSummary(average = average, count = count, histogram = histogram)
        summary = adjusted
        _state.update { it.copy(summary = adjusted.toUi()) }
    }

    // Tapping your current vote again takes it back. Optimistic: the score and the buttons change at
    // once and roll back if the backend refuses.
    private fun vote(
        reviewId: String,
        direction: ReviewVote,
    ) {
        requireSignIn {
            // The viewer's earlier votes come with /mine; a tap before it would count from nothing.
            if (!mineLoaded) return@requireSignIn
            val previous = votes[reviewId]
            val next = direction.takeUnless { it == previous }
            applyVote(reviewId, from = previous, to = next)
            val tap = (voteTaps[reviewId] ?: 0) + 1
            voteTaps[reviewId] = tap
            val lock = voteLocks.getOrPut(reviewId) { Mutex() }
            viewModelScope.launch {
                lock.withLock {
                    repository
                        .setVote(reviewId, next)
                        .onSuccess { confirmedVotes = confirmedVotes.withVote(reviewId, next) }
                        .onFailure { error ->
                            // A newer tap is queued behind this one and settles the vote itself.
                            if (voteTaps[reviewId] != tap) return@onFailure
                            applyVote(reviewId, from = votes[reviewId], to = confirmedVotes[reviewId])
                            message(error.reviewMessage())
                        }
                }
            }
        }
    }

    private fun applyVote(
        reviewId: String,
        from: ReviewVote?,
        to: ReviewVote?,
    ) {
        votes = votes.withVote(reviewId, to)
        val delta = (to?.value ?: 0) - (from?.value ?: 0)
        loaded = loaded.map { r -> if (r.id == reviewId) r.copy(score = r.score + delta) else r }
        publishReviews()
    }

    private fun sendReport() {
        val report = _state.value.report?.takeUnless { it.isSending } ?: return
        val reason = report.reason ?: return
        viewModelScope.launch {
            _state.update { it.copy(report = report.copy(isSending = true)) }
            repository
                .report(report.reviewId, reason, report.note)
                .onSuccess {
                    _state.update { it.copy(report = null) }
                    message(Res.string.reviews_report_sent)
                }.onFailure { error ->
                    _state.update { it.copy(report = it.report?.copy(isSending = false)) }
                    message(error.reviewMessage())
                }
        }
    }

    private fun openReply(reviewId: String) {
        val existing =
            loaded
                .firstOrNull { it.id == reviewId }
                ?.reply
                ?.text
                .orEmpty()
        _state.update { it.copy(reply = ReplyState(reviewId = reviewId, text = existing)) }
    }

    private fun sendReply() {
        val reply = _state.value.reply ?: return
        if (reply.text.isBlank() || reply.isSending) return
        viewModelScope.launch {
            _state.update { it.copy(reply = reply.copy(isSending = true)) }
            repository
                .reply(reply.reviewId, reply.text)
                .onSuccess { posted ->
                    loaded = loaded.map { r -> if (r.id == reply.reviewId) r.copy(reply = posted) else r }
                    _state.update { it.copy(reply = null) }
                    publishReviews()
                    message(Res.string.reviews_reply_posted)
                }.onFailure { error ->
                    _state.update { it.copy(reply = it.reply?.copy(isSending = false)) }
                    message(error.reviewMessage())
                }
        }
    }

    private fun deleteReply(reviewId: String) {
        viewModelScope.launch {
            repository
                .deleteReply(reviewId)
                .onSuccess {
                    loaded = loaded.map { r -> if (r.id == reviewId) r.copy(reply = null) else r }
                    publishReviews()
                }.onFailure { message(it.reviewMessage()) }
        }
    }

    private fun Map<String, ReviewVote>.withVote(
        reviewId: String,
        vote: ReviewVote?,
    ): Map<String, ReviewVote> = if (vote == null) this - reviewId else this + (reviewId to vote)

    private fun requireSignIn(block: () -> Unit) {
        if (!signedIn) {
            viewModelScope.launch { _events.send(ReviewsEvent.OnNavigateToAuthentication) }
            return
        }
        block()
    }

    private fun message(resource: StringResource) {
        viewModelScope.launch { _events.send(ReviewsEvent.OnMessage(getString(resource))) }
    }

    private fun CannotReviewReason.message(): StringResource =
        when (this) {
            CannotReviewReason.ACCOUNT_TOO_NEW -> Res.string.reviews_cannot_account_too_new
            CannotReviewReason.OWN_REPO -> Res.string.reviews_cannot_own_repo
            CannotReviewReason.BANNED -> Res.string.reviews_cannot_banned
        }

    private companion object {
        const val MAX_TEXT = 2000
        const val MAX_NOTE = 500
    }
}
