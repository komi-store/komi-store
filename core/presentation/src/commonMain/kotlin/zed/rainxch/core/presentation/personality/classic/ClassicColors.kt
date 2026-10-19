package zed.rainxch.core.presentation.personality.classic

import androidx.compose.ui.graphics.Color
import zed.rainxch.core.domain.model.appearance.AccentId
import zed.rainxch.core.presentation.personality.model.PersonalityColors

private data class ClassicPrimary(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
)

private fun classicPrimary(
    accent: AccentId,
    dark: Boolean,
): ClassicPrimary =
    when (accent) {
        AccentId.COBALT -> {
            if (dark) {
                ClassicPrimary(Color(0xFFB6C4FF), Color(0xFF00257A), Color(0xFF2C44A8), Color(0xFFDCE1FF))
            } else {
                ClassicPrimary(Color(0xFF3B5BDB), Color(0xFFFFFFFF), Color(0xFFDCE1FF), Color(0xFF00164E))
            }
        }

        AccentId.CRIMSON -> {
            if (dark) {
                ClassicPrimary(Color(0xFFFFB4AB), Color(0xFF690005), Color(0xFF93000A), Color(0xFFFFDAD6))
            } else {
                ClassicPrimary(Color(0xFFB3261E), Color(0xFFFFFFFF), Color(0xFFF9DEDC), Color(0xFF410E0B))
            }
        }

        AccentId.SUN -> {
            if (dark) {
                ClassicPrimary(Color(0xFFF4BE48), Color(0xFF3F2E00), Color(0xFF5B4300), Color(0xFFFFDEA8))
            } else {
                ClassicPrimary(Color(0xFF7A5900), Color(0xFFFFFFFF), Color(0xFFFFDEA8), Color(0xFF261900))
            }
        }

        AccentId.FROST -> {
            if (dark) {
                ClassicPrimary(Color(0xFF4DD9E3), Color(0xFF00363A), Color(0xFF004F54), Color(0xFF6FF6FE))
            } else {
                ClassicPrimary(Color(0xFF00696E), Color(0xFFFFFFFF), Color(0xFF6FF6FE), Color(0xFF002022))
            }
        }

        AccentId.MONO -> {
            if (dark) {
                ClassicPrimary(Color(0xFFC7C7C7), Color(0xFF2E2E2E), Color(0xFF474747), Color(0xFFE4E4E4))
            } else {
                ClassicPrimary(Color(0xFF4A4A4A), Color(0xFFFFFFFF), Color(0xFFE2E2E2), Color(0xFF1A1A1A))
            }
        }
    }

fun classicAccentSwatch(
    accent: AccentId,
    dark: Boolean,
): Pair<Color, Color> = classicPrimary(accent, dark).let { it.primary to it.onPrimary }

private data class ClassicNeutral(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val surfaceVariant: Color,
    val onSurfaceVariant: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color,
    val onError: Color,
)

private val LightNeutral =
    ClassicNeutral(
        background = Color(0xFFFBFBFD),
        onBackground = Color(0xFF1A1C1E),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF1A1C1E),
        surfaceVariant = Color(0xFFEEF0F4),
        onSurfaceVariant = Color(0xFF44474E),
        surfaceContainer = Color(0xFFF3F4F8),
        surfaceContainerHigh = Color(0xFFECEEF3),
        outline = Color(0xFFC4C6CF),
        outlineVariant = Color(0xFFE2E2E6),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
    )

private val DarkNeutral =
    ClassicNeutral(
        background = Color(0xFF111316),
        onBackground = Color(0xFFE3E2E6),
        surface = Color(0xFF1A1C1E),
        onSurface = Color(0xFFE3E2E6),
        surfaceVariant = Color(0xFF2A2D33),
        onSurfaceVariant = Color(0xFFC4C6CF),
        surfaceContainer = Color(0xFF1E2023),
        surfaceContainerHigh = Color(0xFF282A2E),
        outline = Color(0xFF8E9099),
        outlineVariant = Color(0xFF44474E),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
    )

private val AmoledNeutral =
    DarkNeutral.copy(
        background = Color(0xFF000000),
        surface = Color(0xFF000000),
        surfaceVariant = Color(0xFF141414),
        surfaceContainer = Color(0xFF0C0C0C),
        surfaceContainerHigh = Color(0xFF161616),
        outlineVariant = Color(0xFF2A2A2A),
    )

fun classicColors(
    dark: Boolean,
    amoled: Boolean = false,
    accent: AccentId = AccentId.COBALT,
): PersonalityColors {
    val primary = classicPrimary(accent, dark)
    val neutral =
        when {
            !dark -> LightNeutral
            amoled -> AmoledNeutral
            else -> DarkNeutral
        }
    return PersonalityColors(
        primary = primary.primary,
        onPrimary = primary.onPrimary,
        primaryContainer = primary.primaryContainer,
        onPrimaryContainer = primary.onPrimaryContainer,
        background = neutral.background,
        onBackground = neutral.onBackground,
        surface = neutral.surface,
        onSurface = neutral.onSurface,
        surfaceVariant = neutral.surfaceVariant,
        onSurfaceVariant = neutral.onSurfaceVariant,
        surfaceContainer = neutral.surfaceContainer,
        surfaceContainerHigh = neutral.surfaceContainerHigh,
        outline = neutral.outline,
        outlineVariant = neutral.outlineVariant,
        error = neutral.error,
        onError = neutral.onError,
        shadow = Color(0xFF000000),
        scrim = Color(0xFF000000),
        screentoneOpacity = 0f,
        gridOpacity = 0f,
    )
}
