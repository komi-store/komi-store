package zed.rainxch.core.domain.utils

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.system.SystemArchitecture
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.InstallerInfoExtractor

/**
 * The shared resolution behind both the update check and a download started from the
 * library: which release the app tracks, and which asset of it is the app's own file.
 * The library download used to bypass all of this and pick the raw newest release's
 * asset; these tests pin the two decisions it must inherit with it.
 */
class TrackedReleaseResolverTest {

    private val installer = FakeInstaller()

    @Test
    fun theAssetFilterDecidesWhichAssetTheAppTracks() {
        val releases = listOf(release("v1.0", listOf("MyApp-1.0.apk", "MyApp-1.0-zeta.apk")))

        // Without a filter the pool's order decides; with one, only the matching asset
        // is a candidate at all.
        assertEquals("MyApp-1.0.apk", resolve(releases)?.primaryAsset?.name)
        assertEquals("MyApp-1.0-zeta.apk", resolve(releases, filter = "zeta")?.primaryAsset?.name)
    }

    @Test
    fun fallbackToOlderReleasesDecidesHowFarTheWindowReaches() {
        val releases =
            listOf(
                release("v2.0", listOf("Other-2.0.apk")),
                release("v1.9", listOf("MyApp-1.9.apk")),
            )

        // The newest release carries no asset for this app; without fallback the walk
        // stops at it and the app resolves to nothing.
        assertNull(resolve(releases, filter = "MyApp"))

        val walked = resolve(releases, filter = "MyApp", fallback = true)
        assertEquals("v1.9", walked?.release?.tagName)
        assertEquals("MyApp-1.9.apk", walked?.primaryAsset?.name)

        // Without the filter the newest release wins outright — the sibling's asset the
        // library download used to take: the shape the shared resolution corrects.
        assertEquals("Other-2.0.apk", resolve(releases)?.primaryAsset?.name)
    }

    private fun resolve(
        releases: List<GithubRelease>,
        filter: String? = null,
        fallback: Boolean = false,
        installedAssetName: String? = null,
    ): ResolvedRelease? {
        val self = app(installedAssetName = installedAssetName, filter = filter, fallback = fallback)
        return TrackedReleaseResolver.resolve(
            releases = releases,
            filter = AssetFilter.parse(filter)?.getOrNull(),
            fallbackToOlderReleases = fallback,
            preferredVariant = null,
            preferredTokens = emptySet(),
            preferredGlob = null,
            pickedIndex = null,
            pickedSiblingCount = null,
            trackedPackageName = self.packageName,
            installedAssetName = self.installedAssetName,
            repoApps = listOf(self),
            installer = installer,
        )
    }

    private fun app(
        installedAssetName: String? = null,
        filter: String? = null,
        fallback: Boolean = false,
    ): InstalledApp =
        InstalledApp(
            packageName = "com.example.myapp",
            repoId = 1L,
            repoName = "myapp",
            repoOwner = "example",
            repoOwnerAvatarUrl = "",
            repoDescription = null,
            primaryLanguage = null,
            repoUrl = "https://github.com/example/myapp",
            installedVersion = "v1.0",
            installedAssetName = installedAssetName,
            installedAssetUrl = null,
            latestVersion = null,
            latestAssetName = null,
            latestAssetUrl = null,
            latestAssetSize = null,
            appName = "MyApp",
            installSource = InstallSource.MANUAL,
            installedAt = 0L,
            lastCheckedAt = 0L,
            lastUpdatedAt = 0L,
            isUpdateAvailable = false,
            signingFingerprint = null,
            systemArchitecture = "arm64-v8a",
            fileExtension = "apk",
            assetFilterRegex = filter,
            fallbackToOlderReleases = fallback,
        )

    private fun release(tag: String, assetNames: List<String>): GithubRelease =
        GithubRelease(
            id = tag.hashCode().toLong(),
            tagName = tag,
            name = tag,
            publishedAt = "2026-01-01T00:00:00Z",
            description = null,
            assets = assetNames.map(::asset),
            tarballUrl = "",
            zipballUrl = "",
            htmlUrl = "",
            isPrerelease = false,
        )

    private fun asset(name: String): GithubAsset =
        GithubAsset(
            id = name.hashCode().toLong(),
            name = name,
            contentType = "application/octet-stream",
            size = 1L,
            downloadUrl = "https://example.invalid/$name",
        )

    private class FakeInstaller : Installer {
        override suspend fun isSupported(extOrMime: String): Boolean = false

        override suspend fun ensurePermissionsOrThrow(extOrMime: String) = error("not used")

        override suspend fun install(
            filePath: String,
            extOrMime: String,
        ): InstallOutcome = error("not used")

        override fun uninstall(packageName: String) = error("not used")

        override fun isAssetInstallable(assetName: String): Boolean =
            assetName.endsWith(".apk", ignoreCase = true)

        // The real installer picks by architecture; the tests pin the pool's order
        // instead, so a pool that lost or reordered its candidates shows up as a pick.
        override fun choosePrimaryAsset(assets: List<GithubAsset>): GithubAsset? = assets.firstOrNull()

        override fun detectSystemArchitecture(): SystemArchitecture = error("not used")

        override fun isObtainiumInstalled(): Boolean = false

        override fun openInObtainium(
            repoOwner: String,
            repoName: String,
            onOpenInstaller: () -> Unit,
        ) = error("not used")

        override fun isAppManagerInstalled(): Boolean = false

        override fun openInAppManager(
            filePath: String,
            onOpenInstaller: () -> Unit,
        ) = error("not used")

        override fun getApkInfoExtractor(): InstallerInfoExtractor = error("not used")

        override fun openApp(packageName: String): Boolean = false

        override fun openWithExternalInstaller(filePath: String) = error("not used")
    }
}
