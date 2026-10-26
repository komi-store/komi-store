package zed.rainxch.core.presentation.components

import androidx.compose.foundation.LocalScrollbarStyle
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
import androidx.compose.foundation.v2.ScrollbarAdapter
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
        scrollbar = {
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
                style = scrollbarStyle(),
            )
        },
        modifier = modifier,
        content = content,
    )
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
        scrollbar = {
            VerticalScrollbar(
                adapter = remember(gridState) { StaggeredGridScrollbarAdapter(gridState) },
                modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
                style = scrollbarStyle(),
            )
        },
        modifier = modifier,
        content = content,
    )
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
        scrollbar = {
            VerticalScrollbar(
                adapter = remember(gridState) { GridScrollbarAdapter(gridState) },
                modifier = Modifier.fillMaxHeight().align(Alignment.CenterEnd),
                style = scrollbarStyle(),
            )
        },
        modifier = modifier,
        content = content,
    )
}

@Composable
private fun ScrollbarContainerShell(
    enabled: Boolean,
    scrollbar: @Composable BoxScope.() -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(modifier = if (enabled) modifier.padding(start = 8.dp) else modifier) {
        content()
        if (enabled) scrollbar()
    }
}

@Composable
private fun scrollbarStyle(): androidx.compose.foundation.ScrollbarStyle =
    LocalScrollbarStyle.current.copy(
        shape = RoundedCornerShape(32.dp),
        unhoverColor = MaterialTheme.colorScheme.onSurface,
        hoverColor = MaterialTheme.colorScheme.onSurfaceVariant,
    )

// The content height is an estimate from the visible items, so it is cached per layout pass.
private abstract class EstimatedGridScrollbarAdapter : ScrollbarAdapter {
    private var cachedLayout: Any? = null
    private var cachedContentSize = 0f

    protected abstract val currentLayout: Any
    protected abstract val totalItemsCount: Int
    protected abstract val firstVisibleIndex: Int?
    protected abstract val firstVisibleOffsetY: Int
    protected abstract val viewportHeight: Int

    protected abstract fun estimateContentSize(): Float

    protected abstract suspend fun scrollToItem(index: Int)

    protected abstract fun scrollByPixels(delta: Float)

    private fun estimatedContentSize(): Float {
        val layout = currentLayout
        if (layout === cachedLayout) return cachedContentSize
        val size = estimateContentSize()
        cachedLayout = layout
        cachedContentSize = size
        return size
    }

    private val averageItemSize: Float
        get() {
            val count = totalItemsCount
            return if (count > 0) estimatedContentSize() / count else 0f
        }

    override val scrollOffset: Double
        get() {
            val index = firstVisibleIndex ?: return 0.0
            return (index * averageItemSize - firstVisibleOffsetY).toDouble()
        }

    override val viewportSize: Double
        get() = viewportHeight.toDouble()

    override val contentSize: Double
        get() = ((estimatedContentSize() - viewportHeight).coerceAtLeast(0f) + viewportHeight).toDouble()

    override suspend fun scrollTo(scrollOffset: Double) {
        val offset = scrollOffset.toFloat()
        val itemSize = averageItemSize
        if (itemSize <= 0f) return
        val exactIndex = offset / itemSize
        val targetIndex = exactIndex.toInt().coerceIn(0, totalItemsCount - 1)
        scrollToItem(targetIndex)
        val remainder = offset - targetIndex * itemSize
        if (remainder != 0f) {
            scrollByPixels(remainder)
        }
    }
}

private class StaggeredGridScrollbarAdapter(
    private val gridState: LazyStaggeredGridState,
) : EstimatedGridScrollbarAdapter() {
    override val currentLayout: Any
        get() = gridState.layoutInfo
    override val totalItemsCount: Int
        get() = gridState.layoutInfo.totalItemsCount
    override val firstVisibleIndex: Int?
        get() =
            gridState.layoutInfo.visibleItemsInfo
                .firstOrNull()
                ?.index
    override val firstVisibleOffsetY: Int
        get() =
            gridState.layoutInfo.visibleItemsInfo
                .firstOrNull()
                ?.offset
                ?.y ?: 0
    override val viewportHeight: Int
        get() = gridState.layoutInfo.viewportSize.height

    override fun estimateContentSize(): Float = estimateStaggeredGridContentSize(gridState.layoutInfo)

    override suspend fun scrollToItem(index: Int) = gridState.scrollToItem(index)

    override fun scrollByPixels(delta: Float) {
        gridState.dispatchRawDelta(delta)
    }
}

private class GridScrollbarAdapter(
    private val gridState: LazyGridState,
) : EstimatedGridScrollbarAdapter() {
    override val currentLayout: Any
        get() = gridState.layoutInfo
    override val totalItemsCount: Int
        get() = gridState.layoutInfo.totalItemsCount
    override val firstVisibleIndex: Int?
        get() =
            gridState.layoutInfo.visibleItemsInfo
                .firstOrNull()
                ?.index
    override val firstVisibleOffsetY: Int
        get() =
            gridState.layoutInfo.visibleItemsInfo
                .firstOrNull()
                ?.offset
                ?.y ?: 0
    override val viewportHeight: Int
        get() = gridState.layoutInfo.viewportSize.height

    override fun estimateContentSize(): Float = estimateLazyGridContentSize(gridState.layoutInfo)

    override suspend fun scrollToItem(index: Int) = gridState.scrollToItem(index)

    override fun scrollByPixels(delta: Float) {
        gridState.dispatchRawDelta(delta)
    }
}

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
