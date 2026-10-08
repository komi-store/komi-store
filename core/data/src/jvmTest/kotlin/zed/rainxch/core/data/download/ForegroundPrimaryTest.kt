package zed.rainxch.core.data.download

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ForegroundPrimaryTest {

    @Test
    fun theMostRecentlyStartedDownloadWins() {
        val chosen =
            ForegroundPrimary.choose(
                activePackages = listOf("com.a", "com.b", "com.c"),
                activeSince = mapOf("com.a" to 100L, "com.b" to 300L, "com.c" to 200L),
            )
        assertEquals("com.b", chosen)
    }

    @Test
    fun nothingActiveMeansNoPrimary() {
        assertNull(ForegroundPrimary.choose(activePackages = emptyList(), activeSince = emptyMap()))
    }

    @Test
    fun aPackageWithNoStartTimeCannotDisplaceOneThatHasIt() {
        val chosen =
            ForegroundPrimary.choose(
                activePackages = listOf("com.unknown", "com.known"),
                activeSince = mapOf("com.known" to 5L),
            )
        assertEquals("com.known", chosen)
    }

    @Test
    fun tiesResolveDeterministicallyAndIndependentOfInputOrder() {
        val timestamps = mapOf("com.beta" to 42L, "com.alpha" to 42L)
        val forward =
            ForegroundPrimary.choose(
                activePackages = listOf("com.beta", "com.alpha"),
                activeSince = timestamps,
            )
        val reverse =
            ForegroundPrimary.choose(
                activePackages = listOf("com.alpha", "com.beta"),
                activeSince = timestamps,
            )
        assertEquals(forward, reverse, "the winner must not depend on iteration order")
        assertEquals("com.beta", forward, "the greater package name wins a tie")
    }

    @Test
    fun severalPackagesWithNoStartTimeStillResolveDeterministically() {
        val forward =
            ForegroundPrimary.choose(
                activePackages = listOf("com.b", "com.a"),
                activeSince = emptyMap(),
            )
        val reverse =
            ForegroundPrimary.choose(
                activePackages = listOf("com.a", "com.b"),
                activeSince = emptyMap(),
            )
        assertEquals(forward, reverse)
        assertEquals("com.b", forward, "the greater package name wins a tie of unknown times")
    }

    @Test
    fun aSingleActiveDownloadNeedsNoTimestamps() {
        assertEquals(
            "com.only",
            ForegroundPrimary.choose(
                activePackages = listOf("com.only"),
                activeSince = emptyMap(),
            ),
        )
    }
}
