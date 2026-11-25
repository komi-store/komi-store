package zed.rainxch.core.domain.model.installation

import zed.rainxch.core.domain.utils.UpdateVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfirmInstallTest {
    @Test
    fun a_confirmed_install_records_the_release_timestamp() {
        val result =
            testUpdatableApp().confirmInstall(
                build =
                    InstalledBuild(
                        tag = "2.0.0",
                        releaseId = 7L,
                        assetId = 8L,
                        assetDigest = "sha256:abc",
                        releasePublishedAt = "2026-10-01T00:00:00Z",
                        assetName = "app-2.0.0.apk",
                        assetUrl = "https://dl/app-2.0.0.apk",
                        versionName = "2.0.0",
                        versionCode = 200L,
                        signingFingerprint = "SHA",
                    ),
                at = 3_000L,
            )

        assertEquals("2026-10-01T00:00:00Z", result.installedReleasePublishedAt)
    }

    @Test
    fun confirmInstallWritesInstallZone() {
        val result =
            testUpdatableApp().confirmInstall(
                build =
                    InstalledBuild(
                        tag = "2.0.0",
                        assetName = "app-2.0.0.apk",
                        assetUrl = "https://dl/app-2.0.0.apk",
                        versionName = "2.0.0",
                        versionCode = 200L,
                        signingFingerprint = "SHA-NEW",
                    ),
                at = 9999L,
            )

        assertEquals("2.0.0", result.installedVersion)
        assertEquals("app-2.0.0.apk", result.installedAssetName)
        assertEquals("https://dl/app-2.0.0.apk", result.installedAssetUrl)
        assertEquals("2.0.0", result.installedVersionName)
        assertEquals(200L, result.installedVersionCode)
        assertEquals("SHA-NEW", result.signingFingerprint)
        assertEquals(9999L, result.lastUpdatedAt)
        assertEquals(9999L, result.lastCheckedAt)
        assertFalse(result.isPendingInstall)
    }

    @Test
    fun confirmInstallKeepsTheStoredSignerWhenNoneIsReported() {
        val result =
            testUpdatableApp().confirmInstall(
                build =
                    InstalledBuild(
                        tag = "2.0.0",
                        assetName = "a",
                        assetUrl = "u",
                        versionName = "2.0.0",
                        versionCode = 200L,
                        signingFingerprint = null,
                    ),
                at = 1L,
            )

        assertEquals("SHA", result.signingFingerprint)
    }

    @Test
    fun confirmInstallClearsUpdateFlagWhenLatestMatchesInstalled() {
        val result =
            testUpdatableApp().confirmInstall(
                build =
                    InstalledBuild(
                        tag = "2.0.0",
                        assetName = "a",
                        assetUrl = "u",
                        versionName = "2.0.0",
                        versionCode = 200L,
                        signingFingerprint = null,
                    ),
                at = 1L,
            )
        assertFalse(result.isUpdateAvailable)
        assertEquals(200L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallKeepsUpdateFlagWhenSnapshotStillNewer() {
        val result =
            testUpdatableApp(latestVersion = "3.0.0", latestVersionCode = 300L).confirmInstall(
                build =
                    InstalledBuild(
                        tag = "2.0.0",
                        assetName = "a",
                        assetUrl = "u",
                        versionName = "2.0.0",
                        versionCode = 200L,
                        signingFingerprint = null,
                    ),
                at = 1L,
            )
        assertTrue(result.isUpdateAvailable)
        assertEquals(300L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallKeepsASkippedLatestHiddenAfterARollback() {
        val result =
            testUpdatableApp(
                installedVersion = "2.0.0",
                installedVersionCode = 200L,
                latestVersion = "2.0.0",
                latestVersionCode = 200L,
                skippedReleaseTag = "2.0.0",
            ).confirmInstall(
                build =
                    InstalledBuild(
                        tag = "1.9.0",
                        assetName = "a",
                        assetUrl = "u",
                        versionName = "1.9.0",
                        versionCode = 190L,
                        signingFingerprint = null,
                    ),
                at = 1L,
            )
        assertFalse(result.isUpdateAvailable)
        assertEquals(200L, result.latestVersionCode)
        assertEquals("2.0.0", result.skippedReleaseTag)
    }

    @Test
    fun confirmInstallKeepsFlagWhenLandedBuildIsOlderThanTarget() {
        val result =
            testUpdatableApp(latestVersion = "2.0.0", latestVersionCode = 200L).confirmInstall(
                build =
                    InstalledBuild(
                        tag = "2.0.0",
                        assetName = "a",
                        assetUrl = "u",
                        versionName = "1.5.0",
                        versionCode = 150L,
                        signingFingerprint = null,
                    ),
                at = 1L,
            )
        assertTrue(result.isUpdateAvailable)
        assertEquals(200L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallKeepsFlagWhenLandedCodeIsBelowTargetCode() {
        val result =
            testUpdatableApp(latestVersion = "2.0.0", latestVersionCode = 200L).confirmInstall(
                build =
                    InstalledBuild(
                        tag = "2.0.0",
                        assetName = "a",
                        assetUrl = "u",
                        versionName = "2.0.0",
                        versionCode = 199L,
                        signingFingerprint = null,
                    ),
                at = 1L,
            )
        assertTrue(result.isUpdateAvailable)
        assertEquals(200L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallReleasesTheTimestampLatchAndKeepsTheSnapshotCode() {
        val result =
            testUpdatableApp(
                latestVersion = "nightly",
                latestVersionCode = 500L,
                isUpdateAvailable = true,
            ).confirmInstall(
                build =
                    InstalledBuild(
                        tag = "nightly",
                        assetName = "a",
                        assetUrl = "u",
                        versionName = "26.09.01",
                        versionCode = 500L,
                        signingFingerprint = null,
                    ),
                at = 1L,
            )
        assertFalse(result.isUpdateAvailable)
        assertEquals(500L, result.latestVersionCode)
        assertEquals("nightly", result.installedVersion)
    }

    @Test
    fun confirmInstallKeepsTheLatchWhenTheLandedBuildIsOlderThanTheSnapshot() {
        val result =
            testUpdatableApp(
                latestVersion = "nightly",
                latestVersionCode = 500L,
                isUpdateAvailable = true,
            ).confirmInstall(
                build =
                    InstalledBuild(
                        tag = "nightly",
                        assetName = "a",
                        assetUrl = "u",
                        versionName = "26.09.01",
                        versionCode = 400L,
                        signingFingerprint = null,
                    ),
                at = 1L,
            )
        assertTrue(result.isUpdateAvailable)
        assertEquals(500L, result.latestVersionCode)
    }

    @Test
    fun installedNightlyIsNotOfferedBackOnTheNextScan() {
        val offeredWithNightlyAvailable =
            testUpdatableApp(
                installedVersion = "nightly",
                installedVersionCode = 400L,
                latestVersion = "nightly",
                latestVersionCode = 500L,
                isUpdateAvailable = true,
            )

        val afterInstall =
            offeredWithNightlyAvailable.confirmInstall(
                build =
                    InstalledBuild(
                        tag = "nightly",
                        assetName = "a",
                        assetUrl = "u",
                        versionName = "26.09.01",
                        versionCode = 500L,
                        signingFingerprint = null,
                    ),
                at = 5_000L,
            )

        val verdict =
            UpdateVerdict.decide(
                installed =
                    UpdateVerdict.Installed(
                        tag = afterInstall.installedVersion,
                        versionCode = afterInstall.installedVersionCode,
                    ),
                stored =
                    UpdateVerdict.Stored(
                        latestTag = afterInstall.latestVersion,
                        latestVersionCode = afterInstall.latestVersionCode,
                        publishedAt = "2026-09-01T00:00:00Z",
                        wasUpdateAvailable = afterInstall.isUpdateAvailable,
                    ),
                matched =
                    UpdateVerdict.Matched(
                        tag = "nightly",
                        publishedAt = "2026-09-01T00:00:00Z",
                        isPrerelease = true,
                    ),
                skippedTag = null,
            )

        assertFalse(verdict.isUpdateAvailable)
    }

    @Test
    fun anUninstalledNightlyStaysOfferedAcrossScans() {
        val verdict =
            UpdateVerdict.decide(
                installed =
                    UpdateVerdict.Installed(tag = "nightly", versionCode = 400L),
                stored =
                    UpdateVerdict.Stored(
                        latestTag = "nightly",
                        latestVersionCode = 500L,
                        publishedAt = "2026-09-01T00:00:00Z",
                        wasUpdateAvailable = true,
                    ),
                matched =
                    UpdateVerdict.Matched(
                        tag = "nightly",
                        publishedAt = "2026-09-01T00:00:00Z",
                        isPrerelease = true,
                    ),
                skippedTag = null,
            )

        assertTrue(verdict.isUpdateAvailable)
    }

    @Test
    fun confirmInstallClearsUpdateFlagWhenSnapshotMissing() {
        val result =
            testUpdatableApp(latestVersion = null, latestVersionCode = null).confirmInstall(
                build =
                    InstalledBuild(
                        tag = "1.0.0",
                        assetName = "a",
                        assetUrl = "u",
                        versionName = "1.0.0",
                        versionCode = 100L,
                        signingFingerprint = null,
                    ),
                at = 1L,
            )
        assertFalse(result.isUpdateAvailable)
        assertEquals(100L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallClearsFlagWhenSameTagCarriesNewerCode() {
        val result =
            testUpdatableApp(latestVersion = "2.0.0", latestVersionCode = 200L).confirmInstall(
                build =
                    InstalledBuild(
                        tag = "2.0.0",
                        assetName = "a",
                        assetUrl = "u",
                        versionName = "2.0.0",
                        versionCode = 201L,
                        signingFingerprint = null,
                    ),
                at = 1L,
            )
        assertFalse(result.isUpdateAvailable)
        assertEquals(201L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallPendingHandoffKeepsParkedMetadata() {
        val result =
            testUpdatableApp().confirmInstall(
                build =
                    InstalledBuild(
                        tag = "2.0.0",
                        assetName = "a",
                        assetUrl = "u",
                        versionName = "2.0.0",
                        versionCode = 200L,
                        signingFingerprint = null,
                    ),
                isPending = true,
                at = 1L,
            )
        assertTrue(result.isPendingInstall)
        assertEquals("/data/parked.apk", result.pendingInstallFilePath)
        assertEquals("2.0.0", result.pendingInstallVersion)
    }

    @Test
    fun confirmInstallDoesNotTouchCheckZoneSnapshot() {
        val result =
            testUpdatableApp().confirmInstall(
                build =
                    InstalledBuild(
                        tag = "2.0.0",
                        assetName = "a",
                        assetUrl = "u",
                        versionName = "2.0.0",
                        versionCode = 200L,
                        signingFingerprint = null,
                    ),
                at = 1L,
            )
        assertEquals("2.0.0", result.latestVersion)
        assertEquals("app-2.0.0.apk", result.latestAssetName)
        assertEquals("2026-08-01T00:00:00Z", result.latestReleasePublishedAt)
        assertEquals("2.0.0", result.latestVersionName)
    }
}
