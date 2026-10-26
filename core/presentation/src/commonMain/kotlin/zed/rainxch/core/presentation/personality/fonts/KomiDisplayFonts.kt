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
import zed.rainxch.githubstore.core.presentation.res.khand
import zed.rainxch.githubstore.core.presentation.res.lalezar
import zed.rainxch.githubstore.core.presentation.res.oswald
import zed.rainxch.githubstore.core.presentation.res.zcool_qingke_huangyou

object KomiDisplayFonts {
    @Composable
    fun anton(): FontFamily = FontFamily(Font(Res.font.anton_regular, FontWeight.Normal))

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
    private fun heavy(resource: FontResource): FontFamily =
        FontFamily(
            Font(resource, FontWeight.Normal),
            Font(resource, FontWeight.Medium),
            Font(resource, FontWeight.SemiBold),
            Font(resource, FontWeight.Bold),
            Font(resource, FontWeight.Black),
        )
}
