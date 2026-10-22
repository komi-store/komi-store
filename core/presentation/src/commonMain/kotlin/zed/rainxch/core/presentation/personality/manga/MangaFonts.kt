package zed.rainxch.core.presentation.personality.manga

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import zed.rainxch.core.presentation.personality.fonts.KomiFontFamily
import zed.rainxch.core.presentation.personality.fonts.KomiScript
import zed.rainxch.core.presentation.personality.model.PersonalityType

@Composable
fun PersonalityType.withMangaFonts(script: KomiScript): PersonalityType =
    withFamilies(
        display = mangaDisplayFamily(script),
        body = mangaBodyFamily(script),
        mono = KomiFontFamily.jetBrainsMono(),
        // Oswald is variable: at Anton's Regular it reads far lighter than Anton.
        displayWeight = if (script == KomiScript.Cyrillic) FontWeight.Bold else null,
    )

@Composable
private fun mangaDisplayFamily(script: KomiScript): FontFamily =
    when (script) {
        KomiScript.Latin -> KomiFontFamily.anton()
        KomiScript.Cyrillic -> KomiFontFamily.oswald()
        KomiScript.Arabic -> KomiFontFamily.lalezar()
        KomiScript.Devanagari -> KomiFontFamily.khand()
        KomiScript.Bengali -> KomiFontFamily.balooDa2()
        KomiScript.HanSimplified -> KomiFontFamily.zcoolQingKe()
        KomiScript.Japanese -> KomiFontFamily.delaGothicOne()
        KomiScript.Korean -> KomiFontFamily.blackHanSans()
    }

@Composable
private fun mangaBodyFamily(script: KomiScript): FontFamily =
    when (script) {
        KomiScript.Latin -> KomiFontFamily.notoSans()
        KomiScript.Cyrillic -> KomiFontFamily.notoSans()
        KomiScript.Arabic -> KomiFontFamily.notoSansArabic()
        KomiScript.Devanagari -> KomiFontFamily.notoSansDevanagari()
        KomiScript.Bengali -> KomiFontFamily.notoSansBengali()
        KomiScript.HanSimplified -> KomiFontFamily.notoSansSc()
        KomiScript.Japanese -> KomiFontFamily.notoSansJp()
        KomiScript.Korean -> KomiFontFamily.notoSansKr()
    }
