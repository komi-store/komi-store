package zed.rainxch.core.presentation.components.inputs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.manga.decoration.inkFocusRing

@Composable
fun KomiSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    when (val personality = LocalPersonality.current) {
        is MangaPersonality -> {
            MangaSwitch(personality, checked, onCheckedChange, modifier, enabled)
        }

        is ClassicPersonality -> {
            val colors = personality.colors
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = modifier,
                enabled = enabled,
                colors =
                    SwitchDefaults.colors(
                        checkedThumbColor = colors.onPrimary,
                        checkedTrackColor = colors.primary,
                        uncheckedThumbColor = colors.outline,
                        uncheckedTrackColor = colors.surfaceVariant,
                        uncheckedBorderColor = colors.outline,
                    ),
            )
        }
    }
}

@Composable
private fun MangaSwitch(
    personality: MangaPersonality,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean,
) {
    val colors = personality.colors
    val alpha = if (enabled) 1f else 0.45f
    val trackWidth = 52.dp
    val trackHeight = 30.dp
    val thumb = 22.dp
    val shape = RoundedCornerShape(personality.shape.cornerSmall)
    val track by animateColorAsState(
        if (checked) colors.primary else colors.surfaceVariant,
        label = "komiSwitchTrack",
    )
    val thumbOffset by animateDpAsState(
        if (checked) trackWidth - thumb - 4.dp else 4.dp,
        label = "komiSwitchThumb",
    )
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val toggleModifier =
        if (onCheckedChange != null) {
            Modifier.toggleable(
                value = checked,
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
        } else {
            Modifier
        }
    Box(
        modifier =
            modifier
                .then(toggleModifier)
                .size(width = trackWidth, height = trackHeight)
                .inkFocusRing(focused = { focused || hovered }, color = colors.primary)
                .background(color = track.copy(alpha = track.alpha * alpha), shape = shape)
                .border(width = 2.5.dp, color = colors.outline.copy(alpha = alpha), shape = shape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .offset(x = thumbOffset)
                    .size(thumb)
                    .background(
                        color = (if (checked) colors.onPrimary else colors.surface).copy(alpha = alpha),
                        shape = RoundedCornerShape(personality.shape.cornerSmall),
                    ).border(
                        width = 2.dp,
                        color = colors.outline.copy(alpha = alpha),
                        shape = RoundedCornerShape(personality.shape.cornerSmall),
                    ),
        )
    }
}
