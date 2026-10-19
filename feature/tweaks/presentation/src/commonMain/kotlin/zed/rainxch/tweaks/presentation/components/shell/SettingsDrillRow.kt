package zed.rainxch.tweaks.presentation.components.shell

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.locals.LocalPersonality

@Composable
fun SettingsDrillRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    last: Boolean = false,
) {
    val colors = LocalPersonality.current.colors
    SettingsRow(
        title = title,
        subtitle = subtitle,
        last = last,
        onClick = onClick,
        modifier = modifier,
        trailing = {
            KomiIcon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
    )
}
