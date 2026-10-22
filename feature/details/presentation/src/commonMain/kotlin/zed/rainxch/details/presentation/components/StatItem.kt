package zed.rainxch.details.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
) {
    StatItem(label = label, stat = stat.toLong(), modifier = modifier)
}

// A shortened count ("27k") shows the exact number on long-press or hover.
@Composable
fun StatItem(
    label: String,
    stat: Long,
    modifier: Modifier = Modifier,
) {
    val shown = formatCount(stat)
    val exact = stat.toString()
    if (shown == exact) {
        StatBox(label = label, value = shown, modifier = modifier)
    } else {
        KomiTooltip(text = exact, modifier = modifier) {
            StatBox(label = label, value = shown)
        }
    }
}

@Composable
private fun StatBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val type = LocalPersonality.current.type
    val rowShape = RoundedCornerShape(LocalPersonality.current.shape.corner)
    Column(
        modifier =
            modifier
                .clip(rowShape)
                .border(width = 1.dp, color = colors.outline, shape = rowShape)
                .background(colors.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        KomiText(
            text = label,
            role = KomiTextRole.LabelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
) {
    val colors = LocalPersonality.current.colors
    val rowShape = RoundedCornerShape(LocalPersonality.current.shape.corner)
    Column(
        modifier =
            modifier
                .clip(rowShape)
                .border(width = 1.dp, color = colors.outline, shape = rowShape)
                .background(colors.surface)
                .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        KomiText(
            text = label,
            role = KomiTextRole.LabelSmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        KomiText(
            text = value,
            role = KomiTextRole.CardTitle,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
