package zed.rainxch.core.presentation.personality.utils

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.classic.classicSeedScheme

// Rebuilds Classic's colors from [seed] for [content], such as an app's icon color on its own
// page, and fades to them once the seed is known. Manga keeps its paper and ink.
@Composable
fun KomiSeededTheme(
    seed: Color?,
    content: @Composable () -> Unit,
) {
    val personality = LocalPersonality.current
    if (personality !is ClassicPersonality) {
        content()
        return
    }
    val base = personality.colorScheme
    val target =
        remember(seed, base) {
            seed?.let {
                classicSeedScheme(
                    seed = it,
                    dark = base.background.luminance() < HALF_LUMINANCE,
                    amoled = base.background == Color.Black,
                )
            } ?: base
        }
    val scheme = animatedScheme(target, personality.motion.slowEffects())
    CompositionLocalProvider(LocalPersonality provides personality.copy(colorScheme = scheme)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

// The roles Komi's Classic colors and Material's components read on a page.
@Composable
private fun animatedScheme(
    target: ColorScheme,
    spec: AnimationSpec<Color>,
): ColorScheme {
    @Composable
    fun Color.animated(): Color = animateColorAsState(this, spec, label = "seeded-color").value

    return target.copy(
        primary = target.primary.animated(),
        onPrimary = target.onPrimary.animated(),
        primaryContainer = target.primaryContainer.animated(),
        onPrimaryContainer = target.onPrimaryContainer.animated(),
        secondaryContainer = target.secondaryContainer.animated(),
        onSecondaryContainer = target.onSecondaryContainer.animated(),
        background = target.background.animated(),
        onBackground = target.onBackground.animated(),
        onSurface = target.onSurface.animated(),
        surfaceVariant = target.surfaceVariant.animated(),
        onSurfaceVariant = target.onSurfaceVariant.animated(),
        surfaceContainerLow = target.surfaceContainerLow.animated(),
        surfaceContainer = target.surfaceContainer.animated(),
        surfaceContainerHigh = target.surfaceContainerHigh.animated(),
        surfaceContainerHighest = target.surfaceContainerHighest.animated(),
        outline = target.outline.animated(),
        outlineVariant = target.outlineVariant.animated(),
    )
}

private const val HALF_LUMINANCE = 0.5f
