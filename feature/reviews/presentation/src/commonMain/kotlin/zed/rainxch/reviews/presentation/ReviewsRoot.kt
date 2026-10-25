package zed.rainxch.reviews.presentation

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RateReview
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.reviews.CannotReviewReason
import zed.rainxch.core.domain.model.reviews.ReviewSort
import zed.rainxch.core.domain.model.reviews.ReviewVote
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.bars.KomiTopBarSize
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiFab
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.buttons.KomiIconButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiSegmented
import zed.rainxch.core.presentation.components.buttons.KomiSegmentedItem
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.chips.KomiChipSize
import zed.rainxch.core.presentation.components.overlays.KomiDialog
import zed.rainxch.core.presentation.components.overlays.KomiToastState
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.reviews.RatingSummaryPanel
import zed.rainxch.core.presentation.components.reviews.ReviewCard
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.reviews_action_delete
import zed.rainxch.githubstore.core.presentation.res.reviews_cancel
import zed.rainxch.githubstore.core.presentation.res.reviews_cannot_account_too_new
import zed.rainxch.githubstore.core.presentation.res.reviews_cannot_banned
import zed.rainxch.githubstore.core.presentation.res.reviews_cannot_own_repo
import zed.rainxch.githubstore.core.presentation.res.reviews_delete_body
import zed.rainxch.githubstore.core.presentation.res.reviews_delete_title
import zed.rainxch.githubstore.core.presentation.res.reviews_edit_yours
import zed.rainxch.githubstore.core.presentation.res.reviews_empty
import zed.rainxch.githubstore.core.presentation.res.reviews_empty_filtered
import zed.rainxch.githubstore.core.presentation.res.reviews_filter_stars
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_open
import zed.rainxch.githubstore.core.presentation.res.reviews_retry
import zed.rainxch.githubstore.core.presentation.res.reviews_screen_title
import zed.rainxch.githubstore.core.presentation.res.reviews_sign_in
import zed.rainxch.githubstore.core.presentation.res.reviews_sort_highest
import zed.rainxch.githubstore.core.presentation.res.reviews_sort_lowest
import zed.rainxch.githubstore.core.presentation.res.reviews_sort_newest
import zed.rainxch.githubstore.core.presentation.res.reviews_sort_top
import zed.rainxch.githubstore.core.presentation.res.reviews_unavailable
import zed.rainxch.githubstore.core.presentation.res.reviews_write
import zed.rainxch.reviews.presentation.components.ComposerSheet
import zed.rainxch.reviews.presentation.components.ReplySheet
import zed.rainxch.reviews.presentation.components.ReportSheet
import zed.rainxch.reviews.presentation.components.ReviewsEmpty

@Composable
fun ReviewsRoot(
    onNavigateBack: () -> Unit,
    onNavigateToAuthentication: () -> Unit,
    onNavigateToModeration: () -> Unit,
    viewModel: ReviewsViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberKomiToastState()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ReviewsEvent.OnMessage -> toastState.show(event.message)
            ReviewsEvent.OnNavigateToAuthentication -> onNavigateToAuthentication()
        }
    }

    state.composer?.let { composer ->
        ComposerSheet(
            composer = composer,
            maxLength = MAX_REVIEW_LENGTH,
            onRatingChange = { viewModel.onAction(ReviewsAction.OnComposerRatingChange(it)) },
            onTextChange = { viewModel.onAction(ReviewsAction.OnComposerTextChange(it)) },
            onSubmit = { viewModel.onAction(ReviewsAction.OnSubmitReview) },
            onDelete = { viewModel.onAction(ReviewsAction.OnDeleteReview) },
            onDismiss = { viewModel.onAction(ReviewsAction.OnDismissComposer) },
        )
    }
    state.report?.let { report ->
        ReportSheet(
            report = report,
            onReasonChange = { viewModel.onAction(ReviewsAction.OnReportReasonChange(it)) },
            onNoteChange = { viewModel.onAction(ReviewsAction.OnReportNoteChange(it)) },
            onSend = { viewModel.onAction(ReviewsAction.OnSendReport) },
            onDismiss = { viewModel.onAction(ReviewsAction.OnDismissReport) },
        )
    }
    state.reply?.let { reply ->
        ReplySheet(
            reply = reply,
            onTextChange = { viewModel.onAction(ReviewsAction.OnReplyTextChange(it)) },
            onSend = { viewModel.onAction(ReviewsAction.OnSendReply) },
            onDismiss = { viewModel.onAction(ReviewsAction.OnDismissReply) },
        )
    }
    if (state.isConfirmingDelete) {
        DeleteReviewDialog(
            onConfirm = { viewModel.onAction(ReviewsAction.OnConfirmDelete) },
            onDismiss = { viewModel.onAction(ReviewsAction.OnDismissDelete) },
        )
    }

    ReviewsScreen(
        state = state,
        toastState = toastState,
        onAction = { action ->
            when (action) {
                ReviewsAction.OnBackClick -> onNavigateBack()
                ReviewsAction.OnOpenModeration -> onNavigateToModeration()
                else -> viewModel.onAction(action)
            }
        },
    )
}

