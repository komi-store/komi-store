package zed.rainxch.core.data.mappers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import zed.rainxch.core.domain.model.account.github.GithubRelease

class UpdateCheckWindowTest {

    @Test
    fun the_window_reads_newest_first() {
        val oldest = release(id = 1L, tag = "1.0.0", publishedAt = "2026-09-01T00:00:00Z")
        val newest = release(id = 3L, tag = "3.0.0", publishedAt = "2026-10-01T00:00:00Z")
        val middle = release(id = 2L, tag = "2.0.0", publishedAt = "2026-09-15T00:00:00Z")

        val window =
            listOf(oldest, newest, middle)
                .toUpdateCheckWindow(includePreReleases = false, limit = 50)

        assertEquals(listOf(newest.id, middle.id, oldest.id), window.map { it.id })
    }

    @Test
    fun pre_releases_are_dropped_unless_asked_for() {
        val stable = release(id = 1L, tag = "1.0.0", publishedAt = "2026-09-01T00:00:00Z")
        val beta =
            release(
                id = 2L,
                tag = "2.0.0-beta1",
                publishedAt = "2026-10-01T00:00:00Z",
                isPrerelease = true,
            )

        val stableOnly =
            listOf(beta, stable)
                .toUpdateCheckWindow(includePreReleases = false, limit = 50)
        val everything =
            listOf(beta, stable)
                .toUpdateCheckWindow(includePreReleases = true, limit = 50)

        assertEquals(listOf(stable.id), stableOnly.map { it.id })
        assertEquals(listOf(beta.id, stable.id), everything.map { it.id })
    }

    @Test
    fun the_limit_keeps_the_newest_entries() {
        val releases =
            (1..5).map { index ->
                release(
                    id = index.toLong(),
                    tag = "$index.0.0",
                    publishedAt = "2026-09-0${index}T00:00:00Z",
                )
            }

        val window = releases.toUpdateCheckWindow(includePreReleases = false, limit = 2)

        assertEquals(listOf(5L, 4L), window.map { it.id })
    }

    @Test
    fun the_cap_counts_raw_entries_like_the_fetch_path_not_filtered_ones() {
        // The fetch path's per_page caps the raw window server-side, before any pre-release
        // filter: a window of mostly pre-releases must not reach past the cap for the stable
        // entries the fetch-driven check can never see.
        val oldestStable = release(id = 1L, tag = "1.0.0", publishedAt = "2026-09-01T00:00:00Z")
        val newestStable = release(id = 2L, tag = "2.0.0", publishedAt = "2026-09-05T00:00:00Z")
        val preReleases =
            (10..12).map { index ->
                release(
                    id = index.toLong(),
                    tag = "$index.0.0-beta",
                    publishedAt = "2026-10-0${index - 9}T00:00:00Z",
                    isPrerelease = true,
                )
            }

        val window =
            (preReleases + oldestStable + newestStable)
                .toUpdateCheckWindow(includePreReleases = false, limit = 3)

        assertTrue(window.none { it.isPrerelease }, "pre-releases stay filtered out")
        assertEquals(emptyList(), window.map { it.id }, "the raw cap can leave nothing judgeable")
    }

    @Test
    fun an_empty_list_stays_empty() {
        assertTrue(
            emptyList<GithubRelease>()
                .toUpdateCheckWindow(includePreReleases = false, limit = 50)
                .isEmpty(),
        )
    }

    private fun release(
        id: Long,
        tag: String,
        publishedAt: String,
        isPrerelease: Boolean = false,
    ): GithubRelease =
        GithubRelease(
            id = id,
            tagName = tag,
            name = tag,
            publishedAt = publishedAt,
            description = null,
            assets = emptyList(),
            tarballUrl = "",
            zipballUrl = "",
            htmlUrl = "",
            isPrerelease = isPrerelease,
        )
}
