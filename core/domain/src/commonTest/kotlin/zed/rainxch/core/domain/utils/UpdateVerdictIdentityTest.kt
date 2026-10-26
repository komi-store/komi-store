package zed.rainxch.core.domain.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateVerdictIdentityTest {
    @Test
    fun a_rebuilt_nightly_is_reported_from_its_release_identity_alone() {
        val result =
            decideVerdict(
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
            decideVerdict(
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
            decideVerdict(
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
            decideVerdict(
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
            decideVerdict(
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
        // 26.09.8 installed, 26.09.8a published later as another file; fallback=false proves the release date decides.
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
            decideVerdict(
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
        val unbound = decideVerdict(installedTag = "1.0.0", matchedTag = "1.1.0", matchedAssetId = 42L)
        assertTrue(unbound.isUpdateAvailable)

        val bound =
            decideVerdict(
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
            decideVerdict(
                installedTag = "26.09.8",
                matchedTag = "26.09.8a",
                matchedPublishedAt = "2026-09-08T12:00:00Z",
                matchedAssetId = 22L,
            )
        assertFalse(unbound.isUpdateAvailable)

        val bound =
            decideVerdict(
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
            decideVerdict(
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

    // The gate leans on shapes: a non-version tag leaves version math nothing, a semantic version keeps bytes out.
    @Test
    fun attested_gate_follows_the_tag_scheme() {
        assertEquals(
            VersionMath.Scheme.Unknown,
            VersionMath.detectScheme("testbuild-all-prs-20261007"),
        )
        assertEquals(VersionMath.Scheme.SemVer, VersionMath.detectScheme("1.0.0"))
    }

    // A rolling tag can't be reconciled by version math, so without a binding only the installed file shows a rebuild.
    @Test
    fun attested_bytes_report_a_rebuilt_rolling_tag() {
        val result =
            decideVerdict(
                installedTag = "testbuild-all-prs-20261007",
                matchedTag = "testbuild-all-prs-20261007",
                matchedAssetDigest = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                selfAttestedDigest = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun attested_same_bytes_keep_the_rebuild_quiet_even_when_the_window_moved() {
        // The stored side alone would report, since publishedAt moved on after the last check.
        val result =
            decideVerdict(
                installedTag = "testbuild-all-prs-20261007",
                matchedTag = "testbuild-all-prs-20261007",
                matchedPublishedAt = "2026-10-08T00:00:00Z",
                storedPublishedAt = "2026-10-07T00:00:00Z",
                wasUpdateAvailable = true,
                storedLatestTag = "testbuild-all-prs-20261007",
                storedAssetDigest = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                matchedAssetDigest = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                selfAttestedDigest = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun a_skip_survives_an_in_place_replacement_of_identical_bytes() {
        val result =
            decideVerdict(
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
            decideVerdict(
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
            decideVerdict(
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

    @Test
    fun attested_evidence_stays_out_of_reconcilable_tags() {
        // Same semver string: version math already speaks, the bytes are not consulted.
        val result =
            decideVerdict(
                installedTag = "1.0.0",
                matchedTag = "1.0.0",
                matchedAssetDigest = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                selfAttestedDigest = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun a_skipped_tag_outranks_attested_difference() {
        val result =
            decideVerdict(
                installedTag = "testbuild-all-prs-20261007",
                matchedTag = "testbuild-all-prs-20261007",
                matchedAssetDigest = "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                selfAttestedDigest = "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                skippedTag = "testbuild-all-prs-20261007",
            )
        assertFalse(result.isUpdateAvailable)
    }
}
