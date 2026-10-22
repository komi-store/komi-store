package zed.rainxch.core.data.repository

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstalledReleaseBindingTest {
    private fun input(
        installedAssetId: Long? = 899L,
        installedAssetDigest: String? = null,
        installedVersion: String = "2.0.5",
        matchedTag: String = "v2.0.5",
        matchedDigest: String? = "sha256:aaaa",
        matchedPublishedAt: String? = "2026-10-20T00:00:00Z",
        installedPublishedAt: String? = "2026-10-01T00:00:00Z",
    ) = DigestConfirmationInput(
        installedAssetId = installedAssetId,
        installedAssetDigest = installedAssetDigest,
        installedVersion = installedVersion,
        matchedTag = matchedTag,
        matchedDigest = matchedDigest,
        matchedPublishedAt = matchedPublishedAt,
        installedPublishedAt = installedPublishedAt,
    )

    @Test
    fun a_legacy_binding_with_a_moved_timestamp_needs_a_hash() {
        assertTrue(shouldConfirmDigest(input()))
    }

    @Test
    fun an_unchanged_timestamp_never_pays_for_a_hash() {
        assertFalse(
            shouldConfirmDigest(input(matchedPublishedAt = "2026-10-01T00:00:00Z")),
        )
    }

    @Test
    fun a_row_that_already_carries_a_digest_needs_no_hash() {
        assertFalse(shouldConfirmDigest(input(installedAssetDigest = "sha256:aaaa")))
    }

    @Test
    fun a_different_tag_needs_no_hash() {
        assertFalse(shouldConfirmDigest(input(installedVersion = "2.0.4")))
    }

    @Test
    fun a_release_without_a_digest_has_nothing_to_compare() {
        assertFalse(shouldConfirmDigest(input(matchedDigest = null)))
    }

    @Test
    fun a_row_without_an_asset_id_has_no_binding_to_compare() {
        assertFalse(shouldConfirmDigest(input(installedAssetId = null)))
    }

    @Test
    fun a_missing_installed_timestamp_is_not_a_suspicion() {
        assertFalse(shouldConfirmDigest(input(installedPublishedAt = null)))
    }
}
