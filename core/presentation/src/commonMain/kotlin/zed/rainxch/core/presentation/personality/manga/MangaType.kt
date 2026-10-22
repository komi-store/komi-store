package zed.rainxch.core.presentation.personality.manga

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import zed.rainxch.core.presentation.personality.model.PersonalityType

// Real families (Anton / Noto Sans per script / JetBrains Mono) are injected at theme time
// by withMangaFonts() — @Composable, since CMP Res fonts require composition. These
// defaults only show if a Personality is read without PersonalityTheme wrapping it.
private val MangaDisplayFamily = FontFamily.Default
private val MangaBodyFamily = FontFamily.Default
private val MangaMonoFamily = FontFamily.Default

// Anton and JetBrains Mono ship one weight each; without this a bold request smears them.
private val SingleWeight = FontSynthesis.None

internal val MangaType =
    PersonalityType(
        display =
            TextStyle(
                fontFamily = MangaDisplayFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 32.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.01.em,
                fontSynthesis = SingleWeight,
            ),
        // Anton has no heavier weight, so the emphasized display is the display.
        displayEmphasized =
            TextStyle(
                fontFamily = MangaDisplayFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 32.sp,
                lineHeight = 36.sp,
                letterSpacing = 0.01.em,
                fontSynthesis = SingleWeight,
            ),
        screenTitle =
            TextStyle(
                fontFamily = MangaDisplayFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.01.em,
                fontSynthesis = SingleWeight,
            ),
        sectionTitle =
            TextStyle(
                fontFamily = MangaDisplayFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.02.em,
                fontSynthesis = SingleWeight,
            ),
        cardTitle =
            TextStyle(
                fontFamily = MangaBodyFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        itemTitle =
            TextStyle(
                fontFamily = MangaBodyFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        body =
            TextStyle(
                fontFamily = MangaBodyFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 20.sp,
            ),
        reading =
            TextStyle(
                fontFamily = MangaBodyFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 16.sp,
                lineHeight = 24.sp,
            ),
        caption =
            TextStyle(
                fontFamily = MangaBodyFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontFeatureSettings = "tnum",
            ),
        mono =
            TextStyle(
                fontFamily = MangaMonoFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontSynthesis = SingleWeight,
            ),
        labelLarge =
            TextStyle(
                fontFamily = MangaBodyFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                letterSpacing = 0.01.em,
            ),
        label =
            TextStyle(
                fontFamily = MangaBodyFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.01.em,
            ),
        labelSmall =
            TextStyle(
                fontFamily = MangaBodyFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.02.em,
            ),
        overline =
            TextStyle(
                fontFamily = MangaBodyFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.08.em,
            ),
        stamp =
            TextStyle(
                fontFamily = MangaDisplayFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 14.sp,
                lineHeight = 16.sp,
                letterSpacing = 0.06.em,
                fontSynthesis = SingleWeight,
            ),
        numeral =
            TextStyle(
                fontFamily = MangaDisplayFamily,
                fontWeight = FontWeight.Normal,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                letterSpacing = 0.01.em,
                fontSynthesis = SingleWeight,
            ),
        uppercaseHeadings = true,
    )
