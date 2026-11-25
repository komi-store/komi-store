package zed.rainxch.details.presentation.components.sections

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiCoachmark
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.details.presentation.DetailsState
import zed.rainxch.details.presentation.model.InstalledAppUi
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.action_switch_to_stable
import zed.rainxch.githubstore.core.presentation.res.channel_chip_coachmark_body
import zed.rainxch.githubstore.core.presentation.res.channel_chip_coachmark_dismiss
import zed.rainxch.githubstore.core.presentation.res.channel_chip_coachmark_title
import zed.rainxch.githubstore.core.presentation.res.channel_chip_include_betas
import zed.rainxch.githubstore.core.presentation.res.channel_chip_stable_only
import zed.rainxch.githubstore.core.presentation.res.stalled_project_warning_days
import zed.rainxch.githubstore.core.presentation.res.stalled_project_warning_description
import zed.rainxch.githubstore.core.presentation.res.stalled_project_warning_months

fun LazyListScope.releaseChannel(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
) {
    val showMerged = !state.mergedChangelog.isNullOrBlank() && state.mergedChangelogBaseTag != null
    val showControls = state.installedApp != null || state.canSwitchToStable
    if (showMerged || showControls || state.stalledStableSinceDays != null) {
        item(key = "release-channel-controls") {
            ReleaseChannelControls(state = state, onAction = onAction, showControls = showControls)
        }
    }
}

@Composable
private fun ReleaseChannelControls(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
    showControls: Boolean,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (showControls) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                state.installedApp?.let { app ->
                    BetaChannelChip(
                        installedApp = app,
                        coachmarkPending = state.isChannelChipCoachmarkPending,
                        onAction = onAction,
                    )
                }
                val latestStable = state.latestStableRelease
                if (state.canSwitchToStable && latestStable != null) {
                    ChannelChip(
                        label = stringResource(Res.string.action_switch_to_stable, latestStable.tagName),
                        icon = Icons.Default.Restore,
                        tint = LocalPersonality.current.colors.primary,
                        onClick = { onAction(DetailsAction.SwitchToStable) },
                        contentDescriptionText = null,
                    )
                }
            }
        }
        state.stalledStableSinceDays?.let { days -> StalledProjectWarning(days = days) }
    }
}

@Composable
private fun BetaChannelChip(
    installedApp: InstalledAppUi,
    coachmarkPending: Boolean,
    onAction: (DetailsAction) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val includeBetas = installedApp.includePreReleases
    val channelLabel = stringResource(if (includeBetas) Res.string.channel_chip_include_betas else Res.string.channel_chip_stable_only)
    val pulse by rememberChipPulse(active = coachmarkPending)
    KomiCoachmark(
        visible = coachmarkPending,
        title = stringResource(Res.string.channel_chip_coachmark_title),
        text = stringResource(Res.string.channel_chip_coachmark_body),
        dismissLabel = stringResource(Res.string.channel_chip_coachmark_dismiss),
        onDismiss = { onAction(DetailsAction.OnAcknowledgeChannelChipCoachmark) },
        modifier =
            Modifier.graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            },
    ) {
        ChannelChip(
            label = channelLabel,
            icon = Icons.Default.Science,
            tint = if (includeBetas) colors.primary else colors.onSurfaceVariant,
            onClick = { onAction(DetailsAction.ToggleIncludeBetas) },
            contentDescriptionText = channelLabel,
        )
    }
}

@Composable
private fun StalledProjectWarning(days: Int) {
    val colors = LocalPersonality.current.colors
    val title =
        if (days >= DAYS_PER_MONTH) {
            stringResource(Res.string.stalled_project_warning_months, days / DAYS_PER_MONTH)
        } else {
            stringResource(Res.string.stalled_project_warning_days, days)
        }
    Row(
        verticalAlignment = Alignment.Top,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LocalPersonality.current.shape.corner))
                .background(colors.errorTint)
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
            KomiText(text = title, role = KomiTextRole.CardTitle, color = colors.onError)
            Spacer(Modifier.height(4.dp))
            KomiText(
                text = stringResource(Res.string.stalled_project_warning_description),
                role = KomiTextRole.Body,
                color = colors.onError,
            )
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
            role = KomiTextRole.Caption,
            color = tint,
        )
    }
}

@Composable
private fun rememberChipPulse(active: Boolean): State<Float> {
    val motion = LocalPersonality.current.motion
    return rememberInfiniteTransition(label = "channel-chip-pulse")
        .animateFloat(
            initialValue = 1f,
            targetValue = if (active && motion.animates) 1.06f else 1f,
            animationSpec = infiniteRepeatable(animation = motion.ambient(), repeatMode = RepeatMode.Reverse),
            label = "channel-chip-pulse-scale",
        )
}

private const val DAYS_PER_MONTH = 30
