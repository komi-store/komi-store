package zed.rainxch.core.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StarredTimestampParseTest {
    @Test
    fun `an iso timestamp parses to epoch milliseconds`() {
        assertEquals(1_000L, "1970-01-01T00:00:01Z".toEpochMillisecondsOrNull())
    }

    @Test
    fun `a missing value stays null`() {
        assertNull((null as String?).toEpochMillisecondsOrNull())
    }

    @Test
    fun `an unparsable value degrades to null instead of throwing`() {
        assertNull("not-a-timestamp".toEpochMillisecondsOrNull())
        assertNull("".toEpochMillisecondsOrNull())
    }
}
