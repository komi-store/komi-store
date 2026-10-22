package zed.rainxch.details.domain.util

import zed.rainxch.core.domain.model.account.github.GithubRelease
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionHelperDowngradeTest {
    private var nextReleaseId = 0L

    private fun release(
        tag: String,
        publishedAt: String,
    ): GithubRelease =
        GithubRelease(
            id = nextReleaseId++,
            tagName = tag,
            name = tag,
            publishedAt = publishedAt,
            description = null,
            assets = emptyList(),
            tarballUrl = "",
            zipballUrl = "",
            htmlUrl = "",
        )

    private val releasesNewestFirst =
        listOf(
            release("2.0.2", "2026-09-13T13:12:13Z"),
            release("2.0.1", "2026-08-24T03:05:31Z"),
            release("v2.0.0", "2026-08-04T08:17:55Z"),
            release("Alpha4.7.4", "2025-04-10T14:04:48Z"),
        )

    @Test
    fun stable_is_not_a_downgrade_when_the_nightly_release_is_gone() {
        assertFalse(
            VersionHelper.isDowngradeVersion(
                candidate = "2.0.2",
                current = "nightly",
                allReleases = releasesNewestFirst,
            ),
        )
    }

    @Test
    fun installing_a_nightly_over_a_stable_is_not_a_downgrade() {
        assertFalse(
            VersionHelper.isDowngradeVersion(
                candidate = "nightly",
                current = "2.0.2",
                allReleases = releasesNewestFirst,
            ),
        )
    }

    @Test
    fun a_live_rolling_tag_still_proves_the_stable_release_older() {
        val withRolling =
            listOf(
                release("nightly", "2026-09-15T02:00:00Z"),
                release("2.0.2", "2026-09-13T13:12:13Z"),
                release("2.0.1", "2026-08-24T03:05:31Z"),
            )
        assertTrue(
            VersionHelper.isDowngradeVersion(
                candidate = "2.0.2",
                current = "nightly",
                allReleases = withRolling,
            ),
        )
    }

    @Test
    fun picking_an_older_legacy_alpha_is_still_a_downgrade() {
        assertTrue(
            VersionHelper.isDowngradeVersion(
                candidate = "Alpha4.7.4",
                current = "2.0.2",
                allReleases = releasesNewestFirst,
            ),
        )
    }

    @Test
    fun upgrading_from_legacy_alpha_to_the_newest_stable_is_allowed() {
        assertFalse(
            VersionHelper.isDowngradeVersion(
                candidate = "2.0.2",
                current = "Alpha4.7.4",
                allReleases = releasesNewestFirst,
            ),
        )
    }

    @Test
    fun a_plain_semver_downgrade_still_blocks() {
        assertTrue(
            VersionHelper.isDowngradeVersion(
                candidate = "1.9.4",
                current = "2.0.2",
                allReleases = releasesNewestFirst,
            ),
        )
    }

    @Test
    fun unknown_tags_absent_from_the_list_do_not_block() {
        assertFalse(
            VersionHelper.isDowngradeVersion(
                candidate = "dev-5",
                current = "R2024-01",
                allReleases = releasesNewestFirst,
            ),
        )
    }
}
