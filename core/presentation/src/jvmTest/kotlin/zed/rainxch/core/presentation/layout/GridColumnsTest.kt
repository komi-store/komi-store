package zed.rainxch.core.presentation.layout

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

class GridColumnsTest {
    @Test
    fun columnCountMatchesFormulaForAnchorCases() {
        val cases =
            listOf(
                Triple(10, 270, 1),
                Triple(11, 270, 1),
                Triple(190, 270, 1),
                Triple(195, 270, 1),
                Triple(270, 270, 1),
                Triple(289, 550, 1),
                Triple(345, 550, 1),
                Triple(400, 270, 2),
                Triple(400, 550, 1),
                Triple(570, 550, 1),
                Triple(571, 550, 2),
                Triple(588, 550, 2),
                Triple(588, 270, 3),
                Triple(640, 550, 2),
                Triple(700, 550, 2),
                Triple(700, 270, 3),
                Triple(820, 550, 2),
                Triple(820, 270, 3),
                Triple(1280, 550, 3),
                Triple(1280, 270, 5),
                Triple(3440, 270, 13),
                Triple(3440, 550, 7),
            )
        for ((width, maxCard, expected) in cases) {
            assertEquals(
                expected,
                gridColumnCount(width.toFloat(), maxCard.toFloat()),
                "width=$width maxCard=$maxCard",
            )
        }
    }

    @Test
    fun widthAtOrBelowLowerBoundClampsToOne() {
        assertEquals(1, gridColumnCount(0f, 270f))
        assertEquals(1, gridColumnCount(-5f, 270f))
        assertEquals(1, gridColumnCount(10f, 270f))
        assertEquals(1, gridColumnCount(0f, 550f))
    }

    @Test
    fun degenerateInputsStayFiniteAndBounded() {
        assertEquals(1, gridColumnCount(Float.POSITIVE_INFINITY, 270f))
        assertEquals(1, gridColumnCount(Float.NEGATIVE_INFINITY, 270f))

        assertEquals(1, gridColumnCount(400f, 0f))
        assertEquals(1, gridColumnCount(400f, -50f))

        val negativeSpacing = gridColumnCount(400f, 270f, spacingDp = -1000f)
        assertEquals(16, negativeSpacing, "negativeSpacing=$negativeSpacing")

        val unboundedPixelWidth = gridColumnCount(Int.MAX_VALUE.toFloat(), 270f)
        assertEquals(16, unboundedPixelWidth, "unboundedPixelWidth=$unboundedPixelWidth")

        val hugeWidth = gridColumnCount(1_000_000f, 270f)
        assertEquals(16, hugeWidth)
    }

    @Test
    fun cellsReproduceTheColumnCountTheseScreensAlreadyHad() {
        val density = Density(density = 1f, fontScale = 1f)
        val maxCardWidth = 550.dp
        val padding = 12.dp

        val regions = listOf(360, 561, 800, 1136, 1280, 1692, 2240, 3440)
        for (regionWidth in regions) {
            val availableSize = regionWidth - 2 * with(density) { padding.roundToPx() }
            val expected = gridColumnCount(regionWidth.toFloat(), maxCardWidth.value)

            val widths = widthCappedCellWidths(density, availableSize, 10, maxCardWidth, padding)

            assertEquals(expected, widths.size, "region=$regionWidth")
            val gridWidth = availableSize - 10 * (widths.size - 1)
            val size = gridWidth / widths.size
            val remainder = gridWidth % widths.size
            widths.forEachIndexed { i, w ->
                assertEquals(size + if (i < remainder) 1 else 0, w, "region=$regionWidth col=$i of ${widths.toList()}")
            }
        }
    }

    @Test
    fun contentPaddingIsPutBackOnBothSides() {
        val density = Density(density = 1f, fontScale = 1f)
        val padded =
            widthCappedCellWidths(density, availableSize = 1110, spacing = 10, maxCardWidth = 550.dp, contentPaddingHorizontal = 12.dp)
        val unpadded =
            widthCappedCellWidths(density, availableSize = 1134, spacing = 10, maxCardWidth = 550.dp, contentPaddingHorizontal = 0.dp)
        assertEquals(3, padded.size, "padded=${padded.toList()}")
        assertEquals(3, unpadded.size, "unpadded=${unpadded.toList()}")
    }

    @Test
    fun countMatchesTheRegionTheEarlierMeasurementSawAtEveryDensity() {
        val paddingPerSide = 12.dp
        val boundaryWidthsDp = listOf(570f, 1130f, 1690f, 2250f)
        for (densityValue in listOf(1f, 1.5f, 2f, 2.125f, 2.625f, 2.75f, 3f, 3.5f)) {
            val density = Density(density = densityValue, fontScale = 1f)
            val paddingPx = with(density) { paddingPerSide.roundToPx() }
            for (boundaryDp in boundaryWidthsDp) {
                val atBoundary = (boundaryDp * densityValue).roundToInt()
                for (regionPx in listOf(atBoundary - 1, atBoundary, atBoundary + 1)) {
                    val availableSize = regionPx - 2 * paddingPx

                    val widths =
                        widthCappedCellWidths(density, availableSize, 10, 550.dp, paddingPerSide)

                    assertEquals(
                        gridColumnCount(regionPx / densityValue, 550f),
                        widths.size,
                        "density=$densityValue regionPx=$regionPx boundary=$boundaryDp",
                    )
                }
            }
        }
    }
}