@Composable
private fun ReviewsScreen(
    state: ReviewsUiState,
    toastState: KomiToastState,
    onAction: (ReviewsAction) -> Unit,
) {
    KomiScaffold(
        topBar = {
            KomiTopBar(
                title = stringResource(Res.string.reviews_screen_title),
                size = KomiTopBarSize.Compact,
                onBack = { onAction(ReviewsAction.OnBackClick) },
                actions =
                    if (state.isModerator) {
                        {
                            KomiIconButton(
                                icon = Icons.Rounded.Shield,
                                contentDescription = stringResource(Res.string.reviews_moderation_open),
                                onClick = { onAction(ReviewsAction.OnOpenModeration) },
                                variant = KomiButtonVariant.Tonal,
                            )
                        }
                    } else {
                        null
                    },
            )
        },
        toastState = toastState,
        floatingActionButton = {
            if (!state.isUnavailable && state.cannotReviewReason == null) {
                val label =
                    stringResource(
                        when {
                            !state.isLoggedIn -> Res.string.reviews_sign_in
                            state.mine != null -> Res.string.reviews_edit_yours
                            else -> Res.string.reviews_write
                        },
                    )
                KomiFab(
                    onClick = { onAction(ReviewsAction.OnOpenComposer) },
                    icon = if (state.mine != null) Icons.Rounded.Edit else Icons.Rounded.RateReview,
                    contentDescription = label,
                    label = label,
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when {
                state.isLoading && state.reviews.isEmpty() && state.mine == null -> {
                    Centered(Modifier.fillMaxSize()) { KomiCircularProgress() }
                }

                state.isUnavailable -> {
                    Centered(Modifier.fillMaxSize()) { Message(Res.string.reviews_unavailable) }
                }

                state.errorMessage != null -> {
                    Centered(Modifier.fillMaxSize()) {
                        ErrorBlock(state.errorMessage, onRetry = { onAction(ReviewsAction.OnRetry) })
                    }
                }

                else -> {
                    ReviewsList(state = state, onAction = onAction)
                }
            }
        }
    }
}

@Composable
private fun ReviewsList(
    state: ReviewsUiState,
    onAction: (ReviewsAction) -> Unit,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val last =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            last >= listState.layoutInfo.totalItemsCount - LOAD_MORE_THRESHOLD
        }
    }
    val currentOnAction by rememberUpdatedState(onAction)
    // isLoadingMore is a key so a page that added nothing new asks for the next one; after a failure the
    // retry item asks instead, or this would retry forever.
    LaunchedEffect(nearEnd, state.reviews.size, state.isLoadingMore, state.loadMoreFailed) {
        if (nearEnd && !state.loadMoreFailed) currentOnAction(ReviewsAction.OnLoadMore)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.summary?.takeIf { it.count > 0 }?.let { summary ->
            item(key = "summary") {
                RatingSummaryPanel(
                    summary = summary,
                    selectedStars = state.ratingFilter,
                    onStarsClick = { stars ->
                        onAction(ReviewsAction.OnRatingFilterChange(stars.takeUnless { it == state.ratingFilter }))
                    },
                )
            }
        }
        state.cannotReviewReason?.let { reason ->
            item(key = "cannot") { Message(reason.message()) }
        }
        item(key = "filters") { Filters(state = state, onAction = onAction) }
        state.pinnedMine?.let { own ->
            item(key = "mine") {
                ReviewCard(
                    review = own,
                    onEdit = { onAction(ReviewsAction.OnOpenComposer) },
                    onDelete = { onAction(ReviewsAction.OnDeleteReview) },
                )
            }
        }
        if (state.isEmpty) {
            item(key = "empty") {
                ReviewsEmpty(if (state.ratingFilter != null) Res.string.reviews_empty_filtered else Res.string.reviews_empty)
            }
        }
        items(state.reviews, key = { it.id }) { review ->
            ReviewCard(
                review = review,
                onVote = { vote: ReviewVote -> onAction(ReviewsAction.OnVote(review.id, vote)) }.takeUnless { review.isOwn },
                onReport = { onAction(ReviewsAction.OnOpenReport(review.id)) }.takeUnless { review.isOwn },
                onReply = { onAction(ReviewsAction.OnOpenReply(review.id)) }.takeIf { state.canReply },
                onDeleteReply = { onAction(ReviewsAction.OnDeleteReply(review.id)) }.takeIf { state.canReply && review.reply != null },
            )
        }
        if (state.isLoadingMore) {
            item(key = "more") { Centered(Modifier.fillMaxWidth().padding(12.dp)) { KomiCircularProgress() } }
        }
        if (state.loadMoreFailed) {
            item(key = "more-failed") {
                Centered(Modifier.fillMaxWidth()) {
                    KomiButton(
                        onClick = { onAction(ReviewsAction.OnLoadMore) },
                        label = stringResource(Res.string.reviews_retry),
                        variant = KomiButtonVariant.Tonal,
                    )
                }
            }
        }
    }
}

