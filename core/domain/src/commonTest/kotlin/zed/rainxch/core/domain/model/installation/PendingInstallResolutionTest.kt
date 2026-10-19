package zed.rainxch.core.domain.model.installation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        assertEquals(
            PendingInstallResolution.Keep,
            parked().pendingInstallResolution(system("2.0.0", 100L)),
        )
    }

    @Test
    fun theTargetOnTheSystemConverges() {
        val reached =
            parked().pendingInstallResolution(system("2.0.0", 200L))
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
        val reached =
            parked(pendingInstallVersion = null)
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
        assertEquals(
            PendingInstallResolution.Keep,
            parked(latestVersionCode = null)
                .pendingInstallResolution(system(SystemPackageInfo.UNKNOWN_VERSION_NAME, 100L)),
        )
    }

    @Test
    fun aCodeLessTargetConvergesOnAMovedName() {
        val reached =
            parked(latestVersionCode = null)
                .pendingInstallResolution(system("2.0.0", 100L)) as? PendingInstallResolution.Reached
        assertNotNull(reached)
        assertEquals("2.0.0", reached.resolvedTag)
    }

    @Test
    fun aCodeLessTargetWithNoRecordedBaselineConvergesWhenThePackageAppears() {
        val reached =
            parked(installedVersionName = null, latestVersionCode = null)
                .pendingInstallResolution(system("2.0.0", 100L)) as? PendingInstallResolution.Reached
        assertNotNull(reached)
        assertEquals("2.0.0", reached.resolvedTag)
    }

    @Test
    fun aCodeLessTargetConvergesWhenTheSystemMatchesTheParkedTarget() {
        val reached =
            parked(
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

    private fun system(
        versionName: String,
        versionCode: Long,
    ): SystemPackageInfo =
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
    ): InstalledApp =
        testInstalledApp(
            installedVersionName = installedVersionName,
            latestVersion = latestVersion,
            latestVersionCode = latestVersionCode,
            isPendingInstall = true,
            pendingInstallVersion = pendingInstallVersion,
        )
}
