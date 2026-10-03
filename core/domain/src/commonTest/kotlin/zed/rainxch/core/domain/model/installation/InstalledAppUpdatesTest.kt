package zed.rainxch.core.domain.model.installation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import zed.rainxch.core.domain.utils.UpdateVerdict

class InstalledAppUpdatesTest {
    private fun app(
        installedVersion: String = "1.0.0",
        installedVersionCode: Long = 100L,
        latestVersion: String? = "2.0.0",
        latestVersionCode: Long? = 200L,
        latestVersionName: String? = "2.0.0",
        isUpdateAvailable: Boolean = true,
        isPendingInstall: Boolean = false,
        pendingFilePath: String? = "/data/parked.apk",
        skippedReleaseTag: String? = null,
    ): InstalledApp = InstalledApp(
        packageName = "com.example.app",
        repoId = 1L,
        repoName = "app",
        repoOwner = "owner",
        repoOwnerAvatarUrl = "https://avatar",
        repoDescription = null,
        primaryLanguage = "Kotlin",
        repoUrl = "https://github.com/owner/app",
        installedVersion = installedVersion,
        installedAssetName = "app-1.0.0.apk",
        installedAssetUrl = "https://dl/app-1.0.0.apk",
        latestVersion = latestVersion,
        latestAssetName = "app-2.0.0.apk",
        latestAssetUrl = "https://dl/app-2.0.0.apk",
        latestAssetSize = 1024L,
        appName = "App",
        installSource = InstallSource.THIS_APP,
        installedAt = 1000L,
        lastCheckedAt = 2000L,
        lastUpdatedAt = 1500L,
        isUpdateAvailable = isUpdateAvailable,
        signingFingerprint = "SHA",
        systemArchitecture = "arm64-v8a",
        fileExtension = "apk",
        isPendingInstall = isPendingInstall,
        installedVersionName = "1.0.0",
        installedVersionCode = installedVersionCode,
        latestVersionName = latestVersionName,
        latestVersionCode = latestVersionCode,
        latestReleasePublishedAt = "2026-08-01T00:00:00Z",
        pendingInstallFilePath = pendingFilePath,
        pendingInstallVersion = if (pendingFilePath != null) "2.0.0" else null,
        pendingInstallAssetName = if (pendingFilePath != null) "app-2.0.0.apk" else null,
        skippedReleaseTag = skippedReleaseTag,
    )

