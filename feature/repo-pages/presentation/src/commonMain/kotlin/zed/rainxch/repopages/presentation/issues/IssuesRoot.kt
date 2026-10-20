package zed.rainxch.repopages.presentation.issues

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import zed.rainxch.core.presentation.components.overlays.KomiToastState
import zed.rainxch.core.presentation.components.overlays.rememberKomiToastState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiFab
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.locals.LocalStatusColors
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.githubstore.core.presentation.res.*
import zed.rainxch.repopages.domain.model.IssueState
import zed.rainxch.repopages.domain.model.RepoIssue
import zed.rainxch.repopages.presentation.components.LabelChip
import zed.rainxch.repopages.presentation.components.RepoPagesEmpty
import zed.rainxch.repopages.presentation.components.RepoPagesError
import zed.rainxch.repopages.presentation.components.RepoPagesLoading
import zed.rainxch.repopages.presentation.components.RepoPagesTopBar

@Composable
fun IssuesRoot(
    onNavigateBack: () -> Unit,
    onOpenIssue: (issueNumber: Int) -> Unit,
    viewModel: IssuesViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toastState = rememberKomiToastState()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is IssuesEvent.OnMessage -> {
                toastState.show(event.message)
            }
        }
    }

    if (state.showNewIssueSheet) {
        NewIssueSheet(
            state = state,
            onDismiss = {
                viewModel.onAction(IssuesAction.OnDismissNewIssue)
            },
            onTitleChange = {
                viewModel.onAction(IssuesAction.OnNewIssueTitleChange(it))
            },
            onBodyChange = {
                viewModel.onAction(IssuesAction.OnNewIssueBodyChange(it))
            },
            onSubmit = {
                viewModel.onAction(IssuesAction.OnSubmitNewIssue)
            },
        )
    }

    IssuesScreen(
        state = state,
        toastState = toastState,
        onAction = { action ->
            when (action) {
                IssuesAction.OnBackClick -> onNavigateBack()
                is IssuesAction.OnOpenIssue -> onOpenIssue(action.issueNumber)
                else -> viewModel.onAction(action)
            }
        }
    )
}

@Composable
private fun IssuesScreen(
    state: IssuesUiState,
    toastState: KomiToastState,
    onAction: (IssuesAction) -> Unit
) {
    KomiScaffold(
        topBar = {
            RepoPagesTopBar(
                title = stringResource(Res.string.repo_pages_issues_title),
                onBack = {
                    onAction(IssuesAction.OnBackClick)
                },
            )
        },
        toastState = toastState,
        floatingActionButton = {
            if (!state.isLoading && state.errorMessage == null) {
                KomiFab(
                    onClick = {
                        onAction(IssuesAction.OnOpenNewIssue)
                    },
                    icon = Icons.Default.Add,
                    contentDescription = stringResource(Res.string.repo_pages_new_issue),
                    label = stringResource(Res.string.repo_pages_new_issue),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KomiChip(
                    label = stringResource(Res.string.repo_pages_issues_filter_open),
                    kind = KomiChipKind.Filter,
                    selected = state.filter == IssueState.OPEN,
                    onClick = {
                        onAction(IssuesAction.OnFilterChange(IssueState.OPEN))
                    },
                )

                KomiChip(
                    label = stringResource(Res.string.repo_pages_issues_filter_closed),
                    kind = KomiChipKind.Filter,
                    selected = state.filter == IssueState.CLOSED,
                    onClick = {
                        onAction(IssuesAction.OnFilterChange(IssueState.CLOSED))
                    },
                )
            }

            when {
                state.isLoading -> RepoPagesLoading()

                state.errorMessage != null -> RepoPagesError(
                    message = state.errorMessage,
                    onRetry = {
                        onAction(IssuesAction.OnRetry)
                    },
                )

                state.issues.isEmpty() -> {
                    val emptyText = if (state.filter == IssueState.OPEN) {
                        stringResource(Res.string.repo_pages_issues_empty_open)
                    } else {
                        stringResource(Res.string.repo_pages_issues_empty_closed)
                    }
                    RepoPagesEmpty(message = emptyText)
                }

                else -> IssuesList(
                    state = state,
                    onLoadMore = {
                        onAction(IssuesAction.OnLoadMore)
                    },
                    onOpenIssue = {
                        onAction(IssuesAction.OnOpenIssue(it))
                    },
                )
            }
        }
    }
}

@Composable
private fun NewIssueSheet(
    state: IssuesUiState,
    onDismiss: () -> Unit,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    KomiSheet(
        onDismiss = onDismiss,
        title = stringResource(Res.string.repo_pages_new_issue),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            KomiTextField(
                value = state.newIssueTitle,
                onValueChange = onTitleChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(Res.string.repo_pages_new_issue_title_hint),
                enabled = !state.isCreatingIssue,
            )

            KomiTextField(
                value = state.newIssueBody,
                onValueChange = onBodyChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = stringResource(Res.string.repo_pages_new_issue_body_hint),
                multiline = true,
                rows = 4,
                enabled = !state.isCreatingIssue,
            )

            KomiButton(
                onClick = onSubmit,
                label = stringResource(Res.string.repo_pages_new_issue_submit),
                enabled = !state.isCreatingIssue && state.newIssueTitle.isNotBlank(),
                loading = state.isCreatingIssue,
                modifier = Modifier.align(Alignment.End),
            )
        }
    }
}

@Composable
private fun IssuesList(
    state: IssuesUiState,
    onLoadMore: () -> Unit,
    onOpenIssue: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(
            items = state.issues,
            key = { it.issueId }
        ) { issue ->
            IssueRow(
                issue = issue,
                onClick = {
                    onOpenIssue(issue.issueId)
                }
            )
        }

        if (state.isLoadingMore) {
            item(key = "loading_more") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    KomiCircularProgress(modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun IssueRow(
    issue: RepoIssue,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    KomiSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val statusColors = LocalStatusColors.current
            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(10.dp)
                    .background(
                        color = if (issue.state == IssueState.OPEN) {
                            statusColors.issueOpen
                        } else {
                            statusColors.issueClosed
                        },
                        shape = RoundedCornerShape(shape.cornerSmall),
                    ),
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                KomiText(
                    text = issue.title,
                    role = KomiTextRole.ItemTitle,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                KomiText(
                    text = "#${issue.issueId} · " +
                            stringResource(
                                Res.string.repo_pages_issue_opened_by,
                                issue.authorLogin
                            ),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                if (issue.labels.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        issue.labels.take(6).forEach { label ->
                            LabelChip(label)
                        }
                    }
                }
            }
        }
    }
}
