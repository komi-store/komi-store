package zed.rainxch.core.presentation.personality.manga.decoration

import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// The face lands on its collapsing shadow on press and lifts on hover, read in layout and draw so frames never recompose.
fun Modifier.inkPress(
    pressProgress: () -> Float,
    hoverProgress: () -> Float,
    shadow: DpOffset,
    shadowColor: Color,
    shape: Shape,
    pressInset: Dp = 1.dp,
    hoverLift: Dp = 2.dp,
    hoverGrow: Dp = 3.dp,
): Modifier =
    this
        .offset {
            val pp = pressProgress()
            val hp = hoverProgress()
            val pressX = maxOf(shadow.x.toPx() - pressInset.toPx(), 1.dp.toPx())
            val pressY = maxOf(shadow.y.toPx() - pressInset.toPx(), 1.dp.toPx())
            val lift = -hoverLift.toPx() * hp * (1f - pp)
            IntOffset((pressX * pp + lift).roundToInt(), (pressY * pp + lift).roundToInt())
        }.hardShadow(color = shadowColor, shape = shape) {
            val pp = pressProgress()
            val hp = hoverProgress()
            val grow = hoverGrow.toPx() * hp
            Offset(
                x = (shadow.x.toPx() + grow) * (1f - pp),
                y = (shadow.y.toPx() + grow) * (1f - pp),
            )
        }
