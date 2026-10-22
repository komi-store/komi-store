package zed.rainxch.core.domain.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VersionMathTest {
    @Test
    fun a_stored_timestamp_only_counts_while_it_parses() {
        assertEquals("2026-10-01T00:00:00Z", VersionMath.publishedAtOrNull("2026-10-01T00:00:00Z"))
        assertEquals(null, VersionMath.publishedAtOrNull("not-a-date"))
        assertEquals(null, VersionMath.publishedAtOrNull("   "))
        assertEquals(null, VersionMath.publishedAtOrNull(null))
    }

    @Test
    fun normalize_preserves_opaque_marker_tags() {
        assertEquals("nightly-a1b2c3d", VersionMath.normalizeVersion("nightly-a1b2c3d"))
        assertEquals("canary-deadbeef", VersionMath.normalizeVersion("canary-deadbeef"))
        assertEquals("nightly-abc123", VersionMath.normalizeVersion("vnightly-abc123"))
        assertEquals("nightly", VersionMath.normalizeVersion("nightly"))
        assertEquals("beta-x7z92", VersionMath.normalizeVersion("beta-x7z92"))
        assertEquals("rolling-abc123", VersionMath.normalizeVersion("rolling-abc123"))
        assertEquals("rolling", VersionMath.normalizeVersion("rolling"))
    }

    @Test
    fun normalize_extracts_digits_from_calver_nightly() {
        assertEquals("20260731", VersionMath.normalizeVersion("nightly-20260731"))
        assertEquals("20260801", VersionMath.normalizeVersion("nightly-20260801"))
        assertEquals("20260801", VersionMath.normalizeVersion("rolling-20260801"))
    }

    @Test
    fun normalize_semver_unaffected() {
        assertEquals("1.2.3", VersionMath.normalizeVersion("1.2.3"))
        assertEquals("1.2.3-beta", VersionMath.normalizeVersion("v1.2.3-beta"))
        assertEquals("2.0.9.1", VersionMath.normalizeVersion("2.0.9.1"))
    }

    @Test
    fun unparseable_hash_prereleases_are_not_reconcilable() {
        assertFalse(
            VersionMath.versionsReconcilable(
                "26.08.21fae85",
                "26.08.11f15e4",
            ),
        )
        assertFalse(
            VersionMath.versionsReconcilable(
                "26.08.ac0a687",
                "26.08.11f15e4",
            ),
        )
        assertFalse(
            VersionMath.versionsReconcilable(
                "1.2.3fabc",
                "1.2.4",
            ),
        )
    }

    @Test
    fun opaque_marker_detects_release_tag_alone() {
        assertTrue(VersionMath.isOpaqueMarker("nightly"))
        assertTrue(VersionMath.isOpaqueMarker("nightly-abc"))
        assertTrue(VersionMath.isOpaqueMarker("rolling"))
        assertFalse(VersionMath.isOpaqueMarker("1.0.0"))
        assertFalse(VersionMath.isOpaqueMarker("nightly-20260731"))
    }

    @Test
    fun marker_with_a_dotted_digit_suffix_stays_numerically_comparable() {
        assertFalse(VersionMath.isOpaqueMarker("beta-1.2.3"))
        assertFalse(VersionMath.isTimestampTrackedTag("beta-1.2.3"))
        assertFalse(VersionMath.isOpaqueMarker("rc-1.0.10"))
        assertFalse(VersionMath.isTimestampTrackedTag("rc-1.0.10"))
        assertFalse(VersionMath.isOpaqueMarker("nightly-2026.08.01"))
        assertFalse(VersionMath.isTimestampTrackedTag("nightly-2026.08.01"))
        assertTrue(VersionMath.isVersionNewer("beta-1.10.0", "beta-1.9.0"))
        assertTrue(VersionMath.isVersionNewer("rc-1.0.10", "rc-1.0.9"))
        assertFalse(VersionMath.isVersionNewer("beta-1.9.0", "beta-1.10.0"))
    }

    @Test
    fun timestamp_tracked_covers_opaque_and_hash_tails() {
        assertTrue(VersionMath.isTimestampTrackedTag("nightly"))
        assertTrue(VersionMath.isTimestampTrackedTag("rolling"))
        assertFalse(VersionMath.isOpaqueMarker("26.08.11f15e4"))
        assertTrue(VersionMath.isTimestampTrackedTag("26.08.11f15e4"))
        assertTrue(VersionMath.isTimestampTrackedTag("1.2.3fabc"))
        assertFalse(VersionMath.isTimestampTrackedTag("1.2.3"))
        assertFalse(VersionMath.isTimestampTrackedTag(null))
    }

    @Test
    fun versions_reconcilable_semver() {
        assertTrue(VersionMath.versionsReconcilable("1.2.3", "1.2.4"))
        assertTrue(VersionMath.versionsReconcilable("v1.2.3", "1.2.3"))
    }

    @Test
    fun versions_reconcilable_rejects_hash_mismatch() {
        assertFalse(VersionMath.versionsReconcilable("2.0.9.1", "2.0.9-1c19925b5"))
        assertFalse(VersionMath.versionsReconcilable("nightly-abc", "1.2.3"))
    }

    @Test
    fun calver_nightly_compares_numerically() {
        assertTrue(VersionMath.isVersionNewer("nightly-20260801", "nightly-20260731"))
        assertFalse(VersionMath.isVersionNewer("nightly-20260731", "nightly-20260801"))
    }

    @Test
    fun semver_comparison_regression() {
        assertTrue(VersionMath.isVersionNewer("1.2.4", "1.2.3"))
        assertFalse(VersionMath.isVersionNewer("1.2.3", "1.2.4"))
        assertTrue(VersionMath.isVersionNewer("2.0.0", "1.9.9"))
        assertFalse(VersionMath.isVersionNewer("1.0.0-alpha", "1.0.0"))
    }

    @Test
    fun beta_release_comparison_detects_newer_build() {
        assertTrue(VersionMath.isVersionNewer("3.26.16-beta.20", "3.26.16-beta.19"))
        assertFalse(VersionMath.isVersionNewer("3.26.16-beta.19", "3.26.16-beta.20"))
    }

    @Test
    fun nightly_is_prerelease_tag() {
        assertTrue(VersionMath.isPreReleaseTag("nightly"))
        assertTrue(VersionMath.isPreReleaseTag("nightly-abc"))
        assertTrue(VersionMath.isPreReleaseTag("nightly-20260731"))
        assertTrue(VersionMath.isPreReleaseTag("rolling"))
        assertTrue(VersionMath.isPreReleaseTag("rolling-abc"))
        assertFalse(VersionMath.isPreReleaseTag("v1.2.3"))
        assertFalse(VersionMath.isPreReleaseTag("1.2.3"))
    }

    @Test
    fun nightly_marker_label() {
        assertEquals("Nightly", VersionMath.preReleaseMarkerLabel("nightly"))
        assertEquals("Nightly", VersionMath.preReleaseMarkerLabel("nightly-abc"))
        assertEquals("Nightly", VersionMath.preReleaseMarkerLabel("v1.2.3-nightly"))
        assertEquals("Rolling", VersionMath.preReleaseMarkerLabel("rolling"))
        assertEquals("Rolling", VersionMath.preReleaseMarkerLabel("rolling-abc"))
    }

    @Test
    fun detect_scheme_for_nightly() {
        assertEquals(VersionMath.Scheme.Unknown, VersionMath.detectScheme("nightly"))
        assertEquals(VersionMath.Scheme.Unknown, VersionMath.detectScheme("nightly-abc"))
        assertEquals(VersionMath.Scheme.SemVer, VersionMath.detectScheme("v1.2.3-nightly"))
        assertEquals(VersionMath.Scheme.CalVer, VersionMath.detectScheme("2026-07-31"))
    }

    @Test
    fun timestamp_update_retained_across_scans_without_install() {
        val stillAvailable =
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly-def",
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                previousLatestPublishedAt = "2026-08-01T00:00:00Z",
                previousWasUpdateAvailable = true,
                previousLatestTag = "nightly-def",
            )
        assertTrue(stillAvailable)
    }

    @Test
    fun timestamp_update_reports_newer_release() {
        assertTrue(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly-def",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                previousLatestPublishedAt = "2026-08-01T00:00:00Z",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly-abc",
            ),
        )
    }

    @Test
    fun timestamp_update_not_reported_after_install() {
        assertFalse(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly-def",
                matchedPublishedAt = "2026-08-01T00:00:00Z",
                previousLatestPublishedAt = "2026-08-01T00:00:00Z",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly-def",
            ),
        )
    }

    @Test
    fun timestamp_update_first_scan_with_no_baseline() {
        assertTrue(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                previousLatestPublishedAt = null,
                previousWasUpdateAvailable = false,
                previousLatestTag = null,
            ),
        )
    }

    @Test
    fun published_at_parses_z_and_numeric_offsets_to_the_same_instant() {
        val utc = VersionMath.parsePublishedAtToInstant("2026-09-17T15:27:32Z")
        val plusTwo = VersionMath.parsePublishedAtToInstant("2026-09-17T17:27:32+02:00")
        val minusSeven = VersionMath.parsePublishedAtToInstant("2026-09-17T08:27:32-07:00")

        assertEquals(1789658852000L, utc?.toEpochMilliseconds())
        assertEquals(utc, plusTwo)
        assertEquals(utc, minusSeven)
    }

    @Test
    fun published_at_parse_failure_degrades_to_null() {
        assertEquals(null, VersionMath.parsePublishedAtToInstant(null))
        assertEquals(null, VersionMath.parsePublishedAtToInstant(""))
        assertEquals(null, VersionMath.parsePublishedAtToInstant("   "))
        assertEquals(null, VersionMath.parsePublishedAtToInstant("2026-09-17T15:27:32"))
        assertEquals(null, VersionMath.parsePublishedAtToInstant("refs/tags/nightly"))
        assertEquals(null, VersionMath.parsePublishedAtToInstant("not-a-time"))
    }

    @Test
    fun is_published_at_after_orders_absolute_time_not_lexicographic() {
        assertFalse(
            VersionMath.isPublishedAtAfter(
                "2026-09-17T17:27:32+02:00",
                "2026-09-17T15:27:32Z",
            ),
        )
        assertFalse(
            VersionMath.isPublishedAtAfter(
                "2026-09-17T17:27:32+02:00",
                "2026-09-17T16:00:00Z",
            ),
        )
        assertTrue(
            VersionMath.isPublishedAtAfter(
                "2026-09-17T18:27:32+02:00",
                "2026-09-17T15:27:32Z",
            ),
        )
        assertFalse(VersionMath.isPublishedAtAfter("not-a-time", "2026-09-17T15:27:32Z"))
        assertFalse(VersionMath.isPublishedAtAfter("2026-09-17T15:27:32Z", "not-a-time"))
    }

    @Test
    fun timestamp_update_treats_equal_instants_across_offset_forms_as_not_newer() {
        assertFalse(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-17T17:27:32+02:00",
                previousLatestPublishedAt = "2026-09-17T15:27:32Z",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly",
            ),
        )
    }

    @Test
    fun timestamp_update_reports_offset_form_that_is_truly_newer() {
        assertTrue(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-17T18:27:32+02:00",
                previousLatestPublishedAt = "2026-09-17T15:27:32Z",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly",
            ),
        )
    }

    @Test
    fun timestamp_update_degrades_to_silent_when_either_side_is_unparseable() {
        assertFalse(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "not-a-time",
                previousLatestPublishedAt = "2026-08-01T00:00:00Z",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly",
            ),
        )
        assertFalse(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "2026-08-02T00:00:00Z",
                previousLatestPublishedAt = "not-a-time",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly",
            ),
        )
    }

    @Test
    fun release_identity_sees_a_rebuild_that_the_timestamp_cannot() {
        assertTrue(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-24T11:46:11Z",
                previousLatestPublishedAt = "2026-09-24T11:46:11Z",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly",
                matchedReleaseId = 900L,
                matchedAssetId = 901L,
                previousReleaseId = 800L,
                previousAssetId = 801L,
            ),
        )
    }

    @Test
    fun release_identity_catches_an_asset_replaced_without_any_digest() {
        assertTrue(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = null,
                previousLatestPublishedAt = null,
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly",
                matchedReleaseId = 900L,
                matchedAssetId = 999L,
                previousReleaseId = 900L,
                previousAssetId = 901L,
            ),
        )
    }

    @Test
    fun release_identity_stays_quiet_when_nothing_was_replaced() {
        assertFalse(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-24T11:46:11Z",
                previousLatestPublishedAt = "2026-09-24T11:46:11Z",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly",
                matchedReleaseId = 900L,
                matchedAssetId = 901L,
                previousReleaseId = 900L,
                previousAssetId = 901L,
                matchedAssetDigest = "sha256:aaaa",
                matchedAssetSize = 1024L,
                previousAssetDigest = "sha256:aaaa",
                previousAssetSize = 1024L,
            ),
        )
    }

    @Test
    fun release_identity_says_nothing_without_both_sides() {
        assertTrue(
            VersionMath.releaseObjectChanged(
                matchedReleaseId = 900L,
                matchedAssetId = null,
                storedReleaseId = 800L,
                storedAssetId = null,
            ),
        )
        assertTrue(
            VersionMath.releaseObjectChanged(
                matchedReleaseId = null,
                matchedAssetId = 999L,
                storedReleaseId = null,
                storedAssetId = 901L,
            ),
        )
        assertFalse(
            VersionMath.releaseObjectChanged(
                matchedReleaseId = 900L,
                matchedAssetId = 901L,
                storedReleaseId = null,
                storedAssetId = null,
            ),
        )
        assertFalse(
            VersionMath.releaseObjectChanged(
                matchedReleaseId = null,
                matchedAssetId = null,
                storedReleaseId = 800L,
                storedAssetId = 801L,
            ),
        )
    }

    @Test
    fun asset_identity_reports_a_swapped_asset_at_the_same_publish_time() {
        assertTrue(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-24T11:46:11Z",
                previousLatestPublishedAt = "2026-09-24T11:46:11Z",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly",
                matchedAssetDigest = "sha256:bbbb",
                matchedAssetSize = 70_543_755L,
                previousAssetDigest = "sha256:aaaa",
                previousAssetSize = 70_543_755L,
            ),
        )
    }

    @Test
    fun asset_identity_stays_quiet_when_the_asset_is_the_same_one() {
        assertFalse(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-24T11:46:11Z",
                previousLatestPublishedAt = "2026-09-24T11:46:11Z",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly",
                matchedAssetDigest = "sha256:aaaa",
                matchedAssetSize = 70_543_755L,
                previousAssetDigest = "sha256:aaaa",
                previousAssetSize = 70_543_755L,
            ),
        )
    }

    @Test
    fun asset_identity_falls_back_to_size_and_says_nothing_without_evidence() {
        assertTrue(
            VersionMath.assetIdentityChanged(
                matchedDigest = null,
                matchedSize = 2_000L,
                storedDigest = null,
                storedSize = 1_000L,
            ),
        )
        assertTrue(
            VersionMath.assetIdentityChanged(
                matchedDigest = "sha256:bbbb",
                matchedSize = 1_000L,
                storedDigest = "sha256:aaaa",
                storedSize = 1_000L,
            ),
        )
        assertFalse(
            VersionMath.assetIdentityChanged(
                matchedDigest = null,
                matchedSize = null,
                storedDigest = null,
                storedSize = null,
            ),
        )
        assertFalse(
            VersionMath.assetIdentityChanged(
                matchedDigest = "sha256:aaaa",
                matchedSize = null,
                storedDigest = null,
                storedSize = null,
            ),
        )
    }

    @Test
    fun a_blank_stored_baseline_reads_as_no_baseline() {
        assertTrue(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "2026-09-20T00:00:00Z",
                previousLatestPublishedAt = "",
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly",
            ),
        )
        assertTrue(
            VersionMath.shouldReportTimestampUpdate(
                matchedTag = "nightly",
                matchedPublishedAt = "",
                previousLatestPublishedAt = null,
                previousWasUpdateAvailable = false,
                previousLatestTag = "nightly",
            ),
        )
    }
}
