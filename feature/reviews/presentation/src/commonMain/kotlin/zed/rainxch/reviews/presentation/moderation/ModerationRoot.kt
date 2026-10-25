package zed.rainxch.reviews.presentation.moderation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.reviews.ModerationDecision
import zed.rainxch.core.domain.model.reviews.ModerationStatus
import zed.rainxch.core.domain.model.reviews.ReportReason
import zed.rainxch.core.domain.model.reviews.ReviewTarget
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.bars.KomiTopBarSize
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.chips.KomiChipSize
import zed.rainxch.core.presentation.components.overlays.KomiDialog
import zed.rainxch.core.presentation.components.overlays.KomiToastState
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.reviews.ReviewCard
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.reviews_action_delete
import zed.rainxch.githubstore.core.presentation.res.reviews_cancel
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_ban
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_ban_body
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_ban_title
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_delete_body
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_delete_title
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_dismiss
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_empty_hidden
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_empty_reported
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_hidden
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_hidden_reason
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_hide
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_report_count
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_reported
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_title
import zed.rainxch.githubstore.core.presentation.res.reviews_moderation_unhide
import zed.rainxch.githubstore.core.presentation.res.reviews_report_off_topic
import zed.rainxch.githubstore.core.presentation.res.reviews_report_offensive
import zed.rainxch.githubstore.core.presentation.res.reviews_report_other
import zed.rainxch.githubstore.core.presentation.res.reviews_report_spam
import zed.rainxch.githubstore.core.presentation.res.reviews_retry

@Composable
fun ModerationRoot(
    onNavigateBack: () -> Unit,
    onOpenRepo: (ReviewTarget) -> Unit,
    viewModel: ModerationViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberKomiToastState()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is ModerationEvent.OnMessage -> toastState.show(event.message)
        }
    }

    state.confirm?.let { confirm ->
        ConfirmDialog(
            confirm = confirm,
            onConfirm = { viewModel.onAction(ModerationAction.OnConfirm) },
            onDismiss = { viewModel.onAction(ModerationAction.OnDismissConfirm) },
        )
    }

    ModerationScreen(
        state = state,
        toastState = toastState,
        onAction = { action ->
            when (action) {
                ModerationAction.OnBackClick -> onNavigateBack()
                is ModerationAction.OnOpenRepo -> onOpenRepo(action.repo)
                else -> viewModel.onAction(action)
            }
        },
    )
}

