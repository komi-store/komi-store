package zed.rainxch.core.presentation.personality.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

private const val MIN_BODY_CONTRAST = 4.5f

fun contrastRatio(
    a: Color,
    b: Color,
): Float {
    val hi = max(a.luminance(), b.luminance())
    val lo = min(a.luminance(), b.luminance())
    return (hi + 0.05f) / (lo + 0.05f)
}

fun inkOn(
    background: Color,
    ink: Color,
    page: Color,
): Color = if (contrastRatio(background, ink) >= contrastRatio(background, page)) ink else page

fun ensureContrast(
    preferred: Color,
    background: Color,
    ink: Color,
    page: Color,
    minRatio: Float = MIN_BODY_CONTRAST,
): Color =
    if (contrastRatio(preferred, background) >= minRatio) {
        preferred
    } else {
        inkOn(background, ink, page)
    }
