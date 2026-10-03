package zed.rainxch.core.domain.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import zed.rainxch.core.domain.model.account.github.GithubRelease

class ReleaseLinesTest {
    private fun release(tag: String) =
        GithubRelease(
            id = tag.hashCode().toLong(),
            tagName = tag,
            name = tag,
            publishedAt = "2026-01-01T00:00:00Z",
            description = null,
            assets = emptyList(),
            tarballUrl = "",
            zipballUrl = "",
            htmlUrl = "",
        )

    private fun releases(vararg tags: String) = tags.map(::release)

    @Test
    fun lineIsTheTagWithoutItsVersion() {
        assertEquals("auth", ReleaseLines.of("auth-v4.4.25"))
        assertEquals("photos", ReleaseLines.of("photos-v1.3.64"))
        assertEquals("thunderbird", ReleaseLines.of("THUNDERBIRD_24_0b2"))
        assertEquals("k9mail", ReleaseLines.of("K9MAIL_23_1"))
        assertEquals("", ReleaseLines.of("v3.4.8"))
        assertEquals("", ReleaseLines.of("4.7.2-stable"))
        assertEquals("nightly", ReleaseLines.of("nightly"))
    }

    @Test
    fun platformWordsDoNotSplitAnApp() {
        assertEquals("", ReleaseLines.of("3.4.13-android"))
        assertEquals("", ReleaseLines.of("desktop-v2026.9.1"))
        assertEquals(
            ReleaseLines.of("@standardnotes/desktop@3.202.8"),
            ReleaseLines.of("@standardnotes/mobile@3.202.8"),
        )
    }

    @Test
    fun interleavedLinesAreSeparateApps() {
        assertTrue(
            ReleaseLines.isMultiLine(
                releases("photos-v1.3.64", "ensu-v0.1.20", "auth-v4.4.25", "photos-v1.3.63", "auth-v4.4.24"),
            ),
        )
        assertTrue(
            ReleaseLines.isMultiLine(releases("THUNDERBIRD_23_1", "K9MAIL_23_1", "THUNDERBIRD_23_0", "K9MAIL_23_0")),
        )
    }

    @Test
    fun oneAppStaysOneLine() {
        assertFalse(ReleaseLines.isMultiLine(releases("3.4.13-android", "v3.4.8", "3.4.12-android", "v3.4.7")))
        assertFalse(ReleaseLines.isMultiLine(releases("nightly", "2.0.1", "0.10.15")))
        assertFalse(ReleaseLines.isMultiLine(releases("v1.2", "v1.1", "release-1.0", "release-0.9")))
    }

    @Test
    fun sameLineKeepsOnlyTheAnchorsAppInAMonorepo() {
        val history = releases("photos-v1.3.64", "ensu-v0.1.20", "auth-v4.4.25", "photos-v1.3.63", "auth-v4.4.24")
        assertEquals(
            listOf("auth-v4.4.25", "auth-v4.4.24"),
            ReleaseLines.sameLine(history, history[2]).map { it.tagName },
        )
        val single = releases("3.4.13-android", "v3.4.8")
        assertEquals(single, ReleaseLines.sameLine(single, single[1]))
    }
}
