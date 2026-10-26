package zed.rainxch.core.presentation.personality.fonts

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.geist_mono
import zed.rainxch.githubstore.core.presentation.res.jetbrains_mono

object KomiMonoFonts {
    @Composable
    fun jetBrainsMono(): FontFamily = FontFamily(Font(Res.font.jetbrains_mono, FontWeight.Normal))

    @Composable
    fun geistMono(): FontFamily =
        FontFamily(
            Font(Res.font.geist_mono, FontWeight.Normal),
            Font(Res.font.geist_mono, FontWeight.Medium),
        )
}
