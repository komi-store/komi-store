package zed.rainxch.core.presentation.personality.classic

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme
import com.materialkolor.dynamiccolor.ColorSpec
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.presentation.personality.model.PersonalityColors

// Material's 2025 color spec, TonalSpot: the seed's hue drives primary, secondary and
// tertiary stay near it, and the neutrals carry a trace of it, as wallpaper colors do.
fun classicColorScheme(
    accent: AccentId,
    dark: Boolean,
    amoled: Boolean = false,
    wallpaper: ColorScheme? = null,
): ColorScheme {
    val scheme =
        wallpaper.takeIf { accent == AccentId.WALLPAPER }
            ?: dynamicColorScheme(
                seedColor = accent.seed,
                isDark = dark,
                style = if (accent == AccentId.MONO) PaletteStyle.Monochrome else PaletteStyle.TonalSpot,
                specVersion = ColorSpec.SpecVersion.SPEC_2025,
            )
    return if (dark && amoled) scheme.amoled() else scheme
}

// The same spec and style as the accents, from any seed: an app's icon color on its own page.
fun classicSeedScheme(
    seed: Color,
    dark: Boolean,
    amoled: Boolean = false,
): ColorScheme {
    val scheme =
        dynamicColorScheme(
            seedColor = seed,
            isDark = dark,
            style = PaletteStyle.TonalSpot,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
        )
    return if (dark && amoled) scheme.amoled() else scheme
}

fun classicAccentSwatch(
    accent: AccentId,
    dark: Boolean,
): Pair<Color, Color> = classicColorScheme(accent, dark).let { it.primary to it.onPrimary }

// WALLPAPER falls back to Crimson where the platform has no wallpaper colors.
private val AccentId.seed: Color
    get() =
        when (this) {
            AccentId.COBALT -> Color(0xFF3B5BDB)
            AccentId.CRIMSON, AccentId.WALLPAPER -> Color(0xFFB3261E)
            AccentId.SUN -> Color(0xFFF4BE48)
            AccentId.FROST -> Color(0xFF00696E)
            AccentId.MONO -> Color(0xFF5E5E5E)
        }

// Pure black behind content; the tinted containers keep cards and sheets visible on it.
private fun ColorScheme.amoled(): ColorScheme =
    copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
    )

// Komi's surface is the card fill, which Material calls surfaceContainerLow.
internal fun ColorScheme.toClassicColors(): PersonalityColors =
    PersonalityColors(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        background = background,
        onBackground = onBackground,
        surface = surfaceContainerLow,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        outline = outline,
        outlineVariant = outlineVariant,
        divider = outlineVariant,
        error = error,
        onError = onError,
        shadow = Color.Black,
        scrim = scrim,
        screentoneOpacity = 0f,
        gridOpacity = 0f,
    )
