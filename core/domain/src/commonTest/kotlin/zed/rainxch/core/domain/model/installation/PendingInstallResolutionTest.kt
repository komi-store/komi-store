package zed.rainxch.core.domain.model.installation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// The arm-by-arm coverage for the predicate every resolver shares: no answer, below the target,
// at or beyond the target, and the code-less fallback that a tag-tracked release needs or its
// park could never converge.
class PendingInstallResolutionTest {

    @Test
    fun noSystemAnswerKeepsThePark() {
        assertEquals(
            PendingInstallResolution.Keep,
            parked().pendingInstallResolution(null),
        )
    }

    @Test
    fun aSystemBelowTheTargetKeepsThePark() {
        assertEquals(
            PendingInstallResolution.Keep,
            parked().pendingInstallResolution(system("1.0.0", 100L)),
        )
    }

    @Test
    fun aMovedNameDoesNotOverrideACodeThatIsStillBelowTheTarget() {
        // The row carries a code, so only the code decides: a sideload that raised the name while
        // the build stays older must not tear the park down.
        assertEquals(
            PendingInstallResolution.Keep,
            parked().pendingInstallResolution(system("2.0.0", 100L)),
        )
    }

    @Test
    fun theTargetOnTheSystemConverges() {
        val reached = parked().pendingInstallResolution(system("2.0.0", 200L))
            as? PendingInstallResolution.Reached
        assertNotNull(reached)
        assertEquals("2.0.0", reached.resolvedTag)
        assertEquals("2.0.0", reached.versionName)
        assertEquals(200L, reached.versionCode)
    }

    @Test
    fun aSystemAheadOfTheTargetConverges() {
        assertTrue(
            parked().pendingInstallResolution(system("3.0.0", 300L))
                is PendingInstallResolution.Reached,
        )
    }

    @Test
    fun theTagFallsBackToTheLatestVersionWhenTheParkCarriesNone() {
        // The system's name is deliberately different from the latest version: this reach is
        // decided by the code (200 >= 200), so a fallback that landed on the system name would
        // still resolve, and only a distinct value can prove which candidate actually won.
        val reached = parked(pendingInstallVersion = null)
            .pendingInstallResolution(system("2.5.0", 200L)) as? PendingInstallResolution.Reached
        assertNotNull(reached)
        assertEquals("2.0.0", reached.resolvedTag)
    }

    @Test
    fun aCodeLessTargetKeepsTheParkWhileTheNameIsUnchanged() {
        assertEquals(
            PendingInstallResolution.Keep,
            parked(latestVersionCode = null).pendingInstallResolution(system("1.0.0", 100L)),
        )
    }

    @Test
    fun aCodeLessTargetKeepsTheParkWhenTheSystemReportsNoName() {
        // The Android boundary's placeholder for a missing versionName is not a name that arrived.
        assertEquals(
            PendingInstallResolution.Keep,
            parked(latestVersionCode = null)
                .pendingInstallResolution(system(SystemPackageInfo.UNKNOWN_VERSION_NAME, 100L)),
        )
    }

    @Test
    fun aCodeLessTargetConvergesOnAMovedName() {
        val reached = parked(latestVersionCode = null)
            .pendingInstallResolution(system("2.0.0", 100L)) as? PendingInstallResolution.Reached
        assertNotNull(reached)
        assertEquals("2.0.0", reached.resolvedTag)
    }

    @Test
    fun aCodeLessTargetWithNoRecordedBaselineConvergesWhenThePackageAppears() {
        // A fresh install has no baseline to move off; the package appearing on the system at all
        // is the change the predicate goes by, or its park could never converge.
        val reached = parked(installedVersionName = null, latestVersionCode = null)
            .pendingInstallResolution(system("2.0.0", 100L)) as? PendingInstallResolution.Reached
        assertNotNull(reached)
        assertEquals("2.0.0", reached.resolvedTag)
    }

    @Test
    fun aCodeLessTargetConvergesWhenTheSystemMatchesTheParkedTarget() {
        // The post-install shape from the device pass: the confirmed install rewrote the baseline
        // to the target's name, so "moved" can no longer be seen, and a failed discard would never
        // retry. The system name matching the park's own target is the proof that keeps the retry
        // reachable.
        val reached = parked(
            installedVersionName = "1.6.17",
            latestVersionCode = null,
            pendingInstallVersion = "v1.6.17",
        ).pendingInstallResolution(system("1.6.17", 100L)) as? PendingInstallResolution.Reached
        assertNotNull(reached)
    }

    @Test
    fun aCodeLessTargetWithABlankNameKeepsThePark() {
        assertEquals(
            PendingInstallResolution.Keep,
            parked(latestVersionCode = null).pendingInstallResolution(system("", 100L)),
        )
    }

    private fun system(versionName: String, versionCode: Long): SystemPackageInfo =
        SystemPackageInfo(
            packageName = "com.example.app",
            versionName = versionName,
            versionCode = versionCode,
            isInstalled = true,
            signingFingerprint = "SHA",
        )

    private fun parked(
        installedVersionName: String? = "1.0.0",
        latestVersion: String? = "2.0.0",
        latestVersionCode: Long? = 200L,
        pendingInstallVersion: String? = "2.0.0",
    ): InstalledApp = testInstalledApp(
        installedVersionName = installedVersionName,
        latestVersion = latestVersion,
        latestVersionCode = latestVersionCode,
        isPendingInstall = true,
        pendingInstallVersion = pendingInstallVersion,
    )
}