    @Test
    fun confirmInstallWritesInstallZone() {
        val result = app().confirmInstall(
            tag = "2.0.0",
            assetName = "app-2.0.0.apk",
            assetUrl = "https://dl/app-2.0.0.apk",
            versionName = "2.0.0",
            versionCode = 200L,
            signingFingerprint = "SHA-NEW",
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
    fun confirmInstallClearsUpdateFlagWhenLatestMatchesInstalled() {
        val result = app().confirmInstall(
            tag = "2.0.0",
            assetName = "a",
            assetUrl = "u",
            versionName = "2.0.0",
            versionCode = 200L,
            signingFingerprint = null,
            at = 1L,
        )
        assertFalse(result.isUpdateAvailable)
        assertEquals(200L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallKeepsUpdateFlagWhenSnapshotStillNewer() {
        val result = app(latestVersion = "3.0.0", latestVersionCode = 300L).confirmInstall(
            tag = "2.0.0",
            assetName = "a",
            assetUrl = "u",
            versionName = "2.0.0",
            versionCode = 200L,
            signingFingerprint = null,
            at = 1L,
        )
        assertTrue(result.isUpdateAvailable)
        assertEquals(300L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallKeepsASkippedLatestHiddenAfterARollback() {
        val result =
            app(
                installedVersion = "2.0.0",
                installedVersionCode = 200L,
                latestVersion = "2.0.0",
                latestVersionCode = 200L,
                skippedReleaseTag = "2.0.0",
            ).confirmInstall(
                tag = "1.9.0",
                assetName = "a",
                assetUrl = "u",
                versionName = "1.9.0",
                versionCode = 190L,
                signingFingerprint = null,
                at = 1L,
            )
        assertFalse(result.isUpdateAvailable)
        assertEquals(200L, result.latestVersionCode)
        assertEquals("2.0.0", result.skippedReleaseTag)
    }

    @Test
    fun confirmInstallKeepsFlagWhenLandedBuildIsOlderThanTarget() {
        val result =
            app(latestVersion = "2.0.0", latestVersionCode = 200L).confirmInstall(
                tag = "2.0.0",
                assetName = "a",
                assetUrl = "u",
                versionName = "1.5.0",
                versionCode = 150L,
                signingFingerprint = null,
                at = 1L,
            )
        assertTrue(result.isUpdateAvailable)
        assertEquals(200L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallKeepsFlagWhenLandedCodeIsBelowTargetCode() {
        val result =
            app(latestVersion = "2.0.0", latestVersionCode = 200L).confirmInstall(
                tag = "2.0.0",
                assetName = "a",
                assetUrl = "u",
                versionName = "2.0.0",
                versionCode = 199L,
                signingFingerprint = null,
                at = 1L,
            )
        assertTrue(result.isUpdateAvailable)
        assertEquals(200L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallReleasesTheTimestampLatchAndKeepsTheSnapshotCode() {
        val result =
            app(
                latestVersion = "nightly",
                latestVersionCode = 500L,
                isUpdateAvailable = true,
            ).confirmInstall(
                tag = "nightly",
                assetName = "a",
                assetUrl = "u",
                versionName = "26.09.01",
                versionCode = 500L,
                signingFingerprint = null,
                at = 1L,
            )
        assertFalse(result.isUpdateAvailable)
        assertEquals(500L, result.latestVersionCode)
        assertEquals("nightly", result.installedVersion)
    }

    @Test
    fun confirmInstallKeepsTheLatchWhenTheLandedBuildIsOlderThanTheSnapshot() {
        val result =
            app(
                latestVersion = "nightly",
                latestVersionCode = 500L,
                isUpdateAvailable = true,
            ).confirmInstall(
                tag = "nightly",
                assetName = "a",
                assetUrl = "u",
                versionName = "26.09.01",
                versionCode = 400L,
                signingFingerprint = null,
                at = 1L,
            )
        assertTrue(result.isUpdateAvailable)
        assertEquals(500L, result.latestVersionCode)
    }

    @Test
    fun installedNightlyIsNotOfferedBackOnTheNextScan() {
        val offeredWithNightlyAvailable =
            app(
                installedVersion = "nightly",
                installedVersionCode = 400L,
                latestVersion = "nightly",
                latestVersionCode = 500L,
                isUpdateAvailable = true,
            )

        val afterInstall =
            offeredWithNightlyAvailable.confirmInstall(
                tag = "nightly",
                assetName = "a",
                assetUrl = "u",
                versionName = "26.09.01",
                versionCode = 500L,
                signingFingerprint = null,
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
        val result = app(latestVersion = null, latestVersionCode = null).confirmInstall(
            tag = "1.0.0",
            assetName = "a",
            assetUrl = "u",
            versionName = "1.0.0",
            versionCode = 100L,
            signingFingerprint = null,
            at = 1L,
        )
        assertFalse(result.isUpdateAvailable)
        assertEquals(100L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallClearsFlagWhenSameTagCarriesNewerCode() {
        val result =
            app(latestVersion = "2.0.0", latestVersionCode = 200L).confirmInstall(
                tag = "2.0.0",
                assetName = "a",
                assetUrl = "u",
                versionName = "2.0.0",
                versionCode = 201L,
                signingFingerprint = null,
                at = 1L,
            )
        assertFalse(result.isUpdateAvailable)
        assertEquals(201L, result.latestVersionCode)
    }

    @Test
    fun confirmInstallPendingHandoffKeepsParkedMetadata() {
        val result = app().confirmInstall(
            tag = "2.0.0",
            assetName = "a",
            assetUrl = "u",
            versionName = "2.0.0",
            versionCode = 200L,
            signingFingerprint = null,
            isPending = true,
            at = 1L,
        )
        assertTrue(result.isPendingInstall)
        assertEquals("/data/parked.apk", result.pendingInstallFilePath)
        assertEquals("2.0.0", result.pendingInstallVersion)
    }

    @Test
    fun confirmInstallDoesNotTouchCheckZoneSnapshot() {
        val result = app().confirmInstall(
            tag = "2.0.0",
            assetName = "a",
            assetUrl = "u",
            versionName = "2.0.0",
            versionCode = 200L,
            signingFingerprint = null,
            at = 1L,
        )
        assertEquals("2.0.0", result.latestVersion)
        assertEquals("app-2.0.0.apk", result.latestAssetName)
        assertEquals("2026-08-01T00:00:00Z", result.latestReleasePublishedAt)
        assertEquals("2.0.0", result.latestVersionName)
    }

    @Test
    fun resolvePendingFromSystemAdoptsTagAndClearsPending() {
        val result = app(isPendingInstall = true).resolvePendingFromSystem(
            resolvedTag = "2.0.0",
            versionName = "2.0.0",
            versionCode = 200L,
        )
        assertFalse(result.isPendingInstall)
        assertEquals("2.0.0", result.installedVersion)
        assertEquals("2.0.0", result.installedVersionName)
        assertEquals(200L, result.installedVersionCode)
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun resolvePendingFromSystemDropsTheBindingWhenTheInstallDidNotLand() {
        val result =
            app(latestVersionCode = 300L)
                .copy(
                    installedReleaseId = 7001L,
                    installedAssetId = 7002L,
                    installedAssetDigest = "sha256:x",
                )
                .resolvePendingFromSystem(
                    resolvedTag = "2.0.0",
                    versionName = "2.0.0",
                    versionCode = 200L,
                )
        assertEquals(null, result.installedReleaseId)
        assertEquals(null, result.installedAssetId)
        assertEquals(null, result.installedAssetDigest)
    }

    @Test
    fun resolvePendingFromSystemKeepsTheBindingWhenThereWasNoTarget() {
        val result =
            app(latestVersionCode = null)
                .copy(installedReleaseId = 7001L)
                .resolvePendingFromSystem(
                    resolvedTag = "1.0.0",
                    versionName = "1.0.0",
                    versionCode = 100L,
                )
        assertEquals(7001L, result.installedReleaseId)
    }

    @Test
    fun resolvePendingFromSystemKeepsTheBindingWhenTheInstallLanded() {
        val result =
            app(latestVersionCode = 200L)
                .copy(
                    installedReleaseId = 7001L,
                    installedAssetId = 7002L,
                    installedAssetDigest = "sha256:x",
                )
                .resolvePendingFromSystem(
                    resolvedTag = "2.0.0",
                    versionName = "2.0.0",
                    versionCode = 200L,
                )
        assertEquals(7001L, result.installedReleaseId)
    }

    @Test
    fun resolvePendingFromSystemKeepsUpdateFlagWhenSnapshotNewer() {
        val result = app(latestVersionCode = 300L).resolvePendingFromSystem(
            resolvedTag = "2.0.0",
            versionName = "2.0.0",
            versionCode = 200L,
        )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun updateFlagIsFalseWhenSnapshotCodeIsNull() {
        val result =
            app(latestVersionCode = null).resolvePendingFromSystem(
                resolvedTag = "2.0.0",
                versionName = "2.0.0",
                versionCode = 100L,
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun updateFlagIsFalseWhenSnapshotCodeIsZero() {
        val result =
            app(latestVersionCode = 0L).observeExternalInstall(
                versionName = "2.0.0",
                versionCode = 100L,
            )
        assertFalse(result.isUpdateAvailable)
    }

    @Test
    fun observeExternalInstallKeepsInstalledTagBelowTheSnapshot() {
        val result = app().observeExternalInstall(
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
        val result = app().observeExternalInstall(
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
        val result = app(latestVersionCode = null).observeExternalInstall(
            versionName = "2.0.0",
            versionCode = 200L,
        )
        assertEquals("2.0.0", result.installedVersion)
    }

    @Test
    fun observeExternalInstallKeepsTagForABuildTheSnapshotDoesNotName() {
        val result = app(latestVersionCode = null).observeExternalInstall(
            versionName = "2.1.0-dev",
            versionCode = 210L,
        )
        assertEquals("1.0.0", result.installedVersion)
    }

    @Test
    fun scanAfterAdoptedExternalUpdateIsQuiet() {
        val observed = app(latestVersionCode = null).observeExternalInstall(
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
        val result = app(latestVersionCode = 200L).observeExternalInstall(
            versionName = "0.9.0",
            versionCode = 50L,
        )
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun markAndClearPendingTouchOnlyPendingFlag() {
        val marked = app().markPending()
        assertTrue(marked.isPendingInstall)
        assertEquals("/data/parked.apk", marked.pendingInstallFilePath)

        val cleared = app(isPendingInstall = true).clearPending()
        assertFalse(cleared.isPendingInstall)
    }

    @Test
    fun withLatestSnapshotWritesOnlyCheckZone() {
        val result = app().withLatestSnapshot(
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
        val result = app()
            .markPending()
            .withLatestSnapshot(
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
        val result = app().withMigratedVersionInfo(
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
            app(installedVersion = "1.9.0", isUpdateAvailable = true).normalizeInstalledTag(
                tag = "2.0.0",
                isUpdateAvailable = true,
            )
        assertEquals("2.0.0", result.installedVersion)
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun normalizeInstalledTagKeepsAClearedVerdictCleared() {
        val result =
            app(installedVersion = "1.9.0", isUpdateAvailable = false).normalizeInstalledTag(
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
            app(latestVersion = "3.0.0", latestVersionCode = 300L, isPendingInstall = true)
                .normalizeInstalledTag(tag = "2.0.0", isUpdateAvailable = false)
        assertEquals("2.0.0", result.installedVersion)
        assertTrue(result.isPendingInstall)
        assertEquals("2.0.0", result.pendingInstallVersion)
        assertEquals("3.0.0", result.latestVersion)
        assertEquals(300L, result.latestVersionCode)
    }

    @Test
    fun resolvePendingFromSystemKeepsTheOldTagWhenTheInstallDidNotReachTarget() {
        val result =
            app(
                installedVersion = "1.0.0",
                latestVersion = "2.0.0",
                latestVersionCode = 200L,
                isPendingInstall = true,
            ).resolvePendingFromSystem(
                resolvedTag = "2.0.0",
                versionName = "1.0.0",
                versionCode = 100L,
            )
        assertEquals("1.0.0", result.installedVersion)
        assertFalse(result.isPendingInstall)
        assertTrue(result.isUpdateAvailable)
    }

    @Test
    fun resolvePendingFromSystemAdoptsTheParkedTagWhenTheInstallReachedTarget() {
        val result =
            app(
                installedVersion = "1.0.0",
                latestVersion = "3.0.0",
                latestVersionCode = 300L,
                isPendingInstall = true,
            ).resolvePendingFromSystem(
                resolvedTag = "3.0.0",
                versionName = "1.5.0",
                versionCode = 300L,
            )
        assertEquals("2.0.0", result.installedVersion)
        assertFalse(result.isPendingInstall)
    }

    @Test
    fun externalInstallFlagIsNotFooledByAStaleInstalledTag() {
        val afterExternalInstall =
            app(installedVersion = "1.0.0", latestVersion = "2.0.0", latestVersionCode = null)

        val preFix = UpdateVerdict.decide(
            installed = UpdateVerdict.Installed("1.0.0", 200L),
            stored = UpdateVerdict.Stored(
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
            app(
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
        val drifted = app(latestVersion = "3.0.0", latestVersionCode = null)
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

