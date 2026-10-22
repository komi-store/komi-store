package zed.rainxch.core.presentation.personality.manga

import androidx.compose.ui.graphics.Color
import zed.rainxch.core.presentation.personality.model.PersonalityColors

private data class PaperInk(
    val page: Color,
    val panel: Color,
    val well: Color,
    val ink: Color,
    val muted: Color,
    val shadow: Color,
    val error: Color,
    val onError: Color,
    val screentoneOpacity: Float,
    val gridOpacity: Float,
)

private fun paperInk(paper: MangaPaper): PaperInk =
    when (paper) {
        MangaPaper.DAY -> {
            PaperInk(
                page = Color(0xFFF1EADC),
                panel = Color(0xFFFAF5EA),
                well = Color(0xFFE7DEC9),
                ink = Color(0xFF1B150D),
                muted = Color(0xFF695F50),
                shadow = Color(0xFF1B150D),
                error = Color(0xFFB3261E),
                onError = Color(0xFFFFFFFF),
                screentoneOpacity = 0.16f,
                gridOpacity = 0.05f,
            )
        }

        MangaPaper.NIGHT -> {
            PaperInk(
                page = Color(0xFF0C0A07),
                panel = Color(0xFF16120C),
                well = Color(0xFF211B12),
                ink = Color(0xFFF0E9DA),
                muted = Color(0xFF968B77),
                shadow = Color(0xFF000000),
                error = Color(0xFFFF6B5E),
                onError = Color(0xFF1B150D),
                screentoneOpacity = 0.20f,
                gridOpacity = 0.06f,
            )
        }

        MangaPaper.NORD -> {
            PaperInk(
                page = Color(0xFF2E3440),
                panel = Color(0xFF3B4252),
                well = Color(0xFF434C5E),
                ink = Color(0xFFECEFF4),
                muted = Color(0xFF9AA5BD),
                shadow = Color(0xFF20242E),
                error = Color(0xFFE5818A),
                onError = Color(0xFF20242E),
                screentoneOpacity = 0.16f,
                gridOpacity = 0.05f,
            )
        }
    }

fun mangaColors(
    paper: MangaPaper,
    accent: MangaAccent,
): PersonalityColors {
    val ink = paperInk(paper)
    val (primary, onPrimary) = mangaAccentSwatch(accent) ?: (ink.ink to ink.page)
    return PersonalityColors(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primary,
        onPrimaryContainer = onPrimary,
        background = ink.page,
        onBackground = ink.ink,
        surface = ink.panel,
        onSurface = ink.ink,
        surfaceVariant = ink.well,
        onSurfaceVariant = ink.muted,
        surfaceContainer = ink.panel,
        surfaceContainerHigh = ink.well,
        outline = ink.ink,
        outlineVariant = ink.muted,
        divider = ink.ink.copy(alpha = 0.4f),
        error = ink.error,
        onError = ink.onError,
        shadow = ink.shadow,
        scrim = ink.shadow,
        screentoneOpacity = ink.screentoneOpacity,
        gridOpacity = ink.gridOpacity,
    )
}