// Sort as one connected group, scrolling sideways where the labels run long. The star filter is picked on
// the summary's bars; while one is on, a chip names it and clears it.
@Composable
private fun Filters(
    state: ReviewsUiState,
    onAction: (ReviewsAction) -> Unit,
) {
    val sorts = ReviewSort.entries.map { KomiSegmentedItem(value = it, title = stringResource(it.label())) }.toImmutableList()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            KomiSegmented(
                selected = state.sort,
                items = sorts,
                onSelect = { onAction(ReviewsAction.OnSortChange(it)) },
                size = KomiIconButtonSize.Sm,
            )
        }
        state.ratingFilter?.let { stars ->
            KomiChip(
                label = stringResource(Res.string.reviews_filter_stars, stars),
                kind = KomiChipKind.Input,
                size = KomiChipSize.Sm,
                selected = true,
                onRemove = { onAction(ReviewsAction.OnRatingFilterChange(null)) },
            )
        }
    }
}

@Composable
private fun DeleteReviewDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    KomiDialog(
        onDismissRequest = onDismiss,
        title = { KomiText(text = stringResource(Res.string.reviews_delete_title), role = KomiTextRole.CardTitle) },
        text = { KomiText(text = stringResource(Res.string.reviews_delete_body), role = KomiTextRole.Body) },
        confirmButton = {
            KomiButton(
                onClick = onConfirm,
                label = stringResource(Res.string.reviews_action_delete),
                variant = KomiButtonVariant.Destructive,
            )
        },
        dismissButton = {
            KomiButton(onClick = onDismiss, label = stringResource(Res.string.reviews_cancel), variant = KomiButtonVariant.Text)
        },
    )
}

@Composable
private fun Centered(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun Message(resource: StringResource) {
    KomiText(
        text = stringResource(resource),
        role = KomiTextRole.Body,
        color = LocalPersonality.current.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    )
}

@Composable
private fun ErrorBlock(
    message: String,
    onRetry: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        KomiText(
            text = message,
            role = KomiTextRole.Body,
            color = LocalPersonality.current.colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        KomiButton(onClick = onRetry, label = stringResource(Res.string.reviews_retry), variant = KomiButtonVariant.Tonal)
    }
}

private fun ReviewSort.label(): StringResource =
    when (this) {
        ReviewSort.TOP -> Res.string.reviews_sort_top
        ReviewSort.NEWEST -> Res.string.reviews_sort_newest
        ReviewSort.HIGHEST -> Res.string.reviews_sort_highest
        ReviewSort.LOWEST -> Res.string.reviews_sort_lowest
    }

private fun CannotReviewReason.message(): StringResource =
    when (this) {
        CannotReviewReason.ACCOUNT_TOO_NEW -> Res.string.reviews_cannot_account_too_new
        CannotReviewReason.OWN_REPO -> Res.string.reviews_cannot_own_repo
        CannotReviewReason.BANNED -> Res.string.reviews_cannot_banned
    }

private const val MAX_REVIEW_LENGTH = 2000
private const val LOAD_MORE_THRESHOLD = 3
