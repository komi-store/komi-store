package zed.rainxch.apps.presentation.starred

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.apps.presentation.starred.components.StarredCandidateRow
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.bars.KomiTopBarSize
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.inputs.KomiSwitch
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.progress.KomiLoadingIndicator
import zed.rainxch.core.presentation.components.progress.KomiLinearProgress
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.ObserveAsEvents
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.starred_picker_empty
import zed.rainxch.githubstore.core.presentation.res.starred_picker_filter_show_all
import zed.rainxch.githubstore.core.presentation.res.starred_picker_header_counts
import zed.rainxch.githubstore.core.presentation.res.starred_picker_no_match
import zed.rainxch.githubstore.core.presentation.res.starred_picker_progress
import zed.rainxch.githubstore.core.presentation.res.starred_picker_rate_limited
import zed.rainxch.githubstore.core.presentation.res.starred_picker_resume
import zed.rainxch.githubstore.core.presentation.res.starred_picker_search_hint
import zed.rainxch.githubstore.core.presentation.res.starred_picker_sign_in_required
import zed.rainxch.githubstore.core.presentation.res.starred_picker_sort_alphabetical
import zed.rainxch.githubstore.core.presentation.res.starred_picker_sort_recent
import zed.rainxch.githubstore.core.presentation.res.starred_picker_sort_stars
import zed.rainxch.githubstore.core.presentation.res.starred_picker_star_dedup_tooltip
import zed.rainxch.githubstore.core.presentation.res.starred_picker_title

@Composable
fun StarredPickerRoot(
    viewModel: StarredPickerViewModel = koinViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDetails: (repoId: Long, owner: String, repo: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            StarredPickerEvent.NavigateBack -> onNavigateBack()
            is StarredPickerEvent.NavigateToDetails -> onNavigateToDetails(event.repoId, event.owner, event.repo)
        }
    }

    StarredPickerScreen(state = state, onAction = viewModel::onAction)
}

@Composable
private fun StarredPickerScreen(
    state: StarredPickerState,
    onAction: (StarredPickerAction) -> Unit,
) {
    val colors = LocalPersonality.current.colors

    KomiScaffold(
        topBar = {
            KomiTopBar(
                title = stringResource(Res.string.starred_picker_title),
                size = KomiTopBarSize.Compact,
                onBack = { onAction(StarredPickerAction.OnNavigateBack) },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (state.phase) {
                StarredPickerState.Phase.LoadingStars -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        KomiLoadingIndicator()
                    }
                }

                StarredPickerState.Phase.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        KomiText(
                            text = if (!state.isAuthenticated) {
                                stringResource(Res.string.starred_picker_sign_in_required)
                            } else {
                                stringResource(Res.string.starred_picker_empty)
                            },
                            role = KomiTextRole.Body,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }

                StarredPickerState.Phase.ScanningReleases,
                StarredPickerState.Phase.Ready,
                -> {
                    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                        Spacer(Modifier.height(8.dp))

                        KomiText(
                            text = stringResource(
                                Res.string.starred_picker_header_counts,
                                state.totalStarred,
                                state.apkCount,
                                state.trackedCount,
                            ),
                            role = KomiTextRole.Body,
                            color = colors.onSurfaceVariant,
                        )

                        if (state.phase == StarredPickerState.Phase.ScanningReleases) {
                            Spacer(Modifier.height(8.dp))

                            Column(modifier = Modifier.fillMaxWidth()) {
                                KomiLinearProgress(
                                    progress = {
                                        if (state.scanTotal > 0) state.scanProgress.toFloat() / state.scanTotal else 0f
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                Spacer(Modifier.height(4.dp))

                                KomiText(
                                    text = stringResource(
                                        Res.string.starred_picker_progress,
                                        state.scanProgress,
                                        state.scanTotal,
                                    ),
                                    role = KomiTextRole.Caption,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }

                        if (state.rateLimited) {
                            Spacer(Modifier.height(8.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                KomiText(
                                    text = stringResource(Res.string.starred_picker_rate_limited),
                                    role = KomiTextRole.Body,
                                    color = colors.error,
                                    modifier = Modifier.weight(1f),
                                )

                                KomiButton(
                                    onClick = { onAction(StarredPickerAction.OnResume) },
                                    label = stringResource(Res.string.starred_picker_resume),
                                    variant = KomiButtonVariant.Primary,
                                    size = KomiButtonSize.Sm,
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        KomiTextField(
                            value = state.searchQuery,
                            onValueChange = { onAction(StarredPickerAction.OnSearchChange(it)) },
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = stringResource(Res.string.starred_picker_search_hint),
                            leadingIcon = Icons.Rounded.Search,
                            clearable = true,
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            KomiChip(
                                label = stringResource(Res.string.starred_picker_sort_recent),
                                kind = KomiChipKind.Filter,
                                selected = state.sortRule == StarredPickerSortRule.RecentlyStarred,
                                onClick = { onAction(StarredPickerAction.OnSortRuleSelected(StarredPickerSortRule.RecentlyStarred)) },
                            )

                            KomiChip(
                                label = stringResource(Res.string.starred_picker_sort_alphabetical),
                                kind = KomiChipKind.Filter,
                                selected = state.sortRule == StarredPickerSortRule.Alphabetical,
                                onClick = { onAction(StarredPickerAction.OnSortRuleSelected(StarredPickerSortRule.Alphabetical)) },
                            )

                            KomiChip(
                                label = stringResource(Res.string.starred_picker_sort_stars),
                                kind = KomiChipKind.Filter,
                                selected = state.sortRule == StarredPickerSortRule.MostStars,
                                onClick = { onAction(StarredPickerAction.OnSortRuleSelected(StarredPickerSortRule.MostStars)) },
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            KomiText(
                                text = stringResource(Res.string.starred_picker_filter_show_all),
                                role = KomiTextRole.Body,
                                color = colors.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )

                            KomiSwitch(
                                checked = state.showWithoutApk,
                                onCheckedChange = { onAction(StarredPickerAction.OnToggleWithoutApk(it)) },
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        KomiText(
                            text = stringResource(Res.string.starred_picker_star_dedup_tooltip),
                            role = KomiTextRole.Caption,
                            color = colors.onSurfaceVariant,
                        )

                        if (state.visibleCandidates.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                KomiText(
                                    text = stringResource(Res.string.starred_picker_no_match),
                                    role = KomiTextRole.Body,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        } else {
                            Spacer(Modifier.height(8.dp))

                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(items = state.visibleCandidates, key = { it.repoId }) { candidate ->
                                    StarredCandidateRow(
                                        candidate = candidate,
                                        onClick = { onAction(StarredPickerAction.OnCandidateClick(candidate)) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
