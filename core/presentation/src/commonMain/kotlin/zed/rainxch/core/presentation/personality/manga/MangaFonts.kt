package zed.rainxch.core.presentation.personality.manga

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import zed.rainxch.core.presentation.personality.fonts.KomiDisplayFonts
import zed.rainxch.core.presentation.personality.fonts.KomiMonoFonts
import zed.rainxch.core.presentation.personality.fonts.KomiScript
import zed.rainxch.core.presentation.personality.fonts.KomiTextFonts
import zed.rainxch.core.presentation.personality.model.PersonalityType

@Composable
fun PersonalityType.withMangaFonts(script: KomiScript): PersonalityType =
    withFamilies(
        display = mangaDisplayFamily(script),
        body = mangaBodyFamily(script),
        mono = KomiMonoFonts.jetBrainsMono(),
        // Oswald is variable: at Anton's Regular it reads far lighter than Anton.
        displayWeight = if (script == KomiScript.Cyrillic) FontWeight.Bold else null,
    )

@Composable
private fun mangaDisplayFamily(script: KomiScript): FontFamily =
    when (script) {
        KomiScript.Latin -> KomiDisplayFonts.anton()
        KomiScript.Cyrillic -> KomiDisplayFonts.oswald()
        KomiScript.Arabic -> KomiDisplayFonts.lalezar()
        KomiScript.Devanagari -> KomiDisplayFonts.khand()
        KomiScript.Bengali -> KomiDisplayFonts.balooDa2()
        KomiScript.HanSimplified -> KomiDisplayFonts.zcoolQingKe()
        KomiScript.Japanese -> KomiDisplayFonts.delaGothicOne()
        KomiScript.Korean -> KomiDisplayFonts.blackHanSans()
    }

@Composable
private fun mangaBodyFamily(script: KomiScript): FontFamily =
    when (script) {
        KomiScript.Latin -> KomiTextFonts.notoSans()
        KomiScript.Cyrillic -> KomiTextFonts.notoSans()
        KomiScript.Arabic -> KomiTextFonts.notoSansArabic()
        KomiScript.Devanagari -> KomiTextFonts.notoSansDevanagari()
        KomiScript.Bengali -> KomiTextFonts.notoSansBengali()
        KomiScript.HanSimplified -> KomiTextFonts.notoSansSc()
        KomiScript.Japanese -> KomiTextFonts.notoSansJp()
        KomiScript.Korean -> KomiTextFonts.notoSansKr()
    }
