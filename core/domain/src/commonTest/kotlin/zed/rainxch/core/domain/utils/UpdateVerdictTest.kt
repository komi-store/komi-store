package zed.rainxch.core.domain.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateVerdictTest {
    // A re-uploaded asset gets a new id but keeps its bytes: a confirmed digest equal to the release's means no update.
    @Test
    fun a_confirmed_matching_digest_makes_a_republished_tag_a_non_update() {
        val digest = "sha256:aaaa"
        val result =
            decideVerdict(
                installedTag = "2.0.5",
                installedVersionName = "2.0.5",
                matchedTag = "v2.0.5",
                matchedPublishedAt = "2026-10-20T00:00:00Z",
                matchedAssetId = 900L,
                matchedAssetDigest = digest,
                bound =
                    UpdateVerdict.Bound(
                        assetId = 899L,
                        assetDigest = digest,
                        releasePublishedAt = "2026-10-01T00:00:00Z",
                    ),
            )

        assertFalse(result.isUpdateAvailable)
    }

    // A device reports versionName 2.0.5 for tag v2.0.5; that name match once short-circuited the re-release check.
    @Test
    fun a_republished_stable_tag_the_device_reports_by_name_is_still_an_update() {
        val result =
            decideVerdict(
                installedTag = "2.0.5",
                installedVersionName = "2.0.5",
                matchedTag = "v2.0.5",
                matchedPublishedAt = "2026-10-20T00:00:00Z",
                matchedAssetId = 900L,
                bound =
                    UpdateVerdict.Bound(
                        assetId = 899L,
                        assetDigest = null,
                        releasePublishedAt = "2026-10-01T00:00:00Z",
                    ),
            )

        assertTrue(result.isUpdateAvailable)
    }

    // Version comparison can't see a re-released stable tag; new bytes plus a later publish time are the rebuild signal.
    @Test
    fun a_republished_stable_tag_with_new_bytes_and_a_later_time_is_an_update() {
        val result =
            decideVerdict(
                installedTag = "2.0.5",
                installedVersionCode = 205L,
                matchedTag = "2.0.5",
                matchedPublishedAt = "2026-10-20T00:00:00Z",
                matchedAssetId = 900L,
                bound =
                    UpdateVerdict.Bound(
                        assetId = 899L,
                        assetDigest = null,
                        releasePublishedAt = "2026-10-01T00:00:00Z",
                    ),
            )

        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun a_republished_stable_tag_with_the_same_bytes_is_not_an_update() {
        val result =
            decideVerdict(
                installedTag = "2.0.5",
                matchedTag = "2.0.5",
                matchedPublishedAt = "2026-10-20T00:00:00Z",
                matchedAssetId = 899L,
                bound =
                    UpdateVerdict.Bound(
                        assetId = 899L,
                        assetDigest = null,
                        releasePublishedAt = "2026-10-01T00:00:00Z",
                    ),
            )

        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun a_republished_stable_tag_with_an_older_time_is_not_an_update() {
        val result =
            decideVerdict(
                installedTag = "2.0.5",
                matchedTag = "2.0.5",
                matchedPublishedAt = "2026-09-01T00:00:00Z",
                matchedAssetId = 900L,
                bound =
                    UpdateVerdict.Bound(
                        assetId = 899L,
                        assetDigest = null,
                        releasePublishedAt = "2026-10-01T00:00:00Z",
                    ),
            )

        assertFalse(result.isUpdateAvailable)
    }

    // A pre-release built after the compared stable: on a stable channel the tag decides, or a newer stable stays hidden.
    @Test
    fun a_newer_stable_tag_wins_over_a_bound_prerelease_timestamp() {
        val result =
            decideVerdict(
                installedTag = "2.0.4",
                installedVersionCode = 204L,
                storedLatestTag = "2.0.5",
                storedPublishedAt = "2026-09-01T00:00:00Z",
                matchedTag = "2.0.5",
                matchedPublishedAt = "2026-09-01T00:00:00Z",
                bound =
                    UpdateVerdict.Bound(
                        assetId = 11L,
                        assetDigest = "pre-release-digest",
                        releasePublishedAt = "2026-10-01T00:00:00Z",
                    ),
            )

        assertTrue(result.isUpdateAvailable)
    }

    // A nightly install can't be ordered against a versioned tag, so its binding decides: a later stable is an update.
    @Test
    fun a_bound_nightly_still_sees_a_later_stable_release() {
        val result =
            decideVerdict(
                installedTag = "nightly",
                matchedTag = "v2.0.5",
                matchedPublishedAt = "2026-09-20T00:00:00Z",
                matchedAssetId = 22L,
                bound =
                    UpdateVerdict.Bound(
                        assetId = 11L,
                        assetDigest = null,
                        releasePublishedAt = "2026-09-08T00:00:00Z",
                    ),
            )

        assertTrue(result.isUpdateAvailable)
    }

    // com.yunx.app got v1.2.8 outside the store while the stored tag stayed 1.2.6, and the stale tag kept reporting it.
    @Test
    fun a_release_the_device_already_runs_is_not_an_update() {
        val result =
            decideVerdict(
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
            decideVerdict(
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
            decideVerdict(
                installedTag = "1.2.6",
                installedVersionCode = 10L,
                installedVersionName = "1.2.6",
                storedLatestTag = "v1.2.6",
                matchedTag = "v1.2.8",
                matchedPublishedAt = "2026-10-03T11:38:10Z",
            )
        assertTrue(result.isUpdateAvailable)
    }

    // Evidence must be exact: 1.2.8.0 and 1.2.8 normalise alike, and taking that as proof would swallow a real update.
    @Test
    fun a_name_that_only_normalises_to_the_matched_tag_is_not_evidence() {
        val result =
            decideVerdict(
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
            decideVerdict(
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
            decideVerdict(
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
            decideVerdict(
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
            decideVerdict(
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
            decideVerdict(
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
        val result = decideVerdict(installedTag = "1.0.0", matchedTag = "1.1.0")
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun semver_not_newer_stays_silent() {
        val result = decideVerdict(installedTag = "1.2.0", matchedTag = "1.1.0")
        assertFalse(result.isUpdateAvailable)
    }
}
