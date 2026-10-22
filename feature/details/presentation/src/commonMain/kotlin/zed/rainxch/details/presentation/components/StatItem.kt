package zed.rainxch.details.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiTooltip
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.formatCount

@Composable
fun StatItem(
    label: String,
    stat: Int,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    StatItem(label = label, stat = stat.toLong(), modifier = modifier, onClick = onClick)
}

// A shortened count ("27k") shows the exact number on long-press or hover.
@Composable
fun StatItem(
    label: String,
    stat: Long,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val shown = formatCount(stat)
    val exact = stat.toString()
    if (shown == exact) {
        StatBox(label = label, value = shown, onClick = onClick, modifier = modifier)
    } else {
        // TooltipBox puts its modifier on an inner anchor, where a Row weight is lost, so the Box carries it.
        Box(modifier = modifier) {
            KomiTooltip(text = exact, modifier = Modifier.fillMaxSize()) {
                StatBox(label = label, value = shown, onClick = onClick, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val type = LocalPersonality.current.type
    StatFrame(label = label, onClick = onClick, modifier = modifier) {
        KomiText(
            text = value,
            role = KomiTextRole.Numeral,
            color = colors.onSurface,
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = type.caption.fontSize, maxFontSize = type.numeral.fontSize),
        )
    }
}

@Composable
fun TextStatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    StatFrame(label = label, onClick = onClick, modifier = modifier) {
        KomiText(
            text = value,
            role = KomiTextRole.CardTitle,
            color = LocalPersonality.current.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// A tile that opens a page is one tap target, with a chevron after its label.
@Composable
private fun StatFrame(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    value: @Composable () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val rowShape = RoundedCornerShape(LocalPersonality.current.shape.corner)
    Column(
        modifier =
            modifier
                .clip(rowShape)
                .border(width = 1.dp, color = colors.outline, shape = rowShape)
                .background(colors.surface)
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KomiText(
                text = label,
                role = KomiTextRole.LabelSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (onClick != null) {
                KomiIcon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        value()
    }
}
