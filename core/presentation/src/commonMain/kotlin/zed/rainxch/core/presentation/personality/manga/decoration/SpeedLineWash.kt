package zed.rainxch.core.presentation.personality.manga.decoration

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

fun Modifier.speedLineWash(
    color: Color,
    alpha: Float = 0.16f,
    spokes: Int = 56,
): Modifier =
    drawBehind {
        val center = Offset(size.width - 46.dp.toPx(), 4.dp.toPx())
        val reach = 130.dp.toPx()
        for (i in 0 until spokes) {
            val angle = (2.0 * PI * i / spokes).toFloat()
            val end = Offset(center.x + cos(angle) * reach, center.y + sin(angle) * reach)
            drawLine(
                brush =
                    Brush.linearGradient(
                        colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                        start = center,
                        end = end,
                    ),
                start = center,
                end = end,
                strokeWidth = 1.1f,
            )
        }
    }
