package zed.rainxch.core.presentation.components.dividers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality

@Composable
fun KomiHorizontalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = Dp.Unspecified,
    color: Color = Color.Unspecified,
) {
    val personality = LocalPersonality.current
    val isManga = personality is MangaPersonality
    val resolvedThickness =
        if (thickness != Dp.Unspecified) {
            thickness
        } else if (isManga) {
            2.5.dp
        } else {
            1.dp
        }
    val resolvedColor =
        if (color != Color.Unspecified) {
            color
        } else {
            personality.colors.divider
        }
    Box(
        modifier
            .fillMaxWidth()
            .height(resolvedThickness)
            .background(resolvedColor),
    )
}

@Composable
fun KomiVerticalDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = Dp.Unspecified,
    color: Color = Color.Unspecified,
) {
    val personality = LocalPersonality.current
    val isManga = personality is MangaPersonality
    val resolvedThickness =
        if (thickness != Dp.Unspecified) {
            thickness
        } else if (isManga) {
            2.5.dp
        } else {
            1.dp
        }
    val resolvedColor =
        if (color != Color.Unspecified) {
            color
        } else {
            personality.colors.divider
        }
    Box(
        modifier
            .fillMaxHeight()
            .width(resolvedThickness)
            .background(resolvedColor),
    )
}
