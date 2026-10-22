package zed.rainxch.core.presentation.components.buttons

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiTooltip
import zed.rainxch.core.presentation.locals.LocalPersonality

@Composable
fun KomiSoftIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 40.dp,
) {
    val colors = LocalPersonality.current.colors
    val shape = RoundedCornerShape(LocalPersonality.current.shape.cornerSmall)
    val alpha = if (enabled) 1f else 0.38f
    val tint = if (accent) colors.primary else colors.onSurfaceVariant
    val surface =
        if (accent) {
            Modifier.background(if (enabled) colors.primaryTint else colors.disabledContainer, shape)
        } else {
            Modifier.border(1.dp, colors.outlineVariant.copy(alpha = alpha), shape)
        }

    KomiTooltip(text = contentDescription, modifier = modifier) {
        Box(
            modifier =
                Modifier
                    .minimumInteractiveComponentSize()
                    .size(size)
                    .clip(shape)
                    .then(surface)
                    .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            KomiIcon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint.copy(alpha = alpha),
                modifier = Modifier.size(size * 0.5f),
            )
        }
    }
}
