package zed.rainxch.core.presentation.personality.utils

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.materialkolor.ktx.toHct
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.classic.classicSeedScheme

// Rebuilds Classic's colors from [seed] for [content], such as an app's icon color on its own
// page. Manga keeps its paper and ink, and the Mono accent, whose scheme has no chroma, stays gray.
// The colors switch at once: a fade would recompose the whole page on every frame.
@Composable
fun KomiSeededTheme(
    seed: Color?,
    content: @Composable () -> Unit,
) {
    val personality = LocalPersonality.current
    if (personality !is ClassicPersonality || personality.colorScheme.isGray()) {
        content()
        return
    }
    val base = personality.colorScheme
    val scheme =
        remember(seed, base) {
            seed?.let {
                classicSeedScheme(
                    seed = it,
                    dark = base.background.luminance() < HALF_LUMINANCE,
                    amoled = base.background == Color.Black,
                )
            } ?: base
        }
    CompositionLocalProvider(LocalPersonality provides personality.copy(colorScheme = scheme)) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

private fun ColorScheme.isGray(): Boolean = primary.toHct().chroma < MONO_CHROMA

private const val HALF_LUMINANCE = 0.5f
private const val MONO_CHROMA = 4.0
