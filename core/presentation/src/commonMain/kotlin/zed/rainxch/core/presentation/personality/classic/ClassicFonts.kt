package zed.rainxch.core.presentation.personality.classic

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import zed.rainxch.core.presentation.personality.fonts.KomiFontFamily
import zed.rainxch.core.presentation.personality.fonts.KomiScript
import zed.rainxch.core.presentation.personality.model.PersonalityType

@Composable
fun PersonalityType.withClassicFonts(script: KomiScript): PersonalityType {
    val display = classicDisplayFamily(script)
    return withFamilies(
        display = display,
        body = display,
        mono = KomiFontFamily.geistMono(),
    )
}

@Composable
private fun classicDisplayFamily(script: KomiScript): FontFamily =
    when (script) {
        KomiScript.Latin, KomiScript.Cyrillic -> KomiFontFamily.nunito()
        else -> FontFamily.Default
    }
