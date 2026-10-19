package zed.rainxch.core.domain.utils

import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.InstalledApp
import kotlin.test.Test
import kotlin.test.assertEquals

class ExternalInstallVerdictTest {
    private fun app(
        latestVersion: String? = "nightly",
        latestVersionName: String? = "nightly",
        latestVersionCode: Long? = null,
    ): InstalledApp =
        InstalledApp(
            packageName = "com.example.app",
            repoId = 1L,
            repoName = "app",
            repoOwner = "owner",
            repoOwnerAvatarUrl = "https://avatar",
            repoDescription = null,
            primaryLanguage = "Kotlin",
            repoUrl = "https://github.com/owner/app",
            installedVersion = "nightly",
            installedAssetName = null,
            installedAssetUrl = null,
            latestVersion = latestVersion,
            latestAssetName = null,
            latestAssetUrl = null,
            latestAssetSize = null,
            appName = "App",
            installSource = InstallSource.THIS_APP,
            installedAt = 0L,
            lastCheckedAt = 0L,
            lastUpdatedAt = 0L,
            isUpdateAvailable = false,
            signingFingerprint = null,
            systemArchitecture = "arm64-v8a",
            fileExtension = "apk",
            installedVersionName = "26.09.01",
            installedVersionCode = 500L,
            latestVersionName = latestVersionName,
            latestVersionCode = latestVersionCode,
        )

    @Test
    fun a_nightly_is_not_called_outdated_by_its_own_tag() {
        assertEquals(
            VersionVerdict.UNKNOWN,
            resolveExternalInstallVerdict(
                app = app(),
                newVersionName = "26.09.01",
                newVersionCode = 501L,
            ),
        )
    }

    @Test
    fun two_comparable_version_names_are_still_decided() {
        assertEquals(
            VersionVerdict.UP_TO_DATE,
            resolveExternalInstallVerdict(
                app = app(latestVersion = "1.1.0", latestVersionName = "1.1.0"),
                newVersionName = "1.1.0",
                newVersionCode = 0L,
            ),
        )
        assertEquals(
            VersionVerdict.UPDATE_AVAILABLE,
            resolveExternalInstallVerdict(
                app = app(latestVersion = "1.1.0", latestVersionName = "1.1.0"),
                newVersionName = "1.0.0",
                newVersionCode = 0L,
            ),
        )
    }

    @Test
    fun codes_still_take_priority_when_both_sides_have_one() {
        assertEquals(
            VersionVerdict.UPDATE_AVAILABLE,
            resolveExternalInstallVerdict(
                app = app(latestVersion = "1.1.0", latestVersionName = "1.1.0", latestVersionCode = 110L),
                newVersionName = "1.0.0",
                newVersionCode = 100L,
            ),
        )
        assertEquals(
            VersionVerdict.UP_TO_DATE,
            resolveExternalInstallVerdict(
                app = app(latestVersion = "1.1.0", latestVersionName = "1.1.0", latestVersionCode = 110L),
                newVersionName = "1.1.0",
                newVersionCode = 110L,
            ),
        )
    }
}
