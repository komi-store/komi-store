package zed.rainxch.core.presentation.personality.fonts

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.jetbrains.compose.resources.Font
import org.jetbrains.compose.resources.FontResource
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.anton_regular
import zed.rainxch.githubstore.core.presentation.res.baloo_da_2
import zed.rainxch.githubstore.core.presentation.res.black_han_sans
import zed.rainxch.githubstore.core.presentation.res.dela_gothic_one
import zed.rainxch.githubstore.core.presentation.res.geist_mono
import zed.rainxch.githubstore.core.presentation.res.jetbrains_mono
import zed.rainxch.githubstore.core.presentation.res.khand
import zed.rainxch.githubstore.core.presentation.res.lalezar
import zed.rainxch.githubstore.core.presentation.res.noto_sans
import zed.rainxch.githubstore.core.presentation.res.noto_sans_arabic
import zed.rainxch.githubstore.core.presentation.res.noto_sans_bengali
import zed.rainxch.githubstore.core.presentation.res.noto_sans_devanagari
import zed.rainxch.githubstore.core.presentation.res.noto_sans_jp
import zed.rainxch.githubstore.core.presentation.res.noto_sans_kr
import zed.rainxch.githubstore.core.presentation.res.noto_sans_sc
import zed.rainxch.githubstore.core.presentation.res.nunito
import zed.rainxch.githubstore.core.presentation.res.oswald
import zed.rainxch.githubstore.core.presentation.res.zcool_qingke_huangyou

object KomiFontFamily {
    @Composable
    fun anton(): FontFamily = FontFamily(Font(Res.font.anton_regular, FontWeight.Normal))

    @Composable
    fun jetBrainsMono(): FontFamily = FontFamily(Font(Res.font.jetbrains_mono, FontWeight.Normal))

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
    fun geistMono(): FontFamily =
        FontFamily(
            Font(Res.font.geist_mono, FontWeight.Normal),
            Font(Res.font.geist_mono, FontWeight.Medium),
        )

    @Composable
    fun oswald(): FontFamily = heavy(Res.font.oswald)

    @Composable
    fun lalezar(): FontFamily = heavy(Res.font.lalezar)

    @Composable
    fun khand(): FontFamily = heavy(Res.font.khand)

    @Composable
    fun balooDa2(): FontFamily = heavy(Res.font.baloo_da_2)

    @Composable
    fun zcoolQingKe(): FontFamily = heavy(Res.font.zcool_qingke_huangyou)

    @Composable
    fun delaGothicOne(): FontFamily = heavy(Res.font.dela_gothic_one)

    @Composable
    fun blackHanSans(): FontFamily = heavy(Res.font.black_han_sans)

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
    private fun heavy(resource: FontResource): FontFamily =
        FontFamily(
            Font(resource, FontWeight.Normal),
            Font(resource, FontWeight.Medium),
            Font(resource, FontWeight.SemiBold),
            Font(resource, FontWeight.Bold),
            Font(resource, FontWeight.Black),
        )

    @Composable
    private fun text(resource: FontResource): FontFamily =
        FontFamily(
            Font(resource, FontWeight.Normal),
            Font(resource, FontWeight.Medium),
            Font(resource, FontWeight.SemiBold),
            Font(resource, FontWeight.Bold),
        )
}
