package zed.rainxch.details.presentation.components.sections

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.details.presentation.DetailsState
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.action_switch_to_stable
import zed.rainxch.githubstore.core.presentation.res.channel_chip_coachmark_body
import zed.rainxch.githubstore.core.presentation.res.channel_chip_coachmark_dismiss
import zed.rainxch.githubstore.core.presentation.res.channel_chip_coachmark_title
import zed.rainxch.githubstore.core.presentation.res.channel_chip_include_betas
import zed.rainxch.githubstore.core.presentation.res.channel_chip_stable_only
import zed.rainxch.githubstore.core.presentation.res.merged_whats_changed_title
import zed.rainxch.githubstore.core.presentation.res.stalled_project_warning_days
import zed.rainxch.githubstore.core.presentation.res.stalled_project_warning_description
import zed.rainxch.githubstore.core.presentation.res.stalled_project_warning_months

fun LazyListScope.releaseChannel(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
) {
    val installedApp = state.installedApp
    val showMerged = !state.mergedChangelog.isNullOrBlank() && state.mergedChangelogBaseTag != null
    val showStalled = state.stalledStableSinceDays != null
    val showSwitchToStable = state.canSwitchToStable
    val showChannelChip = installedApp != null

    if (!showMerged && !showStalled && !showSwitchToStable && !showChannelChip) return

    item(key = "release-channel-controls") {
        val colors = LocalPersonality.current.colors
        val cardShape = RoundedCornerShape(LocalPersonality.current.shape.corner)
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (showChannelChip || showSwitchToStable) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (installedApp != null) {
                        val includeBetas = installedApp.includePreReleases
                        val channelLabel =
                            if (includeBetas) {
                                stringResource(Res.string.channel_chip_include_betas)
                            } else {
                                stringResource(Res.string.channel_chip_stable_only)
                            }
                        val pulse by rememberChipPulse(active = state.isChannelChipCoachmarkPending)
                        Box(
                            modifier =
                                Modifier.graphicsLayer {
                                    scaleX = pulse
                                    scaleY = pulse
                                },
                        ) {
                            ChannelChip(
                                label = channelLabel,
                                icon = Icons.Default.Science,
                                tint =
                                    if (includeBetas) {
                                        colors.primary
                                    } else {
                                        colors.onSurfaceVariant
                                    },
                                onClick = { onAction(DetailsAction.ToggleIncludeBetas) },
                                contentDescriptionText = channelLabel,
                            )
                            if (state.isChannelChipCoachmarkPending) {
                                ChannelChipCoachmark(
                                    onDismiss = {
                                        onAction(DetailsAction.OnAcknowledgeChannelChipCoachmark)
                                    },
                                )
                            }
                        }
                    }

                    if (showSwitchToStable) {
                        if (state.latestStableRelease != null) {
                            ChannelChip(
                                label =
                                    stringResource(
                                        Res.string.action_switch_to_stable,
                                        state.latestStableRelease.tagName,
                                    ),
                                icon = Icons.Default.Restore,
                                tint = colors.primary,
                                onClick = { onAction(DetailsAction.SwitchToStable) },
                                contentDescriptionText = null,
                            )
                        }
                    }
                }
            }

            if (state.stalledStableSinceDays != null) {
                val title =
                    if (state.stalledStableSinceDays >= 30) {
                        stringResource(
                            Res.string.stalled_project_warning_months,
                            state.stalledStableSinceDays / 30,
                        )
                    } else {
                        stringResource(
                            Res.string.stalled_project_warning_days,
                            state.stalledStableSinceDays,
                        )
                    }
                Row(
                    verticalAlignment = Alignment.Top,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(cardShape)
                            .background(colors.error.copy(alpha = 0.25f))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    KomiIcon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = colors.onError,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(12.dp))
                    Column {
                        KomiText(
                            text = title,
                            role = KomiTextRole.Title,
                            color = colors.onError,
                            uppercase = false,
                        )
                        Spacer(Modifier.height(4.dp))
                        KomiText(
                            text = stringResource(Res.string.stalled_project_warning_description),
                            role = KomiTextRole.Body,
                            fontSize = 13.sp,
                            color = colors.onError,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelChip(
    label: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    contentDescriptionText: String?,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .clip(RoundedCornerShape(shape.corner))
                .background(colors.surfaceContainerHigh)
                .clickable(onClick = onClick)
                .then(
                    if (contentDescriptionText != null) {
                        Modifier.semantics { contentDescription = contentDescriptionText }
                    } else {
                        Modifier
                    },
                ).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        KomiIcon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(6.dp))
        KomiText(
            text = label,
            role = KomiTextRole.Label,
            fontSize = 12.sp,
            color = tint,
            uppercase = false,
        )
    }
}

@Composable
private fun rememberChipPulse(active: Boolean) =
    rememberInfiniteTransition(label = "channel-chip-pulse")
        .animateFloat(
            initialValue = 1f,
            targetValue = if (active) 1.06f else 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 1100),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "channel-chip-pulse-scale",
        )

@Composable
private fun ChannelChipCoachmark(onDismiss: () -> Unit) {
    val colors = LocalPersonality.current.colors
    Popup(
        alignment = Alignment.TopStart,
        offset =
            androidx.compose.ui.unit
                .IntOffset(x = 0, y = -220),
        properties =
            PopupProperties(
                focusable = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = false,
            ),
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier =
                Modifier
                    .width(280.dp)
                    .clip(RoundedCornerShape(LocalPersonality.current.shape.corner))
                    .background(colors.primary)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KomiIcon(
                    imageVector = Icons.Default.Science,
                    contentDescription = null,
                    tint = colors.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
                KomiText(
                    text = stringResource(Res.string.channel_chip_coachmark_title),
                    role = KomiTextRole.Title,
                    color = colors.onPrimary,
                    fontWeight = FontWeight.Bold,
                    uppercase = false,
                )
            }
            KomiText(
                text = stringResource(Res.string.channel_chip_coachmark_body),
                role = KomiTextRole.Body,
                fontSize = 13.sp,
                color = colors.onPrimary.copy(alpha = 0.9f),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                KomiButton(
                    onClick = onDismiss,
                    label = stringResource(Res.string.channel_chip_coachmark_dismiss),
                    variant = KomiButtonVariant.Text,
                    size = KomiButtonSize.Sm,
                )
            }
        }
    }
}
