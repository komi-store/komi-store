package zed.rainxch.core.presentation.components.shapes

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection

// A rounded box with a tail on its top edge that points up at what it answers. The tail sits [tailStart]
// in from the start edge, so it flips in right-to-left layouts. Content needs [tailHeight] of extra top
// padding to clear it.
class SpeechBubbleShape(
    private val corner: Dp,
    private val tailStart: Dp,
    private val tailWidth: Dp,
    private val tailHeight: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline =
        with(density) {
            val top = tailHeight.toPx()
            val half = tailWidth.toPx() / 2
            val fromStart = tailStart.toPx() + half
            val center = if (layoutDirection == LayoutDirection.Ltr) fromStart else size.width - fromStart
            val body =
                Path().apply {
                    addRoundRect(RoundRect(0f, top, size.width, size.height, CornerRadius(corner.toPx())))
                }
            // The base dips a pixel into the body so the union leaves no seam where the two meet.
            val tail =
                Path().apply {
                    moveTo(center - half, top + 1f)
                    lineTo(center, 0f)
                    lineTo(center + half, top + 1f)
                    close()
                }
            Outline.Generic(Path.combine(PathOperation.Union, body, tail))
        }
}
