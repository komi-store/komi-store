package zed.rainxch.tweaks.presentation.components.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiTooltip
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow

@Composable
fun AccentSwatch(
    fill: Color,
    onFill: Color,
    mono: Boolean,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    KomiTooltip(text = label, modifier = modifier) {
        SwatchBody(fill = fill, onFill = onFill, mono = mono, selected = selected, label = label, onClick = onClick, icon = icon)
    }
}

@Composable
private fun SwatchBody(
    fill: Color,
    onFill: Color,
    mono: Boolean,
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
    icon: ImageVector?,
) {
    val colors = LocalPersonality.current.colors
    val isManga = LocalPersonality.current is MangaPersonality
    val shape = if (isManga) RectangleShape else CircleShape

    Box(
        modifier =
            Modifier
                .size(40.dp)
                .then(if (selected && isManga) Modifier.hardShadow(DpOffset(3.dp, 3.dp), colors.shadow) else Modifier)
                .clip(shape)
                .background(fill)
                .border(
                    width = if (isManga || selected) 2.5.dp else 1.5.dp,
                    color = if (!isManga && selected) colors.primary else colors.outline,
                    shape = shape,
                ).selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (mono) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clip(shape)
                        .background(
                            Brush.linearGradient(
                                0.5f to colors.onSurface,
                                0.5f to colors.surface,
                                start = Offset.Zero,
                                end = Offset.Infinite,
                            ),
                        ),
            )
        }

        val mark = if (selected) Icons.Filled.Check else icon
        if (mark != null) {
            KomiIcon(
                imageVector = mark,
                contentDescription = null,
                tint = onFill,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
