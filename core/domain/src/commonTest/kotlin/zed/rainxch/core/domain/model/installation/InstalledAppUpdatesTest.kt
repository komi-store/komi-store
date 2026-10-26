package zed.rainxch.core.domain.model.installation

import zed.rainxch.core.domain.utils.UpdateVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstalledAppUpdatesTest {
    @Test
    fun settling_a_park_the_device_already_runs_clears_the_pointer() {
        val parked =
            testInstalledApp(
                isPendingInstall = true,
                pendingInstallFilePath = "/tmp/app.apk",
            )

        val settled =
            parked
                .settlePendingFromSystem(versionName = "1.0.0", versionCode = 100L)
                .clearPending()

        assertFalse(settled.isPendingInstall)
        assertEquals(null, settled.pendingInstallFilePath)
    }

    @Test
    fun a_settled_identity_adopts_the_parked_release_timestamp() {
        val parked =
            testUpdatableApp(latestVersionCode = 200L).copy(
                isPendingInstall = true,
                pendingInstallReleaseId = 7L,
                pendingInstallAssetId = 8L,
                pendingInstallAssetDigest = "sha256:abc",
                pendingInstallReleasePublishedAt = "2026-10-01T00:00:00Z",
            )

        val settled = parked.withSettledInstallIdentity(versionCode = 200L)

        assertEquals("2026-10-01T00:00:00Z", settled.installedReleasePublishedAt)
        assertEquals(null, settled.pendingInstallReleasePublishedAt)
    }

    @Test
    fun resolvePendingFromSystemAdoptsTagAndClearsPending() {
        val result =
            testUpdatableApp(isPendingInstall = true).resolvePendingFromSystem(
                PendingInstallResolution.Reached(
                    resolvedTag = "2.0.0",
                    versionName = "2.0.0",
                    versionCode = 200L,
                ),
            )
        assertFalse(result.isPendingInstall)
        assertEquals("2.0.0", result.installedVersion)
        assertEquals("2.0.0", result.installedVersionName)
        assertEquals(200L, result.installedVersionCode)
        assertFalse(result.isUpdateAvailable)
        assertEquals("/data/parked.apk", result.pendingInstallFilePath)
    }

    @Test
    fun resolvePendingFromSystemDropsTheBindingWhenTheInstallDidNotLand() {
        val result =
            testUpdatableApp(latestVersionCode = 300L)
                .copy(
                    installedReleaseId = 7001L,
                    installedAssetId = 7002L,
                    installedAssetDigest = "sha256:x",
                ).resolvePendingFromSystem(
                    PendingInstallResolution.Reached(
                        resolvedTag = "2.0.0",
                        versionName = "2.0.0",
                        versionCode = 200L,
                    ),
                )
        assertEquals(null, result.installedReleaseId)
        assertEquals(null, result.installedAssetId)
        assertEquals(null, result.installedAssetDigest)
    }

    @Test
    fun resolvePendingFromSystemKeepsTheBindingWhenThereWasNoTarget() {
        val result =
            testUpdatableApp(latestVersionCode = null)
                .copy(installedReleaseId = 7001L)
                .resolvePendingFromSystem(
                    PendingInstallResolution.Reached(
                        resolvedTag = "1.0.0",
                        versionName = "1.0.0",
                        versionCode = 100L,
                    ),
                )
        assertEquals(7001L, result.installedReleaseId)
    }

    @Test
    fun resolvePendingFromSystemKeepsTheBindingWhenTheInstallLanded() {
        val result =
            testUpdatableApp(installedVersionCode = 200L, latestVersionCode = 200L)
                .copy(
                    installedReleaseId = 7001L,
                    installedAssetId = 7002L,
                    installedAssetDigest = "sha256:x",
                ).resolvePendingFromSystem(
                    PendingInstallResolution.Reached(
                        resolvedTag = "2.0.0",
                        versionName = "2.0.0",
                        versionCode = 200L,
                    ),
                )
        assertEquals(7001L, result.installedReleaseId)
    }

    @Test
    fun resolvePendingFromSystemKeepsUpdateFlagWhenSnapshotNewer() {
        val result =
            testUpdatableApp(latestVersionCode = 300L).resolvePendingFromSystem(
                PendingInstallResolution.Reached(
                    resolvedTag = "2.0.0",
                    versionName = "2.0.0",
                    versionCode = 200L,
                ),
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun updateFlagIsFalseWhenSnapshotCodeIsNull() {
        val result =
            testUpdatableApp(latestVersionCode = null).resolvePendingFromSystem(
                PendingInstallResolution.Reached(
                    resolvedTag = "2.0.0",
                    versionName = "2.0.0",
                    versionCode = 100L,
                ),
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun updateFlagIsFalseWhenSnapshotCodeIsZero() {
        val result =
            testUpdatableApp(latestVersionCode = 0L).observeExternalInstall(
                versionName = "2.0.0",
                versionCode = 100L,
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun observeExternalInstallKeepsInstalledTagBelowTheSnapshot() {
        val result =
            testUpdatableApp().observeExternalInstall(
                versionName = "1.2.0",
                versionCode = 120L,
            )
        assertEquals("1.0.0", result.installedVersion)
        assertEquals("1.2.0", result.installedVersionName)
        assertEquals(120L, result.installedVersionCode)
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun observeExternalInstallAdoptsSnapshotTagOnceObservationReachesIt() {
        val result =
            testUpdatableApp().observeExternalInstall(
                versionName = "2.0.0",
                versionCode = 200L,
            )
        assertEquals("2.0.0", result.installedVersion)
        assertEquals("2.0.0", result.installedVersionName)
        assertEquals(200L, result.installedVersionCode)
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun observeExternalInstallAdoptsSnapshotTagByNameWhenTheCodeWasCleared() {
        val result =
            testUpdatableApp(latestVersionCode = null).observeExternalInstall(
                versionName = "2.0.0",
                versionCode = 200L,
            )
        assertEquals("2.0.0", result.installedVersion)
    }

    @Test
    fun observeExternalInstallKeepsTagForABuildTheSnapshotDoesNotName() {
        val result =
            testUpdatableApp(latestVersionCode = null).observeExternalInstall(
                versionName = "2.1.0-dev",
                versionCode = 210L,
            )
        assertEquals("1.0.0", result.installedVersion)
    }

    @Test
    fun scanAfterAdoptedExternalUpdateIsQuiet() {
        val observed =
            testUpdatableApp(latestVersionCode = null).observeExternalInstall(
                versionName = "2.0.0",
                versionCode = 200L,
            )
        val verdict =
            UpdateVerdict.decide(
                installed = UpdateVerdict.Installed(observed.installedVersion, observed.installedVersionCode),
                stored =
                    UpdateVerdict.Stored(
                        latestTag = observed.latestVersion,
                        latestVersionCode = observed.latestVersionCode,
                        publishedAt = "2026-09-01T00:00:00Z",
                        wasUpdateAvailable = observed.isUpdateAvailable,
                    ),
                matched = UpdateVerdict.Matched(tag = "2.0.0", publishedAt = "2026-09-01T00:00:00Z", isPrerelease = false),
                skippedTag = null,
            )
        assertFalse(verdict.isUpdateAvailable)
    }

    @Test
    fun observeExternalInstallDetectsDowngradeAsUpdateAvailable() {
        val result =
            testUpdatableApp(latestVersionCode = 200L).observeExternalInstall(
                versionName = "0.9.0",
                versionCode = 50L,
            )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun markAndClearPendingTouchOnlyPendingFlag() {
        val marked =
            testUpdatableApp().markPending(
                releaseId = 7001L,
                assetId = 7002L,
                assetDigest = "sha256:aaa",
            )
        assertTrue(marked.isPendingInstall)
        assertEquals("/data/parked.apk", marked.pendingInstallFilePath)

        val cleared = testUpdatableApp(isPendingInstall = true).clearPending()
        assertFalse(cleared.isPendingInstall)
    }

    @Test
    fun withLatestSnapshotWritesOnlyCheckZone() {
        val result =
            testUpdatableApp().withLatestSnapshot(
                version = "nightly",
                assetName = "app-nightly.apk",
                assetUrl = "https://dl/app-nightly.apk",
                versionName = "26.09.01",
                versionCode = 999L,
            )
        assertEquals("nightly", result.latestVersion)
        assertEquals("app-nightly.apk", result.latestAssetName)
        assertEquals("https://dl/app-nightly.apk", result.latestAssetUrl)
        assertEquals("26.09.01", result.latestVersionName)
        assertEquals(999L, result.latestVersionCode)
        assertEquals("1.0.0", result.installedVersion)
        assertEquals(100L, result.installedVersionCode)
        assertFalse(result.isPendingInstall)
    }

    @Test
    fun chainedPreInstallHandoffCombinesBothZones() {
        val result =
            testUpdatableApp()
                .markPending(
                    releaseId = 7001L,
                    assetId = 7002L,
                    assetDigest = "sha256:aaa",
                ).withLatestSnapshot(
                    version = "3.0.0",
                    assetName = "a3",
                    assetUrl = "u3",
                    versionName = "3.0.0",
                    versionCode = 300L,
                )
        assertTrue(result.isPendingInstall)
        assertEquals("3.0.0", result.latestVersion)
        assertEquals("1.0.0", result.installedVersion)
    }

    @Test
    fun migratedVersionInfoAlignsBothSides() {
        val result =
            testUpdatableApp().withMigratedVersionInfo(
                versionName = "1.5.0",
                versionCode = 150L,
            )
        assertEquals("1.5.0", result.installedVersionName)
        assertEquals(150L, result.installedVersionCode)
        assertEquals("1.5.0", result.latestVersionName)
        assertEquals(150L, result.latestVersionCode)
        assertEquals("1.0.0", result.installedVersion)
        assertEquals("2.0.0", result.latestVersion)
    }

    @Test
    fun normalizeInstalledTagAlignsTagAndForwardsTheVerdict() {
        val result =
            testUpdatableApp(installedVersion = "1.9.0", isUpdateAvailable = true).normalizeInstalledTag(
                tag = "2.0.0",
                isUpdateAvailable = true,
            )
        assertEquals("2.0.0", result.installedVersion)
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun normalizeInstalledTagKeepsAClearedVerdictCleared() {
        val result =
            testUpdatableApp(installedVersion = "1.9.0", isUpdateAvailable = false).normalizeInstalledTag(
                tag = "2.0.0",
                isUpdateAvailable = false,
            )
        assertEquals("2.0.0", result.installedVersion)
        assertFalse(result.isUpdateAvailable)
        assertEquals(200L, result.latestVersionCode)
    }

    @Test
    fun normalizeInstalledTagLeavesPendingAndSnapshotFieldsUntouched() {
        val result =
            testUpdatableApp(latestVersion = "3.0.0", latestVersionCode = 300L, isPendingInstall = true)
                .normalizeInstalledTag(tag = "2.0.0", isUpdateAvailable = false)
        assertEquals("2.0.0", result.installedVersion)
        assertTrue(result.isPendingInstall)
        assertEquals("2.0.0", result.pendingInstallVersion)
        assertEquals("3.0.0", result.latestVersion)
        assertEquals(300L, result.latestVersionCode)
    }

    @Test
    fun resolvePendingFromSystemAdoptsTheParkedTagWhenTheInstallReachedTarget() {
        val result =
            testUpdatableApp(
                installedVersion = "1.0.0",
                latestVersion = "3.0.0",
                latestVersionCode = 300L,
                isPendingInstall = true,
            ).resolvePendingFromSystem(
                PendingInstallResolution.Reached(
                    resolvedTag = "2.0.0",
                    versionName = "1.5.0",
                    versionCode = 300L,
                ),
            )
        assertEquals("2.0.0", result.installedVersion)
        assertFalse(result.isPendingInstall)
    }

    @Test
    fun resolvePendingFromSystemAdoptsTheParkedTagWhenTheNameMovedWithoutACode() {
        val result =
            testUpdatableApp(
                installedVersion = "1.0.0",
                latestVersion = "2.0.0",
                latestVersionCode = null,
                isPendingInstall = true,
            ).resolvePendingFromSystem(
                PendingInstallResolution.Reached(
                    resolvedTag = "2.0.0",
                    versionName = "2.0.0",
                    versionCode = 100L,
                ),
            )
        assertEquals("2.0.0", result.installedVersion)
        assertFalse(result.isPendingInstall)
    }

    @Test
    fun externalInstallFlagIsNotFooledByAStaleInstalledTag() {
        val afterExternalInstall =
            testUpdatableApp(installedVersion = "1.0.0", latestVersion = "2.0.0", latestVersionCode = null)

        val preFix =
            UpdateVerdict.decide(
                installed = UpdateVerdict.Installed("1.0.0", 200L),
                stored =
                    UpdateVerdict.Stored(
                        latestTag = "2.0.0",
                        latestVersionCode = null,
                        publishedAt = "2026-08-01T00:00:00Z",
                        wasUpdateAvailable = false,
                    ),
                matched = UpdateVerdict.Matched("2.0.0", "2026-08-01T00:00:00Z", false),
                skippedTag = null,
            )
        assertTrue(preFix.isUpdateAvailable)

        assertFalse(
            afterExternalInstall.externalInstallUpdateFlag(
                newVersionName = "2.0.0",
                newVersionCode = 200L,
            ),
        )
    }

    @Test
    fun externalInstallFlagStillReportsANewerRelease() {
        val refreshed =
            testUpdatableApp(
                installedVersion = "2.0.0",
                latestVersion = "3.0.0",
                latestVersionCode = null,
                latestVersionName = "3.0.0",
            )
        assertTrue(
            refreshed.externalInstallUpdateFlag(
                newVersionName = "2.0.0",
                newVersionCode = 200L,
            ),
        )
    }

    @Test
    fun snapshotComparisonRaisesTheFlagWhenTheBaselineSurvivedButTheCodeDidNot() {
        val drifted = testUpdatableApp(latestVersion = "3.0.0", latestVersionCode = null)
        assertTrue(
            drifted.snapshotStillNamesNewerBuild(
                installedCode = 200L,
                installedVersion = "2.0.0",
            ),
        )
        assertFalse(
            drifted.snapshotStillNamesNewerBuild(
                installedCode = 300L,
                installedVersion = "3.0.0",
            ),
        )
    }
}
