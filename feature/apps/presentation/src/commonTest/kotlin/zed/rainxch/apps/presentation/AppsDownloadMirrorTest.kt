package zed.rainxch.apps.presentation

import zed.rainxch.apps.presentation.model.UpdateState
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.InstallPolicy
import zed.rainxch.core.domain.system.OrchestratedDownload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppsDownloadMirrorTest {
    private fun entry(
        key: String,
        stage: DownloadStage,
        error: String? = null,
    ) = OrchestratedDownload(
        id = key,
        packageName = key,
        repoOwner = "owner",
        repoName = "name",
        displayAppName = "Name",
        assetName = "name.apk",
        assetSize = 10L,
        downloadUrl = "https://example.invalid/name.apk",
        releaseTag = "v1",
        filePath = null,
        installPolicy = InstallPolicy.DeferUntilUserAction,
        stage = stage,
        progressPercent = null,
        errorMessage = error,
    )

    @Test
    fun aDownloadTheLibraryHasNoRowForBecomesATransientCard() {
        val snapshot = mapOf("owner/name" to entry("owner/name", DownloadStage.Downloading))

        val transient = transientDownloads(snapshot, knownPackages = setOf("com.other"))

        assertEquals(listOf("owner/name"), transient.map { it.packageName })
    }

    @Test
    fun aDownloadWithARowMovesToTheCardToo() {
        val snapshot = mapOf("com.app" to entry("com.app", DownloadStage.Downloading))

        assertEquals(
            listOf("com.app"),
            transientDownloads(snapshot, knownPackages = setOf("com.app")).map { it.packageName },
        )
    }

    @Test
    fun aParkedDownloadWithARowStaysOnItsRowInsteadOfACard() {
        val snapshot = mapOf("com.app" to entry("com.app", DownloadStage.AwaitingInstall))

        assertTrue(transientDownloads(snapshot, knownPackages = setOf("com.app")).isEmpty())

        val fresh = mapOf("owner/name" to entry("owner/name", DownloadStage.AwaitingInstall))
        assertEquals(1, transientDownloads(fresh, knownPackages = emptySet()).size)
    }

    @Test
    fun aPausedDownloadIsACardForRowAndRowlessAlike() {
        val withRow = mapOf("com.app" to entry("com.app", DownloadStage.Paused))
        assertEquals(1, transientDownloads(withRow, knownPackages = setOf("com.app")).size)

        val rowless = mapOf("owner/name" to entry("owner/name", DownloadStage.Paused))
        assertEquals(1, transientDownloads(rowless, knownPackages = emptySet()).size)
    }

    @Test
    fun aSettledDownloadIsNotACard() {
        val snapshot =
            mapOf(
                "owner/name" to entry("owner/name", DownloadStage.Completed),
                "owner/second" to entry("owner/second", DownloadStage.Cancelled),
            )

        assertTrue(transientDownloads(snapshot, emptySet()).isEmpty())
    }

    @Test
    fun theDefaultCardStagesAreTheProductionSetItself() {
        val paused = mapOf("owner/name" to entry("owner/name", DownloadStage.Paused))
        assertEquals(1, transientDownloads(paused, knownPackages = emptySet()).size)

        val failed = mapOf("owner/name" to entry("owner/name", DownloadStage.Failed))
        assertEquals(1, transientDownloads(failed, knownPackages = emptySet()).size)

        val terminal =
            mapOf(
                "owner/name" to entry("owner/name", DownloadStage.Completed),
                "owner/second" to entry("owner/second", DownloadStage.Cancelled),
            )
        assertTrue(transientDownloads(terminal, knownPackages = emptySet()).isEmpty())
    }

    @Test
    fun rowsAreHiddenExactlyWhenTheirDownloadIsOnACard() {
        val known = setOf("com.app", "com.parked")
        val snapshot =
            mapOf(
                "com.app" to entry("com.app", DownloadStage.Downloading),
                "com.parked" to entry("com.parked", DownloadStage.AwaitingInstall),
                "owner/name" to entry("owner/name", DownloadStage.Downloading),
            )

        assertEquals(setOf("com.app"), hiddenRowPackages(snapshot, known))
    }

    @Test
    fun aRowAlreadyBackInAGroupIsNotHidden() {
        val known = setOf("com.app")
        val settled =
            mapOf(
                "com.app" to entry("com.app", DownloadStage.Completed),
            )
        assertTrue(hiddenRowPackages(settled, known).isEmpty())
    }

    @Test
    fun theVersionLineWarnsOnlyWhenTheDownloadGoesBackwards() {
        assertTrue(isVersionDowngrade(installedVersion = "v0.9.0", targetVersion = "v0.8.5"))
        assertFalse(isVersionDowngrade(installedVersion = "v0.9.0", targetVersion = "v0.9.1"))
        assertFalse(isVersionDowngrade(installedVersion = "v0.9.0", targetVersion = "v0.9.0"))
    }

    @Test
    fun aFreshInstallHasNoInstalledVersionSoItIsNeverADowngrade() {
        assertFalse(isVersionDowngrade(installedVersion = null, targetVersion = "v0.8.5"))
        assertFalse(isVersionDowngrade(installedVersion = "", targetVersion = "v0.8.5"))
    }

    @Test
    fun aFailureKeepsItsOwnMessageAndFallsBackWhenItHasNone() {
        assertEquals(
            UpdateState.Error("disk full"),
            mirroredUpdateState(DownloadStage.Failed, errorMessage = "disk full", failedFallback = "fallback"),
        )
        assertEquals(
            UpdateState.Error("fallback"),
            mirroredUpdateState(DownloadStage.Failed, errorMessage = null, failedFallback = "fallback"),
        )
    }

    @Test
    fun aParkedDownloadLeavesTheRowIdleSoTheLibraryGroupCanOfferIt() {
        assertEquals(
            UpdateState.Idle,
            mirroredUpdateState(DownloadStage.AwaitingInstall, errorMessage = null, failedFallback = "f"),
        )
        assertEquals(
            UpdateState.Idle,
            mirroredUpdateState(DownloadStage.Completed, errorMessage = null, failedFallback = "f"),
        )
    }

    @Test
    fun liveAndInstallingStagesKeepTheirOwnRowStates() {
        assertEquals(
            UpdateState.Downloading,
            mirroredUpdateState(DownloadStage.Queued, errorMessage = null, failedFallback = "f"),
        )
        assertEquals(
            UpdateState.Downloading,
            mirroredUpdateState(DownloadStage.Downloading, errorMessage = null, failedFallback = "f"),
        )
        assertEquals(
            UpdateState.Installing,
            mirroredUpdateState(DownloadStage.Installing, errorMessage = null, failedFallback = "f"),
        )
    }

    @Test
    fun anUpdateIsRecordedAsTheReleaseTheOrchestratorDownloaded() {
        val recovered =
            entry("com.app", DownloadStage.AwaitingInstall).copy(
                assetId = 43L,
                assetDigest = "sha256:new",
                downloadUrl = "https://example.invalid/v2/name.apk",
                releaseTag = "v2",
                releaseId = 99L,
            )

        val marked = installedApp().markDownloadedUpdate(recovered, versionName = null, versionCode = null)

        assertTrue(marked.isPendingInstall)
        assertEquals(99L, marked.pendingInstallReleaseId)
        assertEquals(43L, marked.pendingInstallAssetId)
        assertEquals("sha256:new", marked.pendingInstallAssetDigest)
        assertEquals("v2", marked.latestVersion)
        assertEquals("https://example.invalid/v2/name.apk", marked.latestAssetUrl)
        assertEquals("v2", marked.latestVersionName)
    }

    private fun installedApp() =
        InstalledApp(
            packageName = "com.app",
            repoId = 1L,
            repoName = "name",
            repoOwner = "owner",
            repoOwnerAvatarUrl = "",
            repoDescription = null,
            primaryLanguage = null,
            repoUrl = "https://example.invalid/owner/name",
            installedVersion = "v1",
            installedAssetName = "name.apk",
            installedAssetUrl = null,
            latestVersion = "v1.99",
            latestAssetName = "name.apk",
            latestAssetUrl = "https://example.invalid/v1.99/name.apk",
            latestAssetSize = 10L,
            appName = "Name",
            installSource = InstallSource.THIS_APP,
            installedAt = 0L,
            lastCheckedAt = 0L,
            lastUpdatedAt = 0L,
            isUpdateAvailable = true,
            signingFingerprint = null,
            systemArchitecture = "arm64-v8a",
            fileExtension = "apk",
        )
}
