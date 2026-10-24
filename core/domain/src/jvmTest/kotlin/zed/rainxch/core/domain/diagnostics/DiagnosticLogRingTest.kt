package zed.rainxch.core.domain.diagnostics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiagnosticLogRingTest {
    @Test
    fun content_under_the_cap_is_returned_unchanged() {
        val content = "line one\nline two\nline three"
        assertEquals(content, DiagnosticLogRing.trimOldest(content, maxBytes = 1_000))
    }

    @Test
    fun trimming_drops_the_oldest_lines() {
        val content = (1..50).joinToString("\n") { "line $it" }
        val trimmed = DiagnosticLogRing.trimOldest(content, maxBytes = 100)
        assertTrue(trimmed.encodeToByteArray().size <= 100)
        assertTrue(trimmed.endsWith("line 50"))
        assertFalse(trimmed.contains("line 1\n"))
        assertFalse(trimmed.contains("line 20\n"))
    }

    @Test
    fun trimming_respects_the_target_budget() {
        val content = (1..100).joinToString("\n") { "entry-$it" }
        val trimmed = DiagnosticLogRing.trimOldest(content, maxBytes = 400, targetBytes = 100)
        assertTrue(trimmed.encodeToByteArray().size <= 100)
        assertTrue(trimmed.endsWith("entry-100"))
    }

    @Test
    fun an_oversized_single_line_keeps_its_newest_tail() {
        val content = "x".repeat(200)
        val trimmed = DiagnosticLogRing.trimOldest(content, maxBytes = 50)
        assertEquals(50, trimmed.length)
        assertEquals(content.takeLast(50), trimmed)
    }

    @Test
    fun a_multibyte_tail_is_never_cut_in_the_middle_of_a_character() {
        val content = "日".repeat(100)
        val trimmed = DiagnosticLogRing.trimOldest(content, maxBytes = 90)
        assertTrue(trimmed.encodeToByteArray().size <= 90)
        assertEquals("日".repeat(30), trimmed)
    }

    @Test
    fun empty_content_stays_empty() {
        assertEquals("", DiagnosticLogRing.trimOldest("", maxBytes = 100))
    }
}
