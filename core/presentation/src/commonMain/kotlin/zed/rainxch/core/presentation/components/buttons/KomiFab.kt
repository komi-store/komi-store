package zed.rainxch.core.presentation.components.buttons

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.components.bars.LocalInBar
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow
import zed.rainxch.core.presentation.personality.manga.decoration.inkFocusRing

@Composable
fun KomiFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    when (val personality = LocalPersonality.current) {
        is MangaPersonality -> {
            MangaFab(personality, onClick, icon, contentDescription, modifier, label)
        }

        is ClassicPersonality -> {
            val colors = personality.colors
            if (label == null && LocalInBar.current) {
                KomiCookieButton(
                    icon = icon,
                    contentDescription = contentDescription,
                    onClick = onClick,
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer,
                    modifier = modifier,
                )
            } else if (label != null) {
                ExtendedFloatingActionButton(
                    onClick = onClick,
                    modifier = modifier,
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer,
                    icon = { KomiIcon(icon, contentDescription, modifier = Modifier.size(20.dp)) },
                    text = { KomiText(label, role = KomiTextRole.LabelLarge) },
                )
            } else {
                FloatingActionButton(
                    onClick = onClick,
                    modifier = modifier,
                    containerColor = colors.primaryContainer,
                    contentColor = colors.onPrimaryContainer,
                ) {
                    KomiIcon(icon, contentDescription, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
private fun MangaFab(
    personality: MangaPersonality,
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    label: String?,
) {
    val colors = personality.colors
    val shape = remember(personality.shape.cornerSmall) { RoundedCornerShape(personality.shape.cornerSmall) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val hovered by interaction.collectIsHoveredAsState()
    val press = animateFloatAsState(if (pressed) 1f else 0f, label = "komiFabPress")
    val shadow = DpOffset((5 - 5 * press.value).dp, (5 - 5 * press.value).dp)

    val base =
        modifier
            .hardShadow(offset = shadow, color = colors.shadow, shape = shape)
            .clip(shape)
            .background(colors.primary, shape)
            .border(3.dp, colors.outline, shape)
            .inkFocusRing(focused = { focused || hovered }, color = colors.primary)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)

    if (label != null) {
        Row(
            modifier = base.height(56.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KomiIcon(icon, contentDescription, modifier = Modifier.size(22.dp), tint = colors.onPrimary)
            KomiText(label, role = KomiTextRole.LabelLarge, color = colors.onPrimary)
        }
    } else {
        Box(modifier = base.size(56.dp), contentAlignment = Alignment.Center) {
            KomiIcon(icon, contentDescription, modifier = Modifier.size(26.dp), tint = colors.onPrimary)
        }
    }
}