@Composable
private fun ModerationScreen(
    state: ModerationUiState,
    toastState: KomiToastState,
    onAction: (ModerationAction) -> Unit,
) {
    KomiScaffold(
        topBar = {
            KomiTopBar(
                title = stringResource(Res.string.reviews_moderation_title),
                size = KomiTopBarSize.Compact,
                onBack = { onAction(ModerationAction.OnBackClick) },
            )
        },
        toastState = toastState,
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            StatusFilter(
                status = state.status,
                onStatusChange = { onAction(ModerationAction.OnStatusChange(it)) },
            )
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    (state.isLoading || state.isLoadingMore) && state.items.isEmpty() -> {
                        Centered(Modifier.fillMaxSize()) { KomiCircularProgress() }
                    }

                    state.errorMessage != null -> {
                        Centered(Modifier.fillMaxSize()) {
                            ErrorBlock(state.errorMessage, onRetry = { onAction(ModerationAction.OnRetry) })
                        }
                    }

                    state.items.isEmpty() -> {
                        Centered(Modifier.fillMaxSize()) {
                            Message(
                                if (state.status == ModerationStatus.REPORTED) {
                                    Res.string.reviews_moderation_empty_reported
                                } else {
                                    Res.string.reviews_moderation_empty_hidden
                                },
                            )
                        }
                    }

                    else -> {
                        Queue(state = state, onAction = onAction)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusFilter(
    status: ModerationStatus,
    onStatusChange: (ModerationStatus) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ModerationStatus.entries.forEachIndexed { index, entry ->
            KomiChip(
                label =
                    stringResource(
                        if (entry == ModerationStatus.REPORTED) {
                            Res.string.reviews_moderation_reported
                        } else {
                            Res.string.reviews_moderation_hidden
                        },
                    ),
                kind = KomiChipKind.Filter,
                size = KomiChipSize.Sm,
                selected = status == entry,
                index = index,
                onClick = { onStatusChange(entry) },
            )
        }
    }
}

@Composable
private fun Queue(
    state: ModerationUiState,
    onAction: (ModerationAction) -> Unit,
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
    LaunchedEffect(nearEnd, state.items.size, state.isLoadingMore, state.loadMoreFailed) {
        if (nearEnd && !state.loadMoreFailed) currentOnAction(ModerationAction.OnLoadMore)
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        items(state.items, key = { it.review.id }) { item ->
            QueueItem(
                item = item,
                status = state.status,
                busy = item.review.id in state.busyIds,
                onAction = onAction,
            )
        }
        if (state.isLoadingMore) {
            item(key = "more") { Centered(Modifier.fillMaxWidth()) { KomiCircularProgress() } }
        }
        if (state.loadMoreFailed) {
            item(key = "more-failed") {
                Centered(Modifier.fillMaxWidth()) {
                    KomiButton(
                        onClick = { onAction(ModerationAction.OnLoadMore) },
                        label = stringResource(Res.string.reviews_retry),
                        variant = KomiButtonVariant.Tonal,
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueItem(
    item: ModerationItemUi,
    status: ModerationStatus,
    busy: Boolean,
    onAction: (ModerationAction) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KomiText(
            text = item.repoLabel,
            role = KomiTextRole.Label,
            color = colors.primary,
            modifier =
                Modifier.clickable(role = Role.Button) { onAction(ModerationAction.OnOpenRepo(item.repo)) },
        )
        ReviewCard(review = item.review)
        if (item.reportCount > 0) {
            KomiText(
                text = pluralStringResource(Res.plurals.reviews_moderation_report_count, item.reportCount, item.reportCount),
                role = KomiTextRole.LabelSmall,
                color = colors.onSurfaceVariant,
            )
            item.reports.forEach { report ->
                val reason = report.reason?.let { stringResource(it.label()) }
                KomiText(
                    text = listOfNotNull(reason, report.note).joinToString(": "),
                    role = KomiTextRole.Caption,
                    color = colors.onSurface,
                )
            }
        }
        item.hiddenReason?.let { reason ->
            KomiText(
                text = stringResource(Res.string.reviews_moderation_hidden_reason, reason),
                role = KomiTextRole.Caption,
                color = colors.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val id = item.review.id
            if (status == ModerationStatus.REPORTED) {
                Action(Res.string.reviews_moderation_hide, KomiButtonVariant.Tonal, busy) {
                    onAction(ModerationAction.OnDecision(id, ModerationDecision.HIDE))
                }
                Action(Res.string.reviews_moderation_dismiss, KomiButtonVariant.Outline, busy) {
                    onAction(ModerationAction.OnDecision(id, ModerationDecision.DISMISS))
                }
            } else {
                Action(Res.string.reviews_moderation_unhide, KomiButtonVariant.Tonal, busy) {
                    onAction(ModerationAction.OnDecision(id, ModerationDecision.UNHIDE))
                }
            }
            Action(Res.string.reviews_action_delete, KomiButtonVariant.Text, busy) {
                onAction(ModerationAction.OnDecision(id, ModerationDecision.DELETE))
            }
            Action(Res.string.reviews_moderation_ban, KomiButtonVariant.Text, busy) {
                onAction(ModerationAction.OnBanClick(id))
            }
        }
    }
}

@Composable
private fun Action(
    label: StringResource,
    variant: KomiButtonVariant,
    busy: Boolean,
    onClick: () -> Unit,
) {
    KomiButton(
        onClick = onClick,
        label = stringResource(label),
        variant = variant,
        size = KomiButtonSize.Sm,
        enabled = !busy,
    )
}

@Composable
private fun ConfirmDialog(
    confirm: ModerationConfirm,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val title =
        when (confirm) {
            is ModerationConfirm.Delete -> stringResource(Res.string.reviews_moderation_delete_title)
            is ModerationConfirm.Ban -> stringResource(Res.string.reviews_moderation_ban_title, confirm.authorLogin)
        }
    val body =
        when (confirm) {
            is ModerationConfirm.Delete -> Res.string.reviews_moderation_delete_body
            is ModerationConfirm.Ban -> Res.string.reviews_moderation_ban_body
        }
    val confirmLabel =
        when (confirm) {
            is ModerationConfirm.Delete -> Res.string.reviews_action_delete
            is ModerationConfirm.Ban -> Res.string.reviews_moderation_ban
        }
    KomiDialog(
        onDismissRequest = onDismiss,
        title = { KomiText(text = title, role = KomiTextRole.CardTitle) },
        text = { KomiText(text = stringResource(body), role = KomiTextRole.Body) },
        confirmButton = {
            KomiButton(onClick = onConfirm, label = stringResource(confirmLabel), variant = KomiButtonVariant.Destructive)
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

private fun ReportReason.label(): StringResource =
    when (this) {
        ReportReason.SPAM -> Res.string.reviews_report_spam
        ReportReason.OFFENSIVE -> Res.string.reviews_report_offensive
        ReportReason.OFF_TOPIC -> Res.string.reviews_report_off_topic
        ReportReason.OTHER -> Res.string.reviews_report_other
    }

private const val LOAD_MORE_THRESHOLD = 3
