package zed.rainxch.core.data.repository

import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.installation.InstallSource
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

    @Test
    fun a_stored_digest_that_names_other_bytes_contradicts_the_release() {
        assertTrue(
            identityContradicts(
                entity(installedAssetDigest = "sha256:bbbb"),
                asset(digest = "sha256:aaaa"),
            ),
        )
    }

    @Test
    fun a_matching_digest_does_not_contradict_the_release() {
        assertFalse(
            identityContradicts(
                entity(installedAssetDigest = "sha256:aaaa"),
                asset(digest = "sha256:aaaa"),
            ),
        )
    }

    @Test
    fun a_legacy_row_naming_another_asset_contradicts_the_release() {
        assertTrue(identityContradicts(entity(installedAssetId = 700L), asset(id = 899L)))
    }

    @Test
    fun a_row_without_any_identity_contradicts_nothing() {
        assertFalse(identityContradicts(entity(installedAssetId = null), asset()))
    }

    private fun entity(
        installedAssetId: Long? = 899L,
        installedAssetDigest: String? = null,
    ): InstalledAppEntity =
        InstalledAppEntity(
            packageName = "com.example.app",
            repoId = 1L,
            repoName = "repo",
            repoOwner = "owner",
            repoOwnerAvatarUrl = "",
            repoDescription = null,
            primaryLanguage = null,
            repoUrl = "https://github.com/owner/repo",
            installedVersion = "2.0.5",
            installedAssetName = "app.apk",
            installedAssetUrl = "https://dl/app.apk",
            latestVersion = null,
            latestAssetName = null,
            latestAssetUrl = null,
            latestAssetSize = null,
            appName = "App",
            installSource = InstallSource.THIS_APP,
            signingFingerprint = null,
            installedAt = 1L,
            lastCheckedAt = 1L,
            lastUpdatedAt = 1L,
            isUpdateAvailable = false,
            systemArchitecture = "X64",
            fileExtension = "apk",
            installedAssetId = installedAssetId,
            installedAssetDigest = installedAssetDigest,
        )

    private fun asset(
        id: Long = 899L,
        digest: String? = "sha256:aaaa",
    ): GithubAsset =
        GithubAsset(
            id = id,
            name = "app.apk",
            contentType = "application/vnd.android.package-archive",
            size = 1024L,
            downloadUrl = "https://dl/app.apk",
            digest = digest,
        )
}
