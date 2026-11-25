package zed.rainxch.core.presentation.personality.manga

import androidx.compose.ui.graphics.Color

private val CrimsonSwatch = Color(0xFFD8202A) to Color(0xFFFFFFFF)
private val CobaltSwatch = Color(0xFF1F4ED8) to Color(0xFFFFFFFF)
private val SunSwatch = Color(0xFFF5A300) to Color(0xFF1B150D)
private val FrostSwatch = Color(0xFF88C0D0) to Color(0xFF2E3440)

fun mangaAccentSwatch(accent: MangaAccent): Pair<Color, Color>? =
    when (accent) {
        MangaAccent.MONO -> null
        MangaAccent.CRIMSON -> CrimsonSwatch
        MangaAccent.COBALT -> CobaltSwatch
        MangaAccent.SUN -> SunSwatch
        MangaAccent.FROST -> FrostSwatch
    }
