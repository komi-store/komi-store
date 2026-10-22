package zed.rainxch.apps.presentation.import.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.zIndex
import zed.rainxch.apps.presentation.import.util.LocalReducedMotion
import zed.rainxch.core.presentation.locals.LocalPersonality
import kotlin.math.min
import kotlin.random.Random

private data class Particle(
    val xFraction: Float,
    val fallSpeed: Float,
    val rotationSpeed: Float,
    val rotationOffset: Float,
    val radiusPx: Float,
    val color: Color,
)

@Composable
fun ConfettiOverlay(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = LocalReducedMotion.current
    if (!enabled || reducedMotion) return

    val colors = LocalPersonality.current.colors
    val palette =
        listOf(
            colors.primary,
            colors.primary,
            colors.primary,
            colors.primaryContainer,
            colors.error,
        )

    val particles =
        remember(palette) {
            val rng = Random(42)
            List(40) { index ->

                val color =
                    when {
                        index % 7 == 6 -> palette[4]
                        index % 5 == 0 -> palette[3]
                        else -> palette[index % 3]
                    }
                Particle(
                    xFraction = rng.nextFloat(),
                    fallSpeed = 0.7f + rng.nextFloat() * 0.6f,
                    rotationSpeed = (rng.nextFloat() - 0.5f) * 360f,
                    rotationOffset = rng.nextFloat() * 360f,
                    radiusPx = 6f + rng.nextFloat() * 6f,
                    color = color,
                )
            }
        }

    val progress = remember { Animatable(0f) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    val motion = LocalPersonality.current.motion
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = motion.ambient(easing = LinearEasing),
        )
    }

    if (progress.value >= 1f) return

    Canvas(
        modifier =
            modifier
                .fillMaxSize()
                .zIndex(1f)
                .onSizeChanged { size = it },
    ) {
        if (size.width == 0 || size.height == 0) return@Canvas
        val width = size.width.toFloat()
        val height = size.height.toFloat()
        val travel = height + 200f
        val alpha = (1f - progress.value).coerceIn(0f, 1f)

        particles.forEach { p ->
            val x = p.xFraction * width
            val y = -40f + travel * p.fallSpeed * progress.value

            if (y > height + p.radiusPx) return@forEach

            val rotation = p.rotationOffset + p.rotationSpeed * progress.value
            rotate(degrees = rotation, pivot = Offset(x, y)) {
                drawCircle(
                    color = p.color.copy(alpha = min(1f, alpha) * p.color.alpha),
                    radius = p.radiusPx,
                    center = Offset(x, y),
                )
            }
        }
    }
}
