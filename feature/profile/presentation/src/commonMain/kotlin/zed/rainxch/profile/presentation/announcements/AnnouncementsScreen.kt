package zed.rainxch.profile.presentation.announcements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.bars.KomiTopBarSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.refresh.KomiPullToRefresh
import zed.rainxch.core.presentation.components.scaffold.KomiScaffold
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.announcements_empty
import zed.rainxch.githubstore.core.presentation.res.announcements_open_mute_settings
import zed.rainxch.githubstore.core.presentation.res.announcements_refresh_failed
import zed.rainxch.githubstore.core.presentation.res.announcements_title

@Composable
fun AnnouncementsScreen(
    state: AnnouncementsState,
    onAction: (AnnouncementsAction) -> Unit,
    onNavigateBack: () -> Unit,
) {
    val colors = LocalPersonality.current.colors

    LaunchedEffect(Unit) {
        onAction(AnnouncementsAction.OnEnterScreen)
    }

    KomiScaffold(
        topBar = {
            KomiTopBar(
                title = stringResource(Res.string.announcements_title),
                size = KomiTopBarSize.Compact,
                onBack = onNavigateBack,
                actions = {
                    KomiIconButton(
                        icon = Icons.Rounded.Tune,
                        contentDescription = stringResource(Res.string.announcements_open_mute_settings),
                        onClick = { onAction(AnnouncementsAction.OnOpenMuteSheet) },
                        variant = KomiButtonVariant.Tonal,
                    )
                },
            )
        },
    ) { innerPadding ->
        KomiPullToRefresh(
            isRefreshing = state.isRefreshing,
            onRefresh = { onAction(AnnouncementsAction.OnRefresh) },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
        ) {
            if (state.items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    KomiText(
                        text =
                            stringResource(
                                if (state.refreshFailed) {
                                    Res.string.announcements_refresh_failed
                                } else {
                                    Res.string.announcements_empty
                                },
                            ),
                        role = KomiTextRole.Body,
                        color = colors.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.refreshFailed) {
                        item {
                            KomiText(
                                text = stringResource(Res.string.announcements_refresh_failed),
                                role = KomiTextRole.Caption,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }

                    items(state.items, key = { it.id }) { announcement ->
                        AnnouncementCard(
                            announcement = announcement,
                            isAcknowledged = announcement.id in state.acknowledgedIds,
                            isExpanded = announcement.id in state.expandedIds,
                            onToggleExpand = {
                                onAction(AnnouncementsAction.OnToggleExpand(announcement.id))
                            },
                            onCtaClick = { onAction(AnnouncementsAction.OnCtaClick(announcement)) },
                            onDismissClick = { onAction(AnnouncementsAction.OnDismissClick(announcement)) },
                            onAcknowledgeClick = {
                                onAction(AnnouncementsAction.OnAcknowledgeClick(announcement))
                            },
                        )
                    }
                }
            }
        }
    }

    if (state.isMuteSheetVisible) {
        MuteSettingsBottomSheet(
            mutedCategories = state.mutedCategories,
            onToggle = { category, muted ->
                onAction(AnnouncementsAction.OnToggleMute(category, muted))
            },
            onDismiss = { onAction(AnnouncementsAction.OnDismissMuteSheet) },
        )
    }
}
