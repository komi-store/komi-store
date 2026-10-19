package zed.rainxch.core.presentation.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

private const val MAX_COLUMNS = 16

private const val MIN_POSITIVE = 1e-3f

fun gridColumnCount(
    contentWidthDp: Float,
    maxCardWidthDp: Float,
    spacingDp: Float = CardGridSpec.GridSpacing.value,
): Int {
    if (!contentWidthDp.isFinite() || contentWidthDp <= 0f) return 1
    if (!maxCardWidthDp.isFinite() || maxCardWidthDp <= 0f) return 1
    if (!spacingDp.isFinite()) return 1
    val denom = (maxCardWidthDp + spacingDp).coerceAtLeast(MIN_POSITIVE)
    val raw = ceil((contentWidthDp - spacingDp) / denom).toInt()
    return raw.coerceIn(1, MAX_COLUMNS)
}

object CardGridSpec {
    val InfoMaxCardWidth: Dp = 550.dp
    val CompactMaxCardWidth: Dp = 260.dp
    val ChartCompactMaxCardWidth: Dp = 420.dp
    val GridSpacing: Dp = 10.dp

    val GridArrangement: Arrangement.Horizontal = Arrangement.spacedBy(GridSpacing)

    val GridItemSpacing: Dp = GridSpacing
}

@Stable
data class WidthCappedGridCells(
    val maxCardWidth: Dp,
    val contentPaddingHorizontal: Dp = 0.dp,
) : GridCells {
    override fun Density.calculateCrossAxisCellSizes(
        availableSize: Int,
        spacing: Int,
    ): List<Int> =
        widthCappedCellWidths(this, availableSize, spacing, maxCardWidth, contentPaddingHorizontal)
            .toList()
}

@Stable
data class WidthCappedStaggeredCells(
    val maxCardWidth: Dp,
    val contentPaddingHorizontal: Dp = 0.dp,
) : StaggeredGridCells {
    override fun Density.calculateCrossAxisCellSizes(
        availableSize: Int,
        spacing: Int,
    ): IntArray = widthCappedCellWidths(this, availableSize, spacing, maxCardWidth, contentPaddingHorizontal)
}

@Composable
fun rememberWidthCappedGridCells(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    maxCardWidth: Dp = CardGridSpec.InfoMaxCardWidth,
): GridCells {
    val horizontal = contentPadding.calculateStartPadding(LocalLayoutDirection.current)
    return remember(maxCardWidth, horizontal) {
        WidthCappedGridCells(maxCardWidth, horizontal)
    }
}

@Composable
fun rememberWidthCappedStaggeredCells(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    maxCardWidth: Dp = CardGridSpec.InfoMaxCardWidth,
): StaggeredGridCells {
    val horizontal = contentPadding.calculateStartPadding(LocalLayoutDirection.current)
    return remember(maxCardWidth, horizontal) {
        WidthCappedStaggeredCells(maxCardWidth, horizontal)
    }
}

internal fun widthCappedCellWidths(
    density: Density,
    availableSize: Int,
    spacing: Int,
    maxCardWidth: Dp,
    contentPaddingHorizontal: Dp,
): IntArray {
    val paddingPx = with(density) { contentPaddingHorizontal.roundToPx() }
    val columns =
        gridColumnCount(
            contentWidthDp = (availableSize + paddingPx * 2) / density.density,
            maxCardWidthDp = maxCardWidth.value,
        )
    val total = availableSize - spacing * (columns - 1)
    val size = total / columns
    val remainder = total % columns
    return IntArray(columns) { i -> (size + if (i < remainder) 1 else 0).coerceAtLeast(0) }
}
