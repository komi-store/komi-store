package zed.rainxch.profile.presentation.announcements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.announcement.Announcement
import zed.rainxch.core.domain.model.announcement.AnnouncementCategory
import zed.rainxch.core.domain.model.announcement.AnnouncementIconHint
import zed.rainxch.core.domain.model.announcement.AnnouncementSeverity
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfaceElevation
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.announcements_acknowledge
import zed.rainxch.githubstore.core.presentation.res.announcements_acknowledged
import zed.rainxch.githubstore.core.presentation.res.announcements_read_more
import zed.rainxch.githubstore.core.presentation.res.dismiss

private const val BODY_COLLAPSED_LINES = 4

@Composable
fun AnnouncementCard(
    announcement: Announcement,
    isAcknowledged: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onCtaClick: () -> Unit,
    onDismissClick: () -> Unit,
    onAcknowledgeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val severityColor = severityAccent(announcement.severity)
    val containerColor =
        when (announcement.severity) {
            AnnouncementSeverity.CRITICAL -> colors.error
            AnnouncementSeverity.IMPORTANT -> colors.surfaceContainerHigh
            AnnouncementSeverity.INFO -> colors.surface
        }

    KomiSurface(
        modifier = modifier.fillMaxWidth(),
        elevation = KomiSurfaceElevation.Card,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(shape.corner))
                    .background(containerColor),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(4.dp)
                            .fillMaxHeight()
                            .background(severityColor),
                )
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HeaderRow(
                        severity = announcement.severity,
                        category = announcement.category,
                        iconHint = announcement.iconHint,
                        severityColor = severityColor,
                        isAcknowledged = isAcknowledged,
                    )

                    KomiText(
                        text = announcement.title,
                        role = KomiTextRole.CardTitle,
                        color = colors.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )

                    ExpandableBody(
                        body = announcement.body,
                        isExpanded = isExpanded,
                        onExpand = onToggleExpand,
                    )

                    ActionRow(
                        announcement = announcement,
                        isAcknowledged = isAcknowledged,
                        onCtaClick = onCtaClick,
                        onDismissClick = onDismissClick,
                        onAcknowledgeClick = onAcknowledgeClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(
    severity: AnnouncementSeverity,
    category: AnnouncementCategory,
    iconHint: AnnouncementIconHint?,
    severityColor: Color,
    isAcknowledged: Boolean,
) {
    val colors = LocalPersonality.current.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KomiIcon(
            imageVector = severityIcon(severity, iconHint),
            contentDescription = stringResource(severityLabel(severity)),
            tint = severityColor,
            modifier = Modifier.size(20.dp),
        )
        CategoryChip(category = category)
        Spacer(Modifier.weight(1f))
        if (isAcknowledged) {
            KomiIcon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            KomiText(
                text = stringResource(Res.string.announcements_acknowledged),
                role = KomiTextRole.Overline,
                color = colors.primary,
            )
        }
    }
}

@Composable
private fun CategoryChip(category: AnnouncementCategory) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(shape.cornerSmall))
                .background(colors.surfaceContainerHigh)
                .padding(horizontal = 10.dp, vertical = 2.dp),
    ) {
        KomiText(
            text = stringResource(categoryLabel(category)),
            role = KomiTextRole.Caption,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExpandableBody(
    body: String,
    isExpanded: Boolean,
    onExpand: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    var isOverflowing by remember(body) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KomiText(
            text = body,
            role = KomiTextRole.Body,
            color = colors.onSurface,
            maxLines = if (isExpanded) Int.MAX_VALUE else BODY_COLLAPSED_LINES,
            onTextLayout = { layout ->
                if (!isExpanded) {
                    isOverflowing = layout.hasVisualOverflow
                }
            },
        )
        if (!isExpanded && isOverflowing) {
            KomiButton(
                onClick = onExpand,
                label = stringResource(Res.string.announcements_read_more),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
            )
        }
    }
}

@Composable
private fun ActionRow(
    announcement: Announcement,
    isAcknowledged: Boolean,
    onCtaClick: () -> Unit,
    onDismissClick: () -> Unit,
    onAcknowledgeClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!announcement.ctaUrl.isNullOrBlank()) {
            val resolvedLabel =
                announcement.ctaLabel?.takeIf { it.isNotBlank() }
                    ?: stringResource(Res.string.announcements_read_more)
            KomiButton(
                onClick = onCtaClick,
                label = resolvedLabel,
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
                leadingIcon = Icons.AutoMirrored.Filled.OpenInNew,
            )
        }
        Spacer(Modifier.weight(1f))
        if (announcement.requiresAcknowledgment && !isAcknowledged) {
            KomiButton(
                onClick = onAcknowledgeClick,
                label = stringResource(Res.string.announcements_acknowledge),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
            )
        } else if (announcement.dismissible) {
            KomiButton(
                onClick = onDismissClick,
                label = stringResource(Res.string.dismiss),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
            )
        }
    }
}

@Composable
private fun severityAccent(severity: AnnouncementSeverity): Color {
    val colors = LocalPersonality.current.colors
    return when (severity) {
        AnnouncementSeverity.CRITICAL -> colors.error
        AnnouncementSeverity.IMPORTANT -> colors.primary
        AnnouncementSeverity.INFO -> colors.primary
    }
}

private fun severityIcon(
    severity: AnnouncementSeverity,
    hint: AnnouncementIconHint?,
): ImageVector {
    if (hint != null) {
        return when (hint) {
            AnnouncementIconHint.INFO -> Icons.Filled.Info
            AnnouncementIconHint.WARNING -> Icons.Filled.Warning
            AnnouncementIconHint.SECURITY -> Icons.Filled.Security
            AnnouncementIconHint.CELEBRATION -> Icons.Filled.Campaign
            AnnouncementIconHint.CHANGE -> Icons.Filled.Campaign
        }
    }
    return when (severity) {
        AnnouncementSeverity.CRITICAL -> Icons.Filled.Security
        AnnouncementSeverity.IMPORTANT -> Icons.Filled.Warning
        AnnouncementSeverity.INFO -> Icons.Filled.Info
    }
}
