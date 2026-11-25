package zed.rainxch.core.domain.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateVerdictTimestampTest {
    @Test
    fun beta_build_bump_reports_update() {
        val result =
            decideVerdict(
                installedTag = "3.26.16-beta.19",
                matchedTag = "3.26.16-beta.20",
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun nightly_first_scan_after_install_is_quiet() {
        val result =
            decideVerdict(
                installedTag = "nightly",
                matchedTag = "nightly",
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                storedPublishedAt = null,
                matchedIsPrerelease = true,
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun first_scan_still_reports_a_different_build() {
        val result =
            decideVerdict(
                installedTag = "26.09.01",
                matchedTag = "nightly",
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                storedPublishedAt = null,
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun nightly_newer_published_at_reports_update() {
        val result =
            decideVerdict(
                installedTag = "nightly",
                matchedTag = "nightly",
                storedLatestTag = "nightly",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun nightly_same_timestamp_retains_update_until_installed() {
        val result =
            decideVerdict(
                installedTag = "nightly",
                matchedTag = "nightly",
                storedLatestTag = "nightly",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                wasUpdateAvailable = true,
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun nightly_same_timestamp_no_baseline_change_stays_silent() {
        val result =
            decideVerdict(
                installedTag = "nightly",
                matchedTag = "nightly",
                storedLatestTag = "nightly",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                wasUpdateAvailable = false,
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun nightly_null_matched_timestamp_is_silent() {
        val result =
            decideVerdict(
                installedTag = "nightly",
                matchedTag = "nightly",
                matchedPublishedAt = null,
                storedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun nightly_empty_matched_timestamp_counts_as_present() {
        val result =
            decideVerdict(
                installedTag = "26.09.01",
                matchedTag = "nightly",
                matchedPublishedAt = "",
                storedPublishedAt = null,
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun installerx_unparseable_hash_prerelease_routes_to_timestamp() {
        val result =
            decideVerdict(
                installedTag = "26.08.21fae85",
                matchedTag = "26.08.11f15e4",
                matchedPublishedAt = "2026-08-26T07:15:10Z",
                storedPublishedAt = null,
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun installerx_older_hash_still_detected_when_baseline_advances() {
        val result =
            decideVerdict(
                installedTag = "26.08.21fae85",
                installedVersionCode = 1509L,
                matchedTag = "26.08.11f15e4",
                matchedPublishedAt = "2026-08-26T07:15:10Z",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun installerx_hash_tail_routes_to_timestamp_without_the_prerelease_flag() {
        val result =
            decideVerdict(
                installedTag = "26.08.21fae85",
                matchedTag = "26.08.11f15e4",
                matchedPublishedAt = "2026-08-26T07:15:10Z",
                storedPublishedAt = null,
                matchedIsPrerelease = false,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun installerx_reused_hash_tail_without_prerelease_flag_uses_timestamp() {
        val result =
            decideVerdict(
                installedTag = "26.08.11f15e4",
                matchedTag = "26.08.11f15e4",
                matchedPublishedAt = "2026-08-26T07:15:10Z",
                storedPublishedAt = "2026-08-20T00:00:00Z",
                matchedIsPrerelease = false,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun installerx_matching_codes_and_tag_make_the_rewrite_gate_open() {
        val result =
            decideVerdict(
                installedTag = "26.08.21fae85",
                installedVersionCode = 100L,
                storedLatestTag = "26.08.11f15e4",
                storedLatestVersionCode = 100L,
                matchedTag = "26.08.11f15e4",
                matchedPublishedAt = "2026-08-26T07:15:10Z",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = false,
            )
        assertTrue(result.codesAlreadyMatch)
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun codes_match_short_circuits_to_silent() {
        val result =
            decideVerdict(
                installedTag = "1.0.0",
                installedVersionCode = 100L,
                storedLatestTag = "1.0.0",
                storedLatestVersionCode = 100L,
                matchedTag = "1.0.0",
            )
        assertFalse(result.isUpdateAvailable)
        assertTrue(result.codesAlreadyMatch)
    }

    @Test
    fun skipped_nightly_is_offered_again_once_the_tag_is_rebuilt() {
        val result =
            decideVerdict(
                installedTag = "nightly",
                matchedTag = "nightly",
                skippedTag = "nightly",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.skipBecameStale)
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun skipped_nightly_is_released_when_the_asset_is_replaced_in_place() {
        val result =
            decideVerdict(
                installedTag = "nightly",
                matchedTag = "nightly",
                skippedTag = "nightly",
                matchedPublishedAt = "2026-09-24T11:46:11Z",
                storedPublishedAt = "2026-09-24T11:46:11Z",
                matchedIsPrerelease = true,
                storedAssetId = 801L,
                matchedAssetId = 901L,
            )
        assertTrue(result.skipBecameStale)
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun skipped_nightly_stays_skipped_while_the_build_is_unchanged() {
        val result =
            decideVerdict(
                installedTag = "nightly",
                matchedTag = "nightly",
                skippedTag = "nightly",
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertFalse(result.skipBecameStale)
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun skipped_nightly_survives_a_missing_timestamp_baseline() {
        val result =
            decideVerdict(
                installedTag = "nightly",
                matchedTag = "nightly",
                skippedTag = "nightly",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                storedPublishedAt = null,
                matchedIsPrerelease = true,
            )
        assertFalse(result.skipBecameStale)
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun skipped_plain_tag_outlives_a_republished_timestamp() {
        val result =
            decideVerdict(
                installedTag = "1.0.0",
                matchedTag = "1.1.0",
                skippedTag = "1.1.0",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                storedPublishedAt = "2026-08-01T00:00:00Z",
            )
        assertFalse(result.skipBecameStale)
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun skipped_tag_is_silent() {
        val result = decideVerdict(skippedTag = "1.1.0", matchedTag = "1.1.0")
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun skipped_tag_becomes_stale_when_newer_release_appears() {
        val result = decideVerdict(skippedTag = "1.1.0", matchedTag = "1.2.0")
        assertTrue(result.skipBecameStale)
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun stable_vs_nightly_is_irreconcilable_and_silent() {
        val result =
            decideVerdict(
                installedTag = "nightly",
                matchedTag = "2.0.0",
                matchedIsPrerelease = false,
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun codes_already_match_requires_positive_codes() {
        val zeroInstalled =
            decideVerdict(
                installedTag = "1.0.0",
                installedVersionCode = 0L,
                storedLatestTag = "1.0.0",
                storedLatestVersionCode = 0L,
                matchedTag = "1.0.0",
            )
        assertFalse(zeroInstalled.codesAlreadyMatch)

        val zeroStored =
            decideVerdict(
                installedTag = "1.0.0",
                installedVersionCode = 100L,
                storedLatestTag = "1.0.0",
                storedLatestVersionCode = 0L,
                matchedTag = "1.0.0",
            )
        assertFalse(zeroStored.codesAlreadyMatch)
    }

    @Test
    fun rewrite_gate_rejects_when_codes_or_stored_tag_differ() {
        val matched = decideVerdict(installedTag = "1.0.0", installedVersionCode = 100L)
        assertFalse(matched.codesAlreadyMatch)

        val tagMismatch =
            decideVerdict(
                installedTag = "1.0.0",
                installedVersionCode = 100L,
                storedLatestTag = "1.0.1",
                storedLatestVersionCode = 100L,
                matchedTag = "1.0.0",
            )
        assertFalse(tagMismatch.codesAlreadyMatch)
    }

    @Test
    fun skipped_nightly_same_instant_in_offset_form_is_not_a_rebuild() {
        val result =
            decideVerdict(
                installedTag = "nightly",
                matchedTag = "nightly",
                skippedTag = "nightly",
                matchedPublishedAt = "2026-09-17T17:27:32+02:00",
                storedPublishedAt = "2026-09-17T15:27:32Z",
                matchedIsPrerelease = true,
            )
        assertFalse(result.skipBecameStale)
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun skipped_nightly_genuinely_later_offset_timestamp_releases_the_skip() {
        val result =
            decideVerdict(
                installedTag = "nightly",
                matchedTag = "nightly",
                skippedTag = "nightly",
                matchedPublishedAt = "2026-09-17T18:27:32+02:00",
                storedPublishedAt = "2026-09-17T15:27:32Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.skipBecameStale)
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun skipped_nightly_is_released_even_when_the_stored_code_already_matches_installed() {
        val result =
            decideVerdict(
                installedTag = "nightly",
                installedVersionCode = 500L,
                storedLatestTag = "nightly",
                storedLatestVersionCode = 500L,
                storedPublishedAt = "2026-08-01T00:00:00Z",
                skippedTag = "nightly",
                matchedTag = "nightly",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.codesAlreadyMatch)
        assertTrue(result.skipBecameStale)
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun skipped_nightly_stays_skipped_when_matching_codes_but_the_publish_time_did_not_move() {
        val result =
            decideVerdict(
                installedTag = "nightly",
                installedVersionCode = 500L,
                storedLatestTag = "nightly",
                storedLatestVersionCode = 500L,
                storedPublishedAt = "2026-08-01T00:00:00Z",
                skippedTag = "nightly",
                matchedTag = "nightly",
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.codesAlreadyMatch)
        assertFalse(result.skipBecameStale)
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun adopt_gate_needs_code_proof() {
        assertFalse(UpdateVerdict.shouldAdoptMatchedTag(false, "26.09.01", "nightly"))
        assertFalse(UpdateVerdict.shouldAdoptMatchedTag(false, "2.0.2", "nightly"))
        assertTrue(UpdateVerdict.shouldAdoptMatchedTag(true, "26.09.01", "nightly"))
    }

    @Test
    fun stable_install_keeps_its_tag_when_a_newer_nightly_is_matched() {
        val result =
            decideVerdict(
                installedTag = "2.0.2",
                installedVersionCode = 202L,
                storedLatestTag = "2.0.2",
                storedLatestVersionCode = 202L,
                storedPublishedAt = "2026-08-01T00:00:00Z",
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-01T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
        assertFalse(UpdateVerdict.shouldAdoptMatchedTag(result.codesAlreadyMatch, "2.0.2", "nightly"))
    }

    @Test
    fun adopt_gate_keeps_its_other_bounds() {
        assertFalse(UpdateVerdict.shouldAdoptMatchedTag(true, "2.0.0", "2.0.0"))
        assertFalse(UpdateVerdict.shouldAdoptMatchedTag(false, "1.0.0", "2.0.0"))
        assertTrue(UpdateVerdict.shouldAdoptMatchedTag(true, "1.0.0", "2.0.0"))
    }
}
