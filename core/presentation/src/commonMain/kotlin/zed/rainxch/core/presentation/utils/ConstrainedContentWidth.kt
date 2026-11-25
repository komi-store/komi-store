package zed.rainxch.core.presentation.utils

import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import zed.rainxch.core.domain.model.appearance.ContentWidth
import zed.rainxch.core.presentation.layout.currentWindowWidth
import zed.rainxch.core.presentation.locals.LocalContentWidth

private val MIN_CONTENT_DP = 480.dp
private val MAX_CONTENT_DP = 1600.dp
private const val COMPACT_FRACTION = 0.55f
private const val WIDE_FRACTION = 0.75f
private const val EXTRA_WIDE_FRACTION = 0.95f

private fun ContentWidth.fraction(): Float =
    when (this) {
        ContentWidth.COMPACT -> COMPACT_FRACTION
        ContentWidth.WIDE -> WIDE_FRACTION
        ContentWidth.EXTRA_WIDE -> EXTRA_WIDE_FRACTION
    }

@Composable
@ReadOnlyComposable
fun contentWidthCap(): Dp {
    val fraction = LocalContentWidth.current.fraction()
    val windowDp = currentWindowWidth()
    return (windowDp * fraction).coerceIn(MIN_CONTENT_DP, MAX_CONTENT_DP)
}

@Composable
@ReadOnlyComposable
fun Modifier.constrainedContentWidth(): Modifier = widthIn(max = contentWidthCap())
