package zed.rainxch.core.presentation.components

import kotlin.test.Test
import kotlin.test.assertEquals

class GridContentHeightTest {
    private fun item(
        height: Int,
        span: Int,
    ) = GridItemExtent(height = height, span = span)

    private fun assertEstimate(
        expected: Float,
        actual: Float,
    ) = assertEquals(expected, actual, absoluteTolerance = 0.01f)

    @Test
    fun emptyWindowEstimatesNothing() {
        assertEstimate(
            0f,
            estimateGridContentHeight(
                totalItemsCount = 12,
                visibleItems = emptyList(),
                lanes = 3,
                mainAxisItemSpacing = 0,
                contentPadding = 0,
            ),
        )
        assertEstimate(
            0f,
            estimateGridContentHeight(
                totalItemsCount = 0,
                visibleItems = listOf(item(200, 1)),
                lanes = 3,
                mainAxisItemSpacing = 0,
                contentPadding = 0,
            ),
        )
    }

    @Test
    fun fullLineBannerAboveACardRowUsesTheWeightedLineHeight() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 4,
                visibleItems =
                    listOf(
                        item(height = 40, span = 3),
                        item(height = 200, span = 1),
                        item(height = 200, span = 1),
                        item(height = 200, span = 1),
                    ),
                lanes = 3,
                mainAxisItemSpacing = 0,
                contentPadding = 0,
            )
        assertEstimate(240f, height)
    }

    @Test
    fun fullLineBannerInATwoLaneGridUsesTheWeightedLineHeight() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 4,
                visibleItems =
                    listOf(
                        item(height = 30, span = 2),
                        item(height = 30, span = 2),
                        item(height = 100, span = 1),
                        item(height = 100, span = 1),
                    ),
                lanes = 2,
                mainAxisItemSpacing = 0,
                contentPadding = 0,
            )
        assertEstimate(160f, height)
    }

    @Test
    fun uniformGridKeepsTheExactAnswer() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 99,
                visibleItems = listOf(item(height = 100, span = 1), item(height = 100, span = 1)),
                lanes = 3,
                mainAxisItemSpacing = 0,
                contentPadding = 0,
            )
        assertEstimate(3300f, height)
    }

    @Test
    fun partiallyFilledWindowStillDescribesWholeLines() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 9,
                visibleItems = listOf(item(height = 200, span = 1), item(height = 200, span = 1)),
                lanes = 3,
                mainAxisItemSpacing = 0,
                contentPadding = 0,
            )
        assertEstimate(600f, height)
    }

    @Test
    fun fullLineItemsCountOneLineEach() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 6,
                visibleItems = listOf(item(height = 60, span = 3), item(height = 60, span = 3)),
                lanes = 3,
                mainAxisItemSpacing = 0,
                contentPadding = 0,
            )
        assertEstimate(360f, height)
    }

    @Test
    fun singleLaneGridIsExact() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 3,
                visibleItems = listOf(item(height = 50, span = 1)),
                lanes = 1,
                mainAxisItemSpacing = 0,
                contentPadding = 0,
            )
        assertEstimate(150f, height)
    }

    @Test
    fun spacingAppliesBetweenLinesAndNotAfterTheLast() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 9,
                visibleItems = listOf(item(height = 200, span = 1), item(height = 200, span = 1)),
                lanes = 3,
                mainAxisItemSpacing = 10,
                contentPadding = 0,
            )
        assertEstimate(620f, height)
    }

    @Test
    fun contentPaddingIsIncluded() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 3,
                visibleItems = listOf(item(height = 50, span = 1)),
                lanes = 1,
                mainAxisItemSpacing = 0,
                contentPadding = 24,
            )
        assertEstimate(174f, height)
    }

    @Test
    fun singleLineAddsNoSpacing() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 3,
                visibleItems = listOf(item(height = 50, span = 1), item(height = 50, span = 1)),
                lanes = 3,
                mainAxisItemSpacing = 10,
                contentPadding = 0,
            )
        assertEstimate(50f, height)
    }

    @Test
    fun zeroSpansDoNotProduceANonFiniteEstimate() {
        val height =
            estimateGridContentHeight(
                totalItemsCount = 6,
                visibleItems = listOf(item(height = 200, span = 0)),
                lanes = 3,
                mainAxisItemSpacing = 0,
                contentPadding = 0,
            )
        assertEstimate(0f, height)
    }
}
