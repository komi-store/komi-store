package zed.rainxch.tweaks.presentation.components.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow

@Composable
fun SettingsValuePill(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val personality = LocalPersonality.current) {
        is MangaPersonality -> {
            val colors = personality.colors
            Row(
                modifier =
                    modifier
                        .height(32.dp)
                        .hardShadow(DpOffset(2.dp, 2.dp), colors.shadow)
                        .background(colors.surface)
                        .border(2.5.dp, colors.outline)
                        .clickable(onClick = onClick)
                        .padding(horizontal = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KomiText(
                    text = value,
                    role = KomiTextRole.Label,
                    color = colors.onSurface,
                )
                KomiIcon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.onSurface,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        is ClassicPersonality -> {
            val colors = personality.colors
            Row(
                modifier =
                    modifier
                        .height(32.dp)
                        .clip(RoundedCornerShape(personality.shape.cornerSmall))
                        .background(colors.surfaceContainerHigh)
                        .clickable(onClick = onClick)
                        .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KomiText(
                    text = value,
                    role = KomiTextRole.Label,
                    color = colors.onSurface,
                )
                KomiIcon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
