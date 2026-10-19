package zed.rainxch.core.presentation.personality.manga.decoration

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

// A repeating dot tile baked into a tiny ImageBitmap once, exposed as a tiling shader brush.
// Lets the dot lattices fill any area with a SINGLE drawRect instead of a per-frame loop of
// hundreds of drawCircle calls (the old approach janked scrolling on weaker GPUs).
private fun dotTileBrush(
    color: Color,
    opacity: Float,
    spacing: Float,
    radius: Float,
): ShaderBrush {
    val side = spacing.roundToInt().coerceAtLeast(1)
    val tile = ImageBitmap(side, side)
    val canvas = Canvas(tile)
    val paint =
        Paint().apply {
            isAntiAlias = true
            this.color = color.copy(alpha = opacity)
        }
    canvas.drawCircle(Offset(side / 2f, side / 2f), radius, paint)
    return ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
}

fun Modifier.hardShadow(
    offset: DpOffset,
    color: Color,
    shape: Shape = RectangleShape,
): Modifier =
    drawBehind {
        val dx = offset.x.toPx()
        val dy = offset.y.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        translate(dx, dy) {
            drawOutline(outline = outline, color = color)
        }
    }

fun Modifier.hardShadow(
    color: Color,
    shape: Shape = RectangleShape,
    offset: DrawScope.() -> Offset,
): Modifier =
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawBehind {
            val o = offset()
            translate(o.x, o.y) {
                drawOutline(outline = outline, color = color)
            }
        }
    }

fun Modifier.screentone(
    color: Color,
    opacity: Float,
    dotRadius: Float = 1.1f,
    spacing: Float = 5f,
): Modifier =
    drawWithCache {
        val brush = dotTileBrush(color, opacity, spacing, dotRadius)
        onDrawWithContent {
            drawContent()
            drawRect(brush = brush)
        }
    }

fun Modifier.speedLines(
    color: Color,
    spokes: Int = 120,
    opacity: Float = 0.12f,
    strokeWidth: Float = 1.2f,
): Modifier =
    drawWithCache {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val reach = hypot(size.width, size.height)
        onDrawBehind {
            for (i in 0 until spokes) {
                val angle = (2.0 * kotlin.math.PI * i / spokes).toFloat()
                drawLine(
                    color = color,
                    start = Offset(cx, cy),
                    end = Offset(cx + cos(angle) * reach, cy + sin(angle) * reach),
                    strokeWidth = strokeWidth,
                    alpha = opacity,
                )
            }
        }
    }

// Top-right corner wash: a dot lattice radial-masked from the top-right corner (opaque at
// the corner, fading to nothing by 70% of the region diagonal). Draws behind content.
fun Modifier.screentoneCorner(
    color: Color,
    opacity: Float,
    boost: Float = 1f,
    regionWidth: Dp = 120.dp,
    regionHeight: Dp = 90.dp,
    dotRadius: Float = 1.1f,
    spacing: Float = 5f,
): Modifier =
    drawWithCache {
        val wPx = regionWidth.toPx()
        val hPx = regionHeight.toPx()
        val maskRadius = hypot(wPx, hPx) * 0.7f
        val bw = wPx.coerceAtMost(size.width).roundToInt().coerceAtLeast(1)
        val bh = hPx.coerceAtMost(size.height).roundToInt().coerceAtLeast(1)
        val region = ImageBitmap(bw, bh)
        val canvas = Canvas(region)
        val paint = Paint().apply { isAntiAlias = true }
        var y = 0f
        while (y <= bh) {
            var x = 0f
            while (x <= bw) {
                val falloff = (1f - hypot(bw - x, y) / maskRadius).coerceIn(0f, 1f)
                if (falloff > 0f) {
                    paint.color = color.copy(alpha = opacity * boost * falloff)
                    canvas.drawCircle(Offset(x, y), dotRadius, paint)
                }
                x += spacing
            }
            y += spacing
        }
        onDrawBehind {
            val left = (size.width - bw).coerceAtLeast(0f)
            drawImage(image = region, topLeft = Offset(left, 0f))
        }
    }

// Halftone dot lattice drawn OVER content with multiply blend, so the dots sit IN the image
// (inked-avatar Layer B). spacing/radius in dp; opacity already resolved by caller.
fun Modifier.halftoneOverlay(
    color: Color,
    opacity: Float,
    spacing: Dp = 4.dp,
    dotRadius: Dp = 1.1.dp,
): Modifier =
    drawWithCache {
        val brush = dotTileBrush(color, opacity, spacing.toPx(), dotRadius.toPx())
        onDrawWithContent {
            drawContent()
            drawRect(brush = brush, blendMode = BlendMode.Multiply)
        }
    }

// Even dot field across the whole surface (stat-slab look). Draws behind content.
fun Modifier.screentoneFill(
    color: Color,
    opacity: Float,
    dotRadius: Float = 1f,
    spacing: Float = 6f,
): Modifier =
    drawWithCache {
        val brush = dotTileBrush(color, opacity, spacing, dotRadius)
        onDrawBehind {
            drawRect(brush = brush)
        }
    }
