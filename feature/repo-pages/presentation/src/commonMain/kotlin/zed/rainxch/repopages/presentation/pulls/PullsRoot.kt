package zed.rainxch.repopages.presentation.pulls

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.locals.LocalStatusColors
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.repo_pages_issue_opened_by
import zed.rainxch.githubstore.core.presentation.res.repo_pages_issues_filter_closed
import zed.rainxch.githubstore.core.presentation.res.repo_pages_issues_filter_open
import zed.rainxch.githubstore.core.presentation.res.repo_pages_pr_draft
import zed.rainxch.githubstore.core.presentation.res.repo_pages_pulls_empty_closed
import zed.rainxch.githubstore.core.presentation.res.repo_pages_pulls_empty_open
import zed.rainxch.githubstore.core.presentation.res.repo_pages_pulls_title
import zed.rainxch.repopages.domain.model.IssueState
import zed.rainxch.repopages.domain.model.PullRequestState
import zed.rainxch.repopages.domain.model.RepoPullRequest
import zed.rainxch.repopages.presentation.components.RepoPagesEmpty
import zed.rainxch.repopages.presentation.components.RepoPagesError
import zed.rainxch.repopages.presentation.components.RepoPagesLoading
import zed.rainxch.repopages.presentation.components.RepoPagesTopBar

@Composable
fun PullsRoot(
    onNavigateBack: () -> Unit,
    onOpenPull: (pullNumber: Int) -> Unit,
    viewModel: PullsViewModel,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    PullsScreen(
        state = state,
        onAction = { action ->
            when (action) {
                PullsAction.OnBackClick -> onNavigateBack()
                is PullsAction.OnOpenPull -> onOpenPull(action.pullNumber)
                else -> viewModel.onAction(action)
            }
        },
    )
}

@Composable
private fun PullsScreen(
    state: PullsUiState,
    onAction: (PullsAction) -> Unit,
) {
    KomiScaffold(
        topBar = {
            RepoPagesTopBar(
                title = stringResource(Res.string.repo_pages_pulls_title),
                onBack = { onAction(PullsAction.OnBackClick) },
            )
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
                    onClick = { onAction(PullsAction.OnFilterChange(IssueState.OPEN)) },
                )

                KomiChip(
                    label = stringResource(Res.string.repo_pages_issues_filter_closed),
                    kind = KomiChipKind.Filter,
                    selected = state.filter == IssueState.CLOSED,
                    onClick = { onAction(PullsAction.OnFilterChange(IssueState.CLOSED)) },
                )
            }

            when {
                state.isLoading -> RepoPagesLoading()

                state.errorMessage != null -> RepoPagesError(
                    message = state.errorMessage,
                    onRetry = { onAction(PullsAction.OnRetry) },
                )

                state.pulls.isEmpty() -> {
                    val emptyText = if (state.filter == IssueState.OPEN) {
                        stringResource(Res.string.repo_pages_pulls_empty_open)
                    } else {
                        stringResource(Res.string.repo_pages_pulls_empty_closed)
                    }
                    RepoPagesEmpty(message = emptyText)
                }

                else -> PullsList(
                    state = state,
                    onLoadMore = { onAction(PullsAction.OnLoadMore) },
                    onOpenPull = { onAction(PullsAction.OnOpenPull(it)) },
                )
            }
        }
    }
}

@Composable
private fun PullsList(
    state: PullsUiState,
    onLoadMore: () -> Unit,
    onOpenPull: (Int) -> Unit,
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
        items(state.pulls, key = { it.number }) { pull ->
            PullRow(pull = pull, onClick = { onOpenPull(pull.number) })
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
private fun PullRow(
    pull: RepoPullRequest,
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
            val pullStateColor = when (pull.state) {
                PullRequestState.OPEN -> statusColors.pullOpen
                PullRequestState.MERGED -> statusColors.pullMerged
                PullRequestState.CLOSED -> statusColors.pullClosed
            }

            Box(
                modifier = Modifier
                    .padding(top = 5.dp)
                    .size(10.dp)
                    .background(color = pullStateColor, shape = RoundedCornerShape(shape.cornerSmall)),
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (pull.isDraft) {
                        KomiText(
                            text = stringResource(Res.string.repo_pages_pr_draft),
                            role = KomiTextRole.Caption,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier
                                .background(colors.surfaceContainerHigh, RoundedCornerShape(shape.cornerSmall))
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        )
                    }

                    KomiText(
                        text = pull.title,
                        role = KomiTextRole.ItemTitle,
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                KomiText(
                    text = "#${pull.number} · " +
                        stringResource(Res.string.repo_pages_issue_opened_by, pull.authorLogin),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
