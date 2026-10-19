package zed.rainxch.tweaks.presentation.components.shell

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.components.lists.KomiListRow

fun Modifier.settingsRowDivider(
    color: Color,
    show: Boolean,
): Modifier =
    if (!show) {
        this
    } else {
        drawBehind {
            val stroke = 2.dp.toPx()
            drawLine(
                color = color,
                start = Offset(0f, size.height - stroke / 2f),
                end = Offset(size.width, size.height - stroke / 2f),
                strokeWidth = stroke,
            )
        }
    }

@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    strong: Boolean = false,
    last: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    KomiListRow(
        title = title,
        modifier = modifier,
        subtitle = subtitle,
        strong = strong,
        showDivider = !last,
        onClick = onClick,
        trailing = trailing,
    )
}
