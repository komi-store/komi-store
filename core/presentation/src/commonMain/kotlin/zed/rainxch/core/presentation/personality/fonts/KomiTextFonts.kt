package zed.rainxch.core.presentation.personality.fonts

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.noto_sans
import zed.rainxch.githubstore.core.presentation.res.noto_sans_arabic
import zed.rainxch.githubstore.core.presentation.res.noto_sans_bengali
import zed.rainxch.githubstore.core.presentation.res.noto_sans_devanagari
import zed.rainxch.githubstore.core.presentation.res.noto_sans_jp
import zed.rainxch.githubstore.core.presentation.res.noto_sans_kr
import zed.rainxch.githubstore.core.presentation.res.noto_sans_sc
import zed.rainxch.githubstore.core.presentation.res.nunito

object KomiTextFonts {
    @Composable
    fun nunito(): FontFamily =
        FontFamily(
            Font(Res.font.nunito, FontWeight.Normal),
            Font(Res.font.nunito, FontWeight.Medium),
            Font(Res.font.nunito, FontWeight.SemiBold),
            Font(Res.font.nunito, FontWeight.Bold),
            Font(Res.font.nunito, FontWeight.ExtraBold),
            Font(Res.font.nunito, FontWeight.Black),
        )

    @Composable
    fun notoSans(): FontFamily = text(Res.font.noto_sans)

    @Composable
    fun notoSansArabic(): FontFamily = text(Res.font.noto_sans_arabic)

    @Composable
    fun notoSansBengali(): FontFamily = text(Res.font.noto_sans_bengali)

    @Composable
    fun notoSansDevanagari(): FontFamily = text(Res.font.noto_sans_devanagari)

    @Composable
    fun notoSansSc(): FontFamily = text(Res.font.noto_sans_sc)

    @Composable
    fun notoSansJp(): FontFamily = text(Res.font.noto_sans_jp)

    @Composable
    fun notoSansKr(): FontFamily = text(Res.font.noto_sans_kr)

    @Composable
    private fun text(resource: FontResource): FontFamily =
        FontFamily(
            Font(resource, FontWeight.Normal),
            Font(resource, FontWeight.Medium),
            Font(resource, FontWeight.SemiBold),
            Font(resource, FontWeight.Bold),
        )
}
