@file:Suppress("DEPRECATION")

package zed.rainxch.core.presentation.components

import androidx.compose.foundation.LocalScrollbarStyle
import androidx.compose.foundation.ScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridLayoutInfo
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
actual fun ScrollbarContainer(
    listState: LazyListState,
    enabled: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    ScrollbarContainerShell(
        enabled = enabled,
        modifier = modifier,
        content = content,
    ) {
        VerticalScrollbar(
            adapter = rememberScrollbarAdapter(listState),
            modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
            style = scrollbarStyle(),
        )
    }
}

@Composable
actual fun ScrollbarContainer(
    gridState: LazyStaggeredGridState,
    enabled: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    ScrollbarContainerShell(
        enabled = enabled,
        modifier = modifier,
        content = content,
    ) {
        VerticalScrollbar(
            adapter = remember(gridState) { StaggeredGridScrollbarAdapter(gridState) },
            modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
            style = scrollbarStyle(),
        )
    }
}

@Composable
actual fun ScrollbarContainer(
    gridState: LazyGridState,
    enabled: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    ScrollbarContainerShell(
        enabled = enabled,
        modifier = modifier,
        content = content,
    ) {
        VerticalScrollbar(
            adapter = remember(gridState) { GridScrollbarAdapter(gridState) },
            modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
            style = scrollbarStyle(),
        )
    }
}

@Composable
private fun ScrollbarContainerShell(
    enabled: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
    scrollbar: @Composable BoxScope.() -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier) {
            content()
        }
        return
    }
    Box(modifier = modifier.padding(start = 8.dp)) {
        content()
        scrollbar()
    }
}

@Composable
private fun scrollbarStyle(): androidx.compose.foundation.ScrollbarStyle =
    LocalScrollbarStyle.current.copy(
        shape = RoundedCornerShape(32.dp),
        unhoverColor = MaterialTheme.colorScheme.onSurface,
        hoverColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

private class GridScrollbarAdapterBase(
    private val totalItemsCount: () -> Int,
    private val firstVisibleIndex: () -> Int?,
    private val firstVisibleOffsetY: () -> Int,
    private val estimateContentSize: () -> Float,
    private val scrollToItem: suspend (Int) -> Unit,
    private val scrollByPixels: (Float) -> Unit,
    private val measureResult: () -> Any,
) : ScrollbarAdapter {
    private var cachedMeasureResult: Any? = null
    private var cachedContentSize = 0f

    private fun estimatedContentSize(): Float {
        val key = measureResult()
        if (key === cachedMeasureResult) return cachedContentSize
        val size = estimateContentSize()
        cachedMeasureResult = key
        cachedContentSize = size
        return size
    }

    private val averageItemSize: Float
        get() {
            val count = totalItemsCount()
            return if (count > 0) estimatedContentSize() / count else 0f
        }

    override val scrollOffset: Float
        get() {
            val index = firstVisibleIndex() ?: return 0f
            return index * averageItemSize - firstVisibleOffsetY()
        }

    override fun maxScrollOffset(containerSize: Int): Float = (estimatedContentSize() - containerSize).coerceAtLeast(0f)

    override suspend fun scrollTo(
        containerSize: Int,
        scrollOffset: Float,
    ) {
        val itemSize = averageItemSize
        if (itemSize <= 0f) return
        val exactIndex = scrollOffset / itemSize
        val targetIndex = exactIndex.toInt().coerceIn(0, totalItemsCount() - 1)
        scrollToItem(targetIndex)
        val remainder = scrollOffset - targetIndex * itemSize
        if (remainder != 0f) {
            scrollByPixels(remainder)
        }
    }
}

private fun StaggeredGridScrollbarAdapter(
    gridState: LazyStaggeredGridState,
): GridScrollbarAdapterBase =
    GridScrollbarAdapterBase(
        totalItemsCount = { gridState.layoutInfo.totalItemsCount },
        firstVisibleIndex = { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index },
        firstVisibleOffsetY = { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.offset?.y ?: 0 },
        estimateContentSize = { estimateStaggeredGridContentSize(gridState.layoutInfo) },
        scrollToItem = { gridState.scrollToItem(it) },
        scrollByPixels = { delta -> gridState.dispatchRawDelta(delta) },
        measureResult = { gridState.layoutInfo },
    )

private fun GridScrollbarAdapter(
    gridState: LazyGridState,
): GridScrollbarAdapterBase =
    GridScrollbarAdapterBase(
        totalItemsCount = { gridState.layoutInfo.totalItemsCount },
        firstVisibleIndex = { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index },
        firstVisibleOffsetY = { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.offset?.y ?: 0 },
        estimateContentSize = { estimateLazyGridContentSize(gridState.layoutInfo) },
        scrollToItem = { gridState.scrollToItem(it) },
        scrollByPixels = { delta -> gridState.dispatchRawDelta(delta) },
        measureResult = { gridState.layoutInfo },
    )

private fun estimateStaggeredGridContentSize(layoutInfo: LazyStaggeredGridLayoutInfo): Float {
    if (layoutInfo.totalItemsCount == 0) return 0f
    val visibleItems = layoutInfo.visibleItemsInfo
    if (visibleItems.isEmpty()) return 0f
    val avgHeight = visibleItems.map { it.size.height }.average().toFloat()
    val laneCount =
        maxOf(
            visibleItems.maxOf { it.lane + 1 },
            1,
        )
    val rows = (layoutInfo.totalItemsCount + laneCount - 1) / laneCount
    return rows * avgHeight + layoutInfo.beforeContentPadding + layoutInfo.afterContentPadding
}

internal data class GridItemExtent(
    val height: Int,
    val span: Int,
)

internal fun estimateGridContentHeight(
    totalItemsCount: Int,
    visibleItems: List<GridItemExtent>,
    lanes: Int,
    mainAxisItemSpacing: Int,
    contentPadding: Int,
): Float {
    if (totalItemsCount == 0) return 0f
    if (visibleItems.isEmpty()) return 0f
    val laneCount = maxOf(lanes, 1)
    var lineSpanSum = 0L
    var weightedHeightSum = 0f
    for (item in visibleItems) {
        lineSpanSum += item.span
        weightedHeightSum += item.height * (item.span.toFloat() / laneCount)
    }
    val linesOccupied = lineSpanSum.toFloat() / laneCount
    val linesPerItem = linesOccupied / visibleItems.size
    val estimatedLines = totalItemsCount * linesPerItem
    val avgLineHeight = if (linesOccupied > 0f) weightedHeightSum / linesOccupied else 0f
    val lineSpacing = if (estimatedLines > 1f) (estimatedLines - 1f) * mainAxisItemSpacing.toFloat() else 0f
    return estimatedLines * avgLineHeight + lineSpacing + contentPadding
}

private fun estimateLazyGridContentSize(layoutInfo: LazyGridLayoutInfo): Float =
    estimateGridContentHeight(
        totalItemsCount = layoutInfo.totalItemsCount,
        visibleItems = layoutInfo.visibleItemsInfo.map { GridItemExtent(it.size.height, it.span) },
        lanes = layoutInfo.maxSpan,
        mainAxisItemSpacing = layoutInfo.mainAxisItemSpacing,
        contentPadding = layoutInfo.beforeContentPadding + layoutInfo.afterContentPadding,
    )
