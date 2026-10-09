package zed.rainxch.core.domain.model.installation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class InstallBindingTest {
    private fun app(
        installedVersion: String = "1.0.0",
        installedVersionName: String? = "1.0.0",
        installedVersionCode: Long = 100L,
        signingFingerprint: String? = "AAAA",
        installedReleaseId: Long? = 9001L,
        installedAssetId: Long? = 9002L,
        installedAssetDigest: String? = "sha256:old",
        latestVersion: String? = "2.0.0",
        latestVersionCode: Long? = 200L,
        latestReleaseId: Long? = 7001L,
        latestAssetId: Long? = 7002L,
        latestAssetDigest: String? = "sha256:aaa",
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
        latestReleaseId = latestReleaseId,
        latestAssetId = latestAssetId,
        latestAssetDigest = latestAssetDigest,
        latestVersionCode = latestVersionCode,
        appName = "App",
        installSource = InstallSource.THIS_APP,
        installedAt = 1000L,
        lastCheckedAt = 2000L,
        lastUpdatedAt = 1500L,
        isUpdateAvailable = false,
        signingFingerprint = signingFingerprint,
        systemArchitecture = "arm64-v8a",
        fileExtension = "apk",
        installedVersionName = installedVersionName,
        installedVersionCode = installedVersionCode,
        installedReleaseId = installedReleaseId,
        installedAssetId = installedAssetId,
        installedAssetDigest = installedAssetDigest,
    )

    private fun local(
        packageName: String = "com.example.app",
        versionName: String = "1.0.0",
        versionCode: Long = 100L,
        signingFingerprint: String? = "AAAA",
    ): SystemPackageInfo = SystemPackageInfo(
        packageName = packageName,
        versionName = versionName,
        versionCode = versionCode,
        isInstalled = true,
        signingFingerprint = signingFingerprint,
    )

    @Test
    fun intactWhenEverythingMatches() {
        val status = app().bindingStatusAgainst(local())
        assertEquals(BindingStatus.Intact, status)
    }

    @Test
    fun brokenWhenPackageNameDiffers() {
        val status = app().bindingStatusAgainst(local(packageName = "com.other.app"))
        assertEquals(BindingStatus.Broken(BindingStatus.BreakReason.PACKAGE_NAME), status)
    }

    @Test
    fun brokenWhenVersionCodeDiffers() {
        val status = app().bindingStatusAgainst(local(versionCode = 101L))
        assertEquals(BindingStatus.Broken(BindingStatus.BreakReason.VERSION_CODE), status)
    }

    @Test
    fun brokenWhenVersionNameDiffers() {
        val status = app().bindingStatusAgainst(local(versionName = "1.0.1"))
        assertEquals(BindingStatus.Broken(BindingStatus.BreakReason.VERSION_NAME), status)
    }

    @Test
    fun brokenWhenSigningFingerprintDiffers() {
        val status = app().bindingStatusAgainst(local(signingFingerprint = "BBBB"))
        assertEquals(BindingStatus.Broken(BindingStatus.BreakReason.SIGNING_FINGERPRINT), status)
    }

    @Test
    fun recordsWithoutReleaseIdStillVerifyViaLocalFacts() {
        val status = app(installedReleaseId = null)
            .bindingStatusAgainst(local())
        assertEquals(BindingStatus.Intact, status)
    }

    @Test
    fun recordsWithoutReleaseIdStillBreakOnLocalMismatch() {
        val status = app(installedReleaseId = null).bindingStatusAgainst(local(versionCode = 999L))
        assertEquals(BindingStatus.Broken(BindingStatus.BreakReason.VERSION_CODE), status)
    }

    @Test
    fun signingComparisonIgnoresCase() {
        val status = app(signingFingerprint = "aabb")
            .bindingStatusAgainst(local(signingFingerprint = "AABB"))
        assertEquals(BindingStatus.Intact, status)
    }

    @Test
    fun missingCodesOnEitherSideDoNotBreak() {
        assertEquals(BindingStatus.Intact, app().bindingStatusAgainst(local(versionCode = 0L)))
        assertEquals(BindingStatus.Intact, app(installedVersionCode = 0L).bindingStatusAgainst(local()))
    }

    @Test
    fun blankVersionNamesDoNotBreak() {
        assertEquals(BindingStatus.Intact, app().bindingStatusAgainst(local(versionName = "")))
        assertEquals(BindingStatus.Intact, app(installedVersionName = null).bindingStatusAgainst(local()))
    }

    @Test
    fun missingLocalSigningDoesNotBreak() {
        val status = app().bindingStatusAgainst(local(signingFingerprint = null))
        assertEquals(BindingStatus.Intact, status)
    }

    @Test
    fun missingStoredSigningDoesNotBreak() {
        val status = app(signingFingerprint = null)
            .bindingStatusAgainst(local(signingFingerprint = "BBBB"))
        assertEquals(BindingStatus.Intact, status)
    }

    @Test
    fun observeExternalInstallClearsTheBindingIdentity() {
        val after = app().observeExternalInstall(versionName = "3.0.0", versionCode = 300L)
        assertEquals(null, after.installedReleaseId)
        assertEquals(null, after.installedAssetId)
        assertEquals(null, after.installedAssetDigest)
    }

    @Test
    fun confirmInstallRecordsTheBindingIdentity() {
        val after = app().confirmInstall(
            tag = "2.0.0",
            releaseId = 7001L,
            assetId = 7002L,
            assetDigest = "sha256:aaa",
            assetName = "app-2.0.0.apk",
            assetUrl = "https://dl/app-2.0.0.apk",
            versionName = "2.0.0",
            versionCode = 200L,
            signingFingerprint = "AAAA",
            at = 3000L,
        )
        assertEquals(7001L, after.installedReleaseId)
        assertEquals(7002L, after.installedAssetId)
        assertEquals("sha256:aaa", after.installedAssetDigest)
    }

    @Test
    fun confirmInstallTakesTheIdentityFromItsArgumentsNotTheSnapshot() {
        val after = app().confirmInstall(
            tag = "2.0.0",
            releaseId = 999L,
            assetId = 998L,
            assetDigest = "sha256:zzz",
            assetName = "app-2.0.0.apk",
            assetUrl = "https://dl/app-2.0.0.apk",
            versionName = "2.0.0",
            versionCode = 200L,
            signingFingerprint = "AAAA",
            at = 3000L,
        )
        assertEquals(999L, after.installedReleaseId)
        assertEquals(998L, after.installedAssetId)
        assertEquals("sha256:zzz", after.installedAssetDigest)
    }

    @Test
    fun confirmInstallDropsAnIdentityItWasNotGiven() {
        val after =
            app().confirmInstall(
                tag = "2.0.0",
                assetName = "app-2.0.0.apk",
                assetUrl = "https://dl/app-2.0.0.apk",
                versionName = "2.0.0",
                versionCode = 200L,
                signingFingerprint = "AAAA",
                at = 3000L,
            )
        assertEquals(null, after.installedReleaseId)
        assertEquals(null, after.installedAssetId)
        assertEquals(null, after.installedAssetDigest)
    }

    @Test
    fun markPendingKeepsTheInstalledIdentityUntilTheBuildLands() {
        val after = app().markPending(
            releaseId = 7001L,
            assetId = 7002L,
            assetDigest = "sha256:aaa",
        )
        assertEquals(9001L, after.installedReleaseId)
        assertEquals(9002L, after.installedAssetId)
        assertEquals("sha256:old", after.installedAssetDigest)
    }

    @Test
    fun markPendingParksTheTargetIdentity() {
        val after = app().markPending(
            releaseId = 7001L,
            assetId = 7002L,
            assetDigest = "sha256:aaa",
        )
        assertEquals(7001L, after.pendingInstallReleaseId)
        assertEquals(7002L, after.pendingInstallAssetId)
        assertEquals("sha256:aaa", after.pendingInstallAssetDigest)
    }

    @Test
    fun resolvePendingMovesIdentityAcrossWhenTheBuildLanded() {
        val parked = app(
            installedVersion = "2.0.0",
            installedVersionName = "2.0.0",
            installedVersionCode = 200L,
            installedReleaseId = null,
            installedAssetId = null,
            installedAssetDigest = null,
            latestVersion = "2.0.0",
            latestVersionCode = 200L,
        ).copy(
            isPendingInstall = true,
            pendingInstallVersion = "2.0.0",
            pendingInstallReleaseId = 7001L,
            pendingInstallAssetId = 7002L,
            pendingInstallAssetDigest = "sha256:aaa",
        )

        val after = parked.resolvePendingFromSystem(
            PendingInstallResolution.Reached(
                resolvedTag = "2.0.0",
                versionName = "2.0.0",
                versionCode = 200L,
            ),
        )

        assertEquals(7001L, after.installedReleaseId)
        assertEquals(7002L, after.installedAssetId)
        assertEquals("sha256:aaa", after.installedAssetDigest)
        assertEquals(null, after.pendingInstallReleaseId)
        assertEquals(null, after.pendingInstallAssetId)
        assertEquals(null, after.pendingInstallAssetDigest)
    }

    @Test
    fun resolvePendingDropsBothSidesWhenTheBuildNeverLanded() {
        val parked = app(
            installedVersion = "1.0.0",
            installedVersionName = "1.0.0",
            installedVersionCode = 100L,
            installedReleaseId = null,
            installedAssetId = null,
            installedAssetDigest = null,
            latestVersion = "2.0.0",
            latestVersionCode = 200L,
        ).copy(
            isPendingInstall = true,
            pendingInstallVersion = "2.0.0",
            pendingInstallReleaseId = 7001L,
            pendingInstallAssetId = 7002L,
            pendingInstallAssetDigest = "sha256:aaa",
        )

        val after = parked.resolvePendingFromSystem(
            PendingInstallResolution.Reached(
                resolvedTag = "1.0.0",
                versionName = "1.5.0",
                versionCode = 150L,
            ),
        )

        assertEquals(null, after.installedReleaseId)
        assertEquals(null, after.installedAssetId)
        assertEquals(null, after.installedAssetDigest)
        assertEquals(null, after.pendingInstallReleaseId)
        assertEquals(null, after.pendingInstallAssetId)
        assertEquals(null, after.pendingInstallAssetDigest)
    }

    @Test
    fun resolvePendingKeepsTheInstalledIdentityWhenTheInstallWasCancelled() {
        val parked = app().markPending(
            releaseId = 7001L,
            assetId = 7002L,
            assetDigest = "sha256:aaa",
        )

        val after = parked.resolvePendingFromSystem(
            PendingInstallResolution.Reached(
                resolvedTag = "1.0.0",
                versionName = "1.0.0",
                versionCode = 100L,
            ),
        )

        assertEquals(9001L, after.installedReleaseId)
        assertEquals(9002L, after.installedAssetId)
        assertEquals("sha256:old", after.installedAssetDigest)
        assertEquals(null, after.pendingInstallReleaseId)
    }

    @Test
    fun resolvePendingNeverPromotesAParkedIdentityWithoutAKnownTarget() {
        val parked = app(latestVersionCode = null).markPending(
            releaseId = 7001L,
            assetId = 7002L,
            assetDigest = "sha256:aaa",
        )

        val cancelled = parked.resolvePendingFromSystem(
            PendingInstallResolution.Reached(
                resolvedTag = "1.0.0",
                versionName = "1.0.0",
                versionCode = 100L,
            ),
        )
        assertEquals(9001L, cancelled.installedReleaseId)
        assertEquals(9002L, cancelled.installedAssetId)

        val moved = parked.resolvePendingFromSystem(
            PendingInstallResolution.Reached(
                resolvedTag = "1.0.0",
                versionName = "2.0.0",
                versionCode = 200L,
            ),
        )
        assertEquals(null, moved.installedReleaseId)
        assertEquals(null, moved.installedAssetId)
        assertEquals(null, moved.installedAssetDigest)
    }

    @Test
    fun resolvePendingNeverBindsAParkedIdentityToADifferentBuild() {
        val parked = app().markPending(
            releaseId = 7001L,
            assetId = 7002L,
            assetDigest = "sha256:aaa",
        )

        val after = parked.resolvePendingFromSystem(
            PendingInstallResolution.Reached(
                resolvedTag = "2.0.0",
                versionName = "2.1.0",
                versionCode = 210L,
            ),
        )

        assertEquals(null, after.installedReleaseId)
        assertEquals(null, after.installedAssetId)
        assertEquals(null, after.installedAssetDigest)
    }

    @Test
    fun confirmInstallDropsAParkedTargetIdentity() {
        val after =
            app()
                .markPending(releaseId = 7001L, assetId = 7002L, assetDigest = "sha256:aaa")
                .confirmInstall(
                    tag = "2.0.0",
                    releaseId = 8001L,
                    assetId = 8002L,
                    assetDigest = "sha256:bbb",
                    assetName = "app-2.0.0.apk",
                    assetUrl = "https://dl/app-2.0.0.apk",
                    versionName = "2.0.0",
                    versionCode = 200L,
                    signingFingerprint = "AAAA",
                    at = 3000L,
                    isPending = true,
                )
        assertEquals(8001L, after.installedReleaseId)
        assertEquals(null, after.pendingInstallReleaseId)
        assertEquals(null, after.pendingInstallAssetId)
        assertEquals(null, after.pendingInstallAssetDigest)
    }

    @Test
    fun clearPendingDropsTheParkedTargetIdentity() {
        val parked = app().copy(
            isPendingInstall = true,
            pendingInstallReleaseId = 7001L,
            pendingInstallAssetId = 7002L,
            pendingInstallAssetDigest = "sha256:aaa",
        )
        val after = parked.clearPending()
        assertEquals(null, after.pendingInstallReleaseId)
        assertEquals(null, after.pendingInstallAssetId)
        assertEquals(null, after.pendingInstallAssetDigest)
    }

    @Test
    fun brokenCarriesADistinctReason() {
        val a = app().bindingStatusAgainst(local(versionCode = 101L))
        val b = app().bindingStatusAgainst(local(signingFingerprint = "BBBB"))
        assertNotEquals(a, b)
    }

    @Test
    fun nothingToDoWhenTheDeviceStillMatchesTheRecord() {
        assertEquals(DeviceChange.NONE, app().deviceChangeAgainst(local()))
    }

    @Test
    fun aChangedSignerIsItsOwnKindOfChange() {
        val change = app().deviceChangeAgainst(local(signingFingerprint = "BBBB"))

        assertEquals(DeviceChange.SIGNER_CHANGE, change)
        assertNotEquals(DeviceChange.NONE, change)
    }

    @Test
    fun aLowerVersionCodeIsADowngrade() {
        assertEquals(
            DeviceChange.DOWNGRADE,
            app().deviceChangeAgainst(local(versionCode = 99L, versionName = "0.9.0")),
        )
    }

    @Test
    fun aDowngradeOutranksASignerChange() {
        // Precedence kept from the inline version: a lower code was reported as a downgrade even
        // when the signer had also moved.
        assertEquals(
            DeviceChange.DOWNGRADE,
            app().deviceChangeAgainst(local(versionCode = 99L, versionName = "0.9.0", signingFingerprint = "BBBB")),
        )
    }

    @Test
    fun aDifferentVersionNameIsAVersionChange() {
        assertEquals(
            DeviceChange.VERSION_CHANGE,
            app().deviceChangeAgainst(local(versionName = "2.0.0")),
        )
    }

    @Test
    fun aHigherVersionCodeIsAVersionChange() {
        assertEquals(
            DeviceChange.VERSION_CHANGE,
            app().deviceChangeAgainst(local(versionCode = 101L, versionName = "1.0.1")),
        )
    }

    @Test
    fun anUnknownSignerOnEitherSideIsNeverADrift() {
        // A key we do not have cannot disagree with anything. Both directions, so neither side can
        // quietly turn an unknown into a false alarm.
        assertEquals(DeviceChange.NONE, app(signingFingerprint = null).deviceChangeAgainst(local()))
        assertEquals(DeviceChange.NONE, app().deviceChangeAgainst(local(signingFingerprint = null)))
    }

    @Test
    fun aBlankSignerIsTreatedAsUnknownNotAsADifference() {
        assertEquals(DeviceChange.NONE, app(signingFingerprint = "").deviceChangeAgainst(local()))
        assertEquals(DeviceChange.NONE, app().deviceChangeAgainst(local(signingFingerprint = "   ")))
    }

    @Test
    fun signerCaseIsInsensitiveSoAReformattingIsNotADrift() {
        assertEquals(DeviceChange.NONE, app().deviceChangeAgainst(local(signingFingerprint = "aaaa")))
    }
}
