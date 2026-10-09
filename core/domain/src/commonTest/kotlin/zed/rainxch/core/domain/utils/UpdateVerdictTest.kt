package zed.rainxch.core.domain.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateVerdictTest {
    private fun decide(
        installedTag: String = "1.0.0",
        installedVersionCode: Long = 100L,
        installedVersionName: String? = null,
        storedLatestTag: String? = null,
        storedLatestVersionCode: Long? = null,
        storedPublishedAt: String? = null,
        wasUpdateAvailable: Boolean = false,
        skippedTag: String? = null,
        matchedTag: String = "1.1.0",
        matchedPublishedAt: String? = "2026-08-01T00:00:00Z",
        matchedIsPrerelease: Boolean = false,
        storedAssetDigest: String? = null,
        storedAssetSize: Long? = null,
        matchedAssetDigest: String? = null,
        matchedAssetSize: Long? = null,
        storedReleaseId: Long? = null,
        storedAssetId: Long? = null,
        matchedReleaseId: Long? = null,
        matchedAssetId: Long? = null,
        bound: UpdateVerdict.Bound? = null,
    ): UpdateVerdict.Result =
        UpdateVerdict.decide(
            installed = UpdateVerdict.Installed(installedTag, installedVersionCode, installedVersionName),
            stored =
                UpdateVerdict.Stored(
                    latestTag = storedLatestTag,
                    latestVersionCode = storedLatestVersionCode,
                    publishedAt = storedPublishedAt,
                    wasUpdateAvailable = wasUpdateAvailable,
                    latestReleaseId = storedReleaseId,
                    latestAssetId = storedAssetId,
                    latestAssetDigest = storedAssetDigest,
                    latestAssetSize = storedAssetSize,
                ),
            matched =
                UpdateVerdict.Matched(
                    tag = matchedTag,
                    publishedAt = matchedPublishedAt,
                    isPrerelease = matchedIsPrerelease,
                    releaseId = matchedReleaseId,
                    assetId = matchedAssetId,
                    assetDigest = matchedAssetDigest,
                    assetSize = matchedAssetSize,
                ),
            skippedTag = skippedTag,
            bound = bound,
        )

    // com.yunx.app: v1.2.8 was offered, the install was blocked for want of installer
    // authorisation, and it was installed another way. The device then reported 1.2.8 while the
    // stored tag stayed at 1.2.6, so the verdict kept comparing that stale tag and reported an
    // update the device had already taken — until the app was downloaded again through the store.
    @Test
    fun a_release_the_device_already_runs_is_not_an_update() {
        val result =
            decide(
                installedTag = "1.2.6",
                installedVersionCode = 12L,
                installedVersionName = "1.2.8",
                storedLatestTag = "v1.2.8",
                matchedTag = "v1.2.8",
                matchedPublishedAt = "2026-10-03T11:38:10Z",
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun a_release_the_device_already_runs_is_not_an_update_for_a_prerelease_too() {
        val result =
            decide(
                installedTag = "3.26.16-beta.42",
                installedVersionCode = 18503L,
                installedVersionName = "3.26.16-beta.44",
                storedLatestTag = "3.26.16-beta.44",
                matchedTag = "3.26.16-beta.44",
                matchedPublishedAt = "2026-10-02T17:31:28Z",
                matchedIsPrerelease = true,
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun a_version_the_device_does_not_report_is_still_an_update() {
        val result =
            decide(
                installedTag = "1.2.6",
                installedVersionCode = 10L,
                installedVersionName = "1.2.6",
                storedLatestTag = "v1.2.6",
                matchedTag = "v1.2.8",
                matchedPublishedAt = "2026-10-03T11:38:10Z",
            )
        assertTrue(result.isUpdateAvailable)
    }

    // The evidence has to be exact, not merely equal after normalisation: "1.2.8.0" and "1.2.8"
    // normalise alike, and treating that as proof would swallow a real update.
    @Test
    fun a_name_that_only_normalises_to_the_matched_tag_is_not_evidence() {
        val result =
            decide(
                installedTag = "1.2.7",
                installedVersionCode = 100L,
                installedVersionName = "1.2.8.0",
                storedLatestTag = "v1.2.7",
                matchedTag = "v1.2.8",
                matchedPublishedAt = "2026-10-03T11:38:10Z",
            )
        assertTrue(result.isUpdateAvailable)
    }

    // A "v" prefix is not a difference, so a device reporting "v1.2.8" is on release "v1.2.8".
    @Test
    fun a_leading_v_does_not_hide_that_the_device_runs_the_matched_release() {
        val result =
            decide(
                installedTag = "1.2.6",
                installedVersionCode = 12L,
                installedVersionName = "v1.2.8",
                storedLatestTag = "v1.2.8",
                matchedTag = "v1.2.8",
                matchedPublishedAt = "2026-10-03T11:38:10Z",
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun without_a_reported_name_the_tag_is_still_the_baseline() {
        val result =
            decide(
                installedTag = "1.2.6",
                installedVersionCode = 12L,
                installedVersionName = null,
                storedLatestTag = "v1.2.8",
                matchedTag = "v1.2.8",
                matchedPublishedAt = "2026-10-03T11:38:10Z",
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun the_stale_tag_is_adopted_when_the_device_runs_the_matched_release() {
        val result =
            decide(
                installedTag = "1.2.6",
                installedVersionCode = 12L,
                installedVersionName = "1.2.8",
                storedLatestTag = "v1.2.8",
                matchedTag = "v1.2.8",
                matchedPublishedAt = "2026-10-03T11:38:10Z",
            )
        assertTrue(result.deviceRunsMatchedRelease)
        assertTrue(
            UpdateVerdict.shouldAdoptMatchedTag(
                codesAlreadyMatch = result.codesAlreadyMatch,
                installedTag = "1.2.6",
                matchedTag = "v1.2.8",
                deviceRunsMatchedRelease = result.deviceRunsMatchedRelease,
            ),
        )
    }

    @Test
    fun a_rolling_tag_rebuild_is_an_update_even_when_the_name_matches_the_tag() {
        val result =
            decide(
                installedTag = "nightly",
                installedVersionName = "nightly",
                matchedTag = "nightly",
                storedLatestTag = "nightly",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
        assertFalse(result.deviceRunsMatchedRelease)
    }

    @Test
    fun a_rolling_tag_reupload_is_an_update_even_when_the_name_matches_the_tag() {
        val result =
            decide(
                installedTag = "nightly",
                installedVersionName = "nightly",
                matchedTag = "nightly",
                storedLatestTag = "nightly",
                storedPublishedAt = "2026-08-01T00:00:00Z",
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                storedAssetDigest = "sha256:aaa",
                matchedAssetDigest = "sha256:bbb",
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun semver_newer_reports_update() {
        val result = decide(installedTag = "1.0.0", matchedTag = "1.1.0")
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun semver_not_newer_stays_silent() {
        val result = decide(installedTag = "1.2.0", matchedTag = "1.1.0")
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun beta_build_bump_reports_update() {
        val result =
            decide(
                installedTag = "3.26.16-beta.19",
                matchedTag = "3.26.16-beta.20",
                matchedIsPrerelease = true,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun nightly_first_scan_after_install_is_quiet() {
        val result =
            decide(
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
            decide(
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
            decide(
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
            decide(
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
            decide(
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
            decide(
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
            decide(
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
            decide(
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
            decide(
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
            decide(
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
            decide(
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
            decide(
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
            decide(
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
            decide(
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
            decide(
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
            decide(
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
            decide(
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
            decide(
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
        val result = decide(skippedTag = "1.1.0", matchedTag = "1.1.0")
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun skipped_tag_becomes_stale_when_newer_release_appears() {
        val result = decide(skippedTag = "1.1.0", matchedTag = "1.2.0")
        assertTrue(result.skipBecameStale)
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun stable_vs_nightly_is_irreconcilable_and_silent() {
        val result =
            decide(
                installedTag = "nightly",
                matchedTag = "2.0.0",
                matchedIsPrerelease = false,
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun codes_already_match_requires_positive_codes() {
        val zeroInstalled =
            decide(
                installedTag = "1.0.0",
                installedVersionCode = 0L,
                storedLatestTag = "1.0.0",
                storedLatestVersionCode = 0L,
                matchedTag = "1.0.0",
            )
        assertFalse(zeroInstalled.codesAlreadyMatch)

        val zeroStored =
            decide(
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
        val matched = decide(installedTag = "1.0.0", installedVersionCode = 100L)
        assertFalse(matched.codesAlreadyMatch)

        val tagMismatch =
            decide(
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
            decide(
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
            decide(
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
            decide(
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
            decide(
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
            decide(
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

    @Test
    fun a_rebuilt_nightly_is_reported_from_its_release_identity_alone() {
        val result =
            decide(
                installedTag = "nightly",
                installedVersionCode = 500L,
                storedLatestTag = "nightly",
                storedLatestVersionCode = 500L,
                storedPublishedAt = "2026-09-24T11:46:11Z",
                wasUpdateAvailable = false,
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-24T11:46:11Z",
                matchedIsPrerelease = true,
                storedReleaseId = 800L,
                storedAssetId = 801L,
                matchedReleaseId = 900L,
                matchedAssetId = 901L,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun an_untouched_nightly_is_quiet_even_with_identities_recorded() {
        val result =
            decide(
                installedTag = "nightly",
                installedVersionCode = 500L,
                storedLatestTag = "nightly",
                storedLatestVersionCode = 500L,
                storedPublishedAt = "2026-09-24T11:46:11Z",
                wasUpdateAvailable = false,
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-24T11:46:11Z",
                matchedIsPrerelease = true,
                storedReleaseId = 900L,
                storedAssetId = 901L,
                matchedReleaseId = 900L,
                matchedAssetId = 901L,
                storedAssetDigest = "sha256:aaaa",
                matchedAssetDigest = "sha256:aaaa",
                storedAssetSize = 1024L,
                matchedAssetSize = 1024L,
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun a_nightly_whose_asset_was_swapped_is_reported_again() {
        val result =
            decide(
                installedTag = "nightly",
                installedVersionCode = 500L,
                storedLatestTag = "nightly",
                storedLatestVersionCode = 500L,
                storedPublishedAt = "2026-09-24T11:46:11Z",
                wasUpdateAvailable = false,
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-24T11:46:11Z",
                matchedIsPrerelease = true,
                storedAssetDigest = "sha256:aaaa",
                storedAssetSize = 70_543_755L,
                matchedAssetDigest = "sha256:bbbb",
                matchedAssetSize = 70_543_755L,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun a_nightly_that_was_not_rebuilt_anywhere_stays_quiet() {
        val result =
            decide(
                installedTag = "nightly",
                installedVersionCode = 500L,
                storedLatestTag = "nightly",
                storedLatestVersionCode = 500L,
                storedPublishedAt = "2026-09-24T11:46:11Z",
                wasUpdateAvailable = false,
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-24T11:46:11Z",
                matchedIsPrerelease = true,
                storedAssetDigest = "sha256:aaaa",
                storedAssetSize = 70_543_755L,
                matchedAssetDigest = "sha256:aaaa",
                matchedAssetSize = 70_543_755L,
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun a_recreated_nightly_with_identical_bytes_still_reports_on_its_timestamp() {
        val result =
            decide(
                installedTag = "nightly",
                installedVersionCode = 500L,
                storedLatestTag = "nightly",
                storedLatestVersionCode = 500L,
                storedPublishedAt = "2026-09-24T11:46:11Z",
                wasUpdateAvailable = false,
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-25T02:00:00Z",
                matchedIsPrerelease = true,
                storedAssetDigest = "sha256:aaaa",
                storedAssetSize = 70_543_755L,
                matchedAssetDigest = "sha256:aaaa",
                matchedAssetSize = 70_543_755L,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun a_later_release_with_a_different_file_is_an_update() {
        // 26.09.8 installed, 26.09.8a published afterwards as a different file. fallback=false
        // proves the report comes from the release date, not from a carried-over verdict.
        val sameFile =
            UpdateVerdict.isSameFile(
                installedAssetId = 11L,
                installedAssetDigest = "sha256:old",
                matchedAssetId = 22L,
                matchedAssetDigest = "sha256:new",
            )
        assertFalse(sameFile)

        assertTrue(
            UpdateVerdict.decideBound(
                sameFile = sameFile,
                matchedPublishedAt = "2026-09-08T12:00:00Z",
                installedReleasePublishedAt = "2026-09-08T00:00:00Z",
                fallback = false,
            ),
        )
    }

    @Test
    fun a_rebuilt_nightly_with_a_changed_file_is_an_update() {
        // Same 'nightly' tag: the identity, not the tag string, has to carry the signal.
        val sameFile =
            UpdateVerdict.isSameFile(
                installedAssetId = 100L,
                installedAssetDigest = null,
                matchedAssetId = 200L,
                matchedAssetDigest = null,
            )
        assertFalse(sameFile)

        assertTrue(
            UpdateVerdict.decideBound(
                sameFile = sameFile,
                matchedPublishedAt = "2026-09-25T02:00:00Z",
                installedReleasePublishedAt = "2026-09-24T11:46:11Z",
                fallback = false,
            ),
        )
    }

    @Test
    fun the_same_asset_id_stays_quiet_even_when_the_tag_reads_differently() {
        val sameFile =
            UpdateVerdict.isSameFile(
                installedAssetId = 42L,
                installedAssetDigest = null,
                matchedAssetId = 42L,
                matchedAssetDigest = null,
            )
        assertTrue(sameFile)

        // fallback=true: a false here can only come from the same-file short circuit.
        assertFalse(
            UpdateVerdict.decideBound(
                sameFile = sameFile,
                matchedPublishedAt = "2026-09-10T00:00:00Z",
                installedReleasePublishedAt = "2026-09-01T00:00:00Z",
                fallback = true,
            ),
        )
    }

    @Test
    fun a_matching_digest_beats_a_new_asset_id() {
        // Digests are the stronger statement: a re-upload keeps the bytes under a new id.
        val sameFile =
            UpdateVerdict.isSameFile(
                installedAssetId = 7L,
                installedAssetDigest = "sha256:same",
                matchedAssetId = 8L,
                matchedAssetDigest = "sha256:same",
            )
        assertTrue(sameFile)

        assertFalse(
            UpdateVerdict.decideBound(
                sameFile = sameFile,
                matchedPublishedAt = "2026-09-10T00:00:00Z",
                installedReleasePublishedAt = "2026-09-01T00:00:00Z",
                fallback = true,
            ),
        )
    }

    @Test
    fun a_different_file_from_an_earlier_release_is_not_an_update() {
        val sameFile =
            UpdateVerdict.isSameFile(
                installedAssetId = 5L,
                installedAssetDigest = "sha256:new",
                matchedAssetId = 6L,
                matchedAssetDigest = "sha256:old",
            )
        assertFalse(sameFile)

        // fallback=true: staying quiet can only come from the release date going forwards.
        assertFalse(
            UpdateVerdict.decideBound(
                sameFile = sameFile,
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                installedReleasePublishedAt = "2026-09-01T00:00:00Z",
                fallback = true,
            ),
        )
    }

    @Test
    fun an_installed_release_outside_the_window_falls_back_to_the_tag_verdict() {
        assertTrue(
            UpdateVerdict.decideBound(
                sameFile = false,
                matchedPublishedAt = "2026-09-10T00:00:00Z",
                installedReleasePublishedAt = null,
                fallback = true,
            ),
        )
        assertFalse(
            UpdateVerdict.decideBound(
                sameFile = false,
                matchedPublishedAt = "2026-09-10T00:00:00Z",
                installedReleasePublishedAt = null,
                fallback = false,
            ),
        )
    }

    @Test
    fun a_skipped_release_stays_skipped_for_a_bound_record() {
        val result =
            decide(
                installedTag = "1.0.0",
                matchedTag = "1.1.0",
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                matchedAssetId = 22L,
                skippedTag = "1.1.0",
                bound =
                    UpdateVerdict.Bound(
                        assetId = 11L,
                        assetDigest = null,
                        releasePublishedAt = "2026-07-01T00:00:00Z",
                    ),
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun a_bound_record_on_the_matched_file_is_up_to_date_whatever_the_tags_say() {
        val unbound = decide(installedTag = "1.0.0", matchedTag = "1.1.0", matchedAssetId = 42L)
        assertTrue(unbound.isUpdateAvailable)

        val bound =
            decide(
                installedTag = "1.0.0",
                matchedTag = "1.1.0",
                matchedAssetId = 42L,
                bound =
                    UpdateVerdict.Bound(
                        assetId = 42L,
                        assetDigest = null,
                        releasePublishedAt = "2026-07-01T00:00:00Z",
                    ),
            )
        assertFalse(bound.isUpdateAvailable)
    }

    @Test
    fun a_bound_record_reports_a_later_file_the_tags_cannot_tell_apart() {
        val unbound =
            decide(
                installedTag = "26.09.8",
                matchedTag = "26.09.8a",
                matchedPublishedAt = "2026-09-08T12:00:00Z",
                matchedAssetId = 22L,
            )
        assertFalse(unbound.isUpdateAvailable)

        val bound =
            decide(
                installedTag = "26.09.8",
                matchedTag = "26.09.8a",
                matchedPublishedAt = "2026-09-08T12:00:00Z",
                matchedAssetId = 22L,
                bound =
                    UpdateVerdict.Bound(
                        assetId = 11L,
                        assetDigest = null,
                        releasePublishedAt = "2026-09-08T00:00:00Z",
                    ),
            )
        assertTrue(bound.isUpdateAvailable)
    }

    @Test
    fun an_asset_replaced_in_place_with_identical_bytes_is_not_a_new_build() {
        val result =
            decide(
                installedTag = "nightly",
                installedVersionCode = 500L,
                storedLatestTag = "nightly",
                storedLatestVersionCode = 500L,
                storedPublishedAt = "2026-09-24T11:46:11Z",
                wasUpdateAvailable = false,
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-24T11:46:11Z",
                matchedIsPrerelease = true,
                storedAssetDigest = "sha256:aaaa",
                storedAssetSize = 70_543_755L,
                matchedAssetDigest = "sha256:aaaa",
                matchedAssetSize = 70_543_755L,
                storedReleaseId = 700L,
                matchedReleaseId = 700L,
                storedAssetId = 801L,
                matchedAssetId = 901L,
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun a_skip_survives_an_in_place_replacement_of_identical_bytes() {
        val result =
            decide(
                installedTag = "nightly",
                installedVersionCode = 500L,
                storedLatestTag = "nightly",
                storedLatestVersionCode = 500L,
                storedPublishedAt = "2026-09-24T11:46:11Z",
                wasUpdateAvailable = false,
                skippedTag = "nightly",
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-24T11:46:11Z",
                matchedIsPrerelease = true,
                storedAssetDigest = "sha256:aaaa",
                storedAssetSize = 70_543_755L,
                matchedAssetDigest = "sha256:aaaa",
                matchedAssetSize = 70_543_755L,
                storedReleaseId = 700L,
                matchedReleaseId = 700L,
                storedAssetId = 801L,
                matchedAssetId = 901L,
            )
        assertFalse(result.isUpdateAvailable)
        assertFalse(result.skipBecameStale)
    }

    @Test
    fun a_skip_is_still_released_when_the_bytes_actually_change() {
        val result =
            decide(
                installedTag = "nightly",
                installedVersionCode = 500L,
                storedLatestTag = "nightly",
                storedLatestVersionCode = 500L,
                storedPublishedAt = "2026-09-24T11:46:11Z",
                wasUpdateAvailable = false,
                skippedTag = "nightly",
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-24T11:46:11Z",
                matchedIsPrerelease = true,
                storedAssetDigest = "sha256:aaaa",
                storedAssetSize = 70_543_755L,
                matchedAssetDigest = "sha256:bbbb",
                matchedAssetSize = 70_543_755L,
                storedReleaseId = 700L,
                matchedReleaseId = 700L,
                storedAssetId = 801L,
                matchedAssetId = 801L,
            )
        assertTrue(result.isUpdateAvailable)
        assertTrue(result.skipBecameStale)
    }

    @Test
    fun a_one_sided_digest_falls_back_to_object_ids() {
        val result =
            decide(
                installedTag = "nightly",
                installedVersionCode = 500L,
                storedLatestTag = "nightly",
                storedLatestVersionCode = 500L,
                storedPublishedAt = "2026-09-24T11:46:11Z",
                wasUpdateAvailable = false,
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-24T11:46:11Z",
                matchedIsPrerelease = true,
                storedAssetDigest = null,
                storedAssetSize = 70_543_755L,
                matchedAssetDigest = "sha256:aaaa",
                matchedAssetSize = 70_543_755L,
                storedAssetId = 801L,
                matchedAssetId = 901L,
            )
        assertTrue(result.isUpdateAvailable)
    }
}
