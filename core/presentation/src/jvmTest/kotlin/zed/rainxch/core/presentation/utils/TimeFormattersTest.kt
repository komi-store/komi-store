package zed.rainxch.core.presentation.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TimeFormattersTest {
    private fun assertSameDate(
        canonical: String,
        lenient: String,
    ) {
        val expected = assertNotNull(formatIsoDate(canonical))
        assertEquals(expected, formatIsoDate(lenient))
    }

    @Test
    fun a_space_between_date_and_time_reads_like_a_t() {
        assertSameDate("2024-05-01T10:20:30Z", "2024-05-01 10:20:30Z")
    }

    @Test
    fun an_offset_without_minutes_reads_as_whole_hours() {
        assertSameDate("2024-05-01T22:20:30+05:00", "2024-05-01T22:20:30+05")
    }

    @Test
    fun missing_seconds_and_minutes_read_as_zero() {
        assertSameDate("2024-05-01T23:20:00Z", "2024-05-01T23:20Z")
        assertSameDate("2024-05-01T23:00:00+02:00", "2024-05-01T23+02:00")
    }

    @Test
    fun a_bare_date_reads_as_midnight_utc() {
        assertSameDate("2024-05-01T00:00:00Z", "2024-05-01")
    }

    @Test
    fun blank_or_unparseable_text_gives_no_date() {
        assertNull(formatIsoDate("   "))
        assertNull(formatIsoDate("yesterday"))
        assertNull(formatIsoDate(null))
    }
}
