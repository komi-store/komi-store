package zed.rainxch.core.data.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import zed.rainxch.core.data.data_source.TokenStore
import zed.rainxch.core.data.dto.GithubDeviceTokenSuccessDto
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.installation.DownloadProgress
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.ParkedInstallDisposal
import zed.rainxch.core.domain.model.smart_detect.MatchingPreview
import zed.rainxch.core.domain.model.system.SystemArchitecture
import zed.rainxch.core.domain.network.AssetIdentity
import zed.rainxch.core.domain.network.DigestVerifier
import zed.rainxch.core.domain.network.Downloader
import zed.rainxch.core.domain.network.SlowDownloadDetector
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.DownloadSpec
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.InstallPolicy
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.InstallerInfoExtractor
import zed.rainxch.core.domain.system.MultiSourceDownloader
import zed.rainxch.core.domain.system.PendingInstallNotifier
import zed.rainxch.core.domain.system.SystemInstallSerializer

// The library offers an install only when isPendingInstall and pendingInstallFilePath are both
// set, so a row with one and not the other appears in no group. Through this boundary the
// orchestrator can only be pinned to the combined write rather than the path-only one; that the
// two columns land in a single statement is a property of InstalledAppDao.markAwaitingInstall's
// UPDATE and is not observable from here.
class DefaultDownloadOrchestratorParkTest {

    // Only the string flows on: FakeDownloader returns it from getDownloadedFilePath and no test
    // touches the file itself, so a real temp file would buy nothing and outlive the run.
    private val parkedPath: String = "/tmp/komi-orchestrator-park-test.apk"

    private fun spec(policy: InstallPolicy) = DownloadSpec(
        packageName = "net.cozic.joplin",
        repoOwner = "laurent22",
        repoName = "joplin",
        asset = GithubAsset(
            id = 42L,
            name = "joplin-3.4.1.apk",
            contentType = "application/vnd.android.package-archive",
            size = 1_000L,
            downloadUrl = "https://github.com/laurent22/joplin/releases/download/v3.4.1/joplin-3.4.1.apk",
            digest = null,
        ),
        displayAppName = "Joplin",
        installPolicy = policy,
        releaseTag = "v3.4.1",
    )

    private fun orchestrator(
        repository: InstalledAppsRepository,
        scope: CoroutineScope,
        notifier: PendingInstallNotifier = RecordingPendingInstallNotifier(),
        installer: Installer = FakeInstaller,
        serializer: SystemInstallSerializer = FakeSystemInstallSerializer,
    ) = DefaultDownloadOrchestrator(
        downloader = FakeDownloader(parkedPath),
        multiSourceDownloader = FakeMultiSourceDownloader,
        digestVerifier = FakeDigestVerifier,
        installer = installer,
        installedAppsRepository = repository,
        pendingInstallNotifier = notifier,
        slowDownloadDetector = FakeSlowDownloadDetector,
        appScope = scope,
        systemInstallSerializer = serializer,
        tokenStore = FakeTokenStore,
    )

    @Test
    fun parking_a_finished_download_goes_through_the_combined_write_only() = runBlocking {
        val repository = RecordingInstalledAppsRepository()
        val notifier = RecordingPendingInstallNotifier()
        val orchestrator = orchestrator(repository, scope = this, notifier = notifier)

        orchestrator.enqueue(spec(InstallPolicy.InstallWhileForeground))
        withTimeout(AWAIT_PARK_MS) { repository.parked.await() }

        assertEquals(1, repository.awaitingInstallWrites.size)
        val write = repository.awaitingInstallWrites.single()
        assertEquals("net.cozic.joplin", write.packageName)
        assertEquals(parkedPath, write.path)
        assertEquals("v3.4.1", write.version)
        assertEquals("joplin-3.4.1.apk", write.assetName)

        // Counter-proof: the path-only write is the one that cannot raise the flag.
        assertTrue(
            repository.pendingInstallFilePathWrites.isEmpty(),
            "parking must not go through the path-only write, got " +
                repository.pendingInstallFilePathWrites,
        )
        assertTrue(
            repository.discardedParks.isEmpty(),
            "parking must not discard the park, got " + repository.discardedParks,
        )

        // InstallWhileForeground parks silently: the user is already looking at the screen.
        assertTrue(
            notifier.notified.isEmpty(),
            "this policy must not notify, got " + notifier.notified,
        )
    }

    @Test
    fun the_download_only_policy_parks_the_same_way() = runBlocking {
        val repository = RecordingInstalledAppsRepository()
        val notifier = RecordingPendingInstallNotifier()
        val orchestrator = orchestrator(repository, scope = this, notifier = notifier)

        orchestrator.enqueue(spec(InstallPolicy.DeferUntilUserAction))
        withTimeout(AWAIT_PARK_MS) { repository.parked.await() }

        assertEquals(listOf(parkedPath), repository.awaitingInstallWrites.map { it.path })
        assertTrue(repository.pendingInstallFilePathWrites.isEmpty())
        assertTrue(
            repository.discardedParks.isEmpty(),
            "the download-only policy must not discard the park, got " + repository.discardedParks,
        )

        // The one real difference between the two policies, and the reason the flags cannot be
        // swapped: a deferred park has to tell the user the file is waiting.
        assertEquals(listOf("net.cozic.joplin"), notifier.notified)
    }

    @Test
    fun a_delegated_install_settles_the_card_and_adopts_the_untracked_app() = runBlocking {
        val repository = RecordingInstalledAppsRepository()
        val orchestrator =
            orchestrator(
                repository,
                scope = this,
                installer = SettlingInstaller(InstallOutcome.DELEGATED_TO_SYSTEM),
                serializer = SettledSystemInstallSerializer,
            )

        orchestrator.enqueue(
            spec(InstallPolicy.DeferUntilUserAction).copy(repoId = 4242L, sourceHost = "github.com"),
        )
        withTimeout(AWAIT_PARK_MS) { repository.parked.await() }

        orchestrator.installPending("net.cozic.joplin")

        // The card is done: a transfer handed to the system settles, instead of spinning on
        // Installing with no event left to move it.
        val entry = orchestrator.downloads.value.getValue("net.cozic.joplin")
        assertEquals(DownloadStage.Completed, entry.stage)
        assertEquals(InstallOutcome.DELEGATED_TO_SYSTEM, entry.installOutcome)

        // And the untracked download is adopted: a pending row pointing at the parked file,
        // for the sync to resolve once the system proves the install landed.
        val adopted = repository.savedApps.single()
        assertEquals("net.cozic.joplin", adopted.packageName)
        assertEquals(4242L, adopted.repoId)
        assertEquals("github.com", adopted.sourceHost)
        assertTrue(adopted.isPendingInstall)
        assertEquals(parkedPath, adopted.pendingInstallFilePath)
        assertEquals("v3.4.1", adopted.installedVersion)
        assertEquals("joplin-3.4.1.apk", adopted.installedAssetName)
    }

    @Test
    fun a_tracked_app_is_not_adopted_again() = runBlocking {
        val repository = RecordingInstalledAppsRepository()
        repository.tracked = trackedRow()
        val orchestrator =
            orchestrator(
                repository,
                scope = this,
                installer = SettlingInstaller(InstallOutcome.DELEGATED_TO_SYSTEM),
                serializer = SettledSystemInstallSerializer,
            )

        orchestrator.enqueue(
            spec(InstallPolicy.DeferUntilUserAction).copy(repoId = 4242L, sourceHost = "github.com"),
        )
        withTimeout(AWAIT_PARK_MS) { repository.parked.await() }

        orchestrator.installPending("net.cozic.joplin")

        // The row already owns this app's bookkeeping; adoption must not duplicate it — while
        // the entry still settles exactly the same way.
        assertTrue(
            repository.savedApps.isEmpty(),
            "adoption must not run for a tracked app, saved " + repository.savedApps,
        )
        assertEquals(
            DownloadStage.Completed,
            orchestrator.downloads.value.getValue("net.cozic.joplin").stage,
        )
    }

    private fun trackedRow() =
        InstalledApp(
            packageName = "net.cozic.joplin",
            repoId = 4242L,
            repoName = "joplin",
            repoOwner = "laurent22",
            repoOwnerAvatarUrl = "",
            repoDescription = null,
            primaryLanguage = null,
            repoUrl = "https://github.com/laurent22/joplin",
            installedVersion = "v3.4.1",
            installedAssetName = "joplin-3.4.1.apk",
            installedAssetUrl = null,
            latestVersion = null,
            latestAssetName = null,
            latestAssetUrl = null,
            latestAssetSize = null,
            appName = "Joplin",
            installSource = InstallSource.THIS_APP,
            installedAt = 0L,
            lastCheckedAt = 0L,
            lastUpdatedAt = 0L,
            isUpdateAvailable = false,
            signingFingerprint = null,
            systemArchitecture = "UNKNOWN",
            fileExtension = "apk",
        )

    private companion object {
        const val AWAIT_PARK_MS = 10_000L
    }

    private data class ParkWrite(
        val packageName: String,
        val path: String,
        val version: String?,
        val assetName: String?,
    )

    private class RecordingInstalledAppsRepository : InstalledAppsRepository {
        val awaitingInstallWrites = mutableListOf<ParkWrite>()
        val pendingInstallFilePathWrites = mutableListOf<String?>()
        val savedApps = mutableListOf<InstalledApp>()
        val parked = CompletableDeferred<Unit>()

        // A discard is not expected on this path; recorded so a regression that clears the park
        // this way fails loudly.
        val discardedParks = mutableListOf<String>()

        override suspend fun markAwaitingInstall(
            packageName: String,
            path: String,
            version: String?,
            assetName: String?,
        ) {
            awaitingInstallWrites += ParkWrite(packageName, path, version, assetName)
            parked.complete(Unit)
        }

        override suspend fun setPendingInstallFilePath(
            packageName: String,
            path: String?,
            version: String?,
            assetName: String?,
        ) {
            pendingInstallFilePathWrites += path
        }

        // Stub: this fake covers the park write path, so a discard is not expected here. The
        // recording keeps both parameters so a path-scoping regression is distinguishable from a
        // plain spurious call.
        override suspend fun discardParkedInstall(
            packageName: String,
            expectedPath: String?,
        ): ParkedInstallDisposal {
            discardedParks += "$packageName (expected=$expectedPath)"
            return ParkedInstallDisposal.Discarded
        }

        override fun getAllInstalledApps(): Flow<List<InstalledApp>> = emptyFlow()

        override fun getAppsWithUpdates(): Flow<List<InstalledApp>> = emptyFlow()

        override fun getUpdateCount(): Flow<Int> = flowOf(0)

        // What the library already tracks, when a test says so. The park path never reads it;
        // adoption does, to leave a tracked app's bookkeeping to its own row.
        var tracked: InstalledApp? = null

        override suspend fun getAppByPackage(packageName: String): InstalledApp? = tracked

        override suspend fun getAppByRepoId(repoId: Long): InstalledApp? = null

        override fun getAppByRepoIdAsFlow(repoId: Long): Flow<InstalledApp?> = flowOf(null)

        override suspend fun getAppsByRepoId(repoId: Long): List<InstalledApp> = emptyList()

        override fun getAppsByRepoIdAsFlow(repoId: Long): Flow<List<InstalledApp>> = emptyFlow()

        override suspend fun isAppInstalled(repoId: Long): Boolean = false

        override suspend fun saveInstalledApp(app: InstalledApp) {
            savedApps += app
        }

        override suspend fun deleteInstalledApp(packageName: String) = Unit

        override suspend fun checkForUpdates(packageName: String): Boolean = false

        override suspend fun checkAllForUpdates() = Unit

        override suspend fun updateAppVersion(
            packageName: String,
            newTag: String,
            newReleaseId: Long?,
            newAssetId: Long?,
            newAssetDigest: String?,
            newAssetName: String?,
            newAssetUrl: String?,
            newVersionName: String,
            newVersionCode: Long,
            signingFingerprint: String?,
            isPendingInstall: Boolean,
        ) = Unit

        override suspend fun clearInstallBinding(packageName: String) = Unit

        override suspend fun updateApp(app: InstalledApp) = Unit

        override suspend fun updateInstalledVersion(
            packageName: String,
            installedVersion: String,
            installedVersionName: String?,
            installedVersionCode: Long,
            isUpdateAvailable: Boolean,
        ) = Unit

        override suspend fun updatePendingStatus(packageName: String, isPending: Boolean) = Unit

        override suspend fun setIncludePreReleases(packageName: String, enabled: Boolean) = Unit

        override suspend fun setUpdateCheckEnabled(packageName: String, enabled: Boolean) = Unit

        override suspend fun setAssetFilter(
            packageName: String,
            regex: String?,
            fallbackToOlderReleases: Boolean,
        ) = Unit

        override suspend fun setPreferredVariant(
            packageName: String,
            variant: String?,
            tokens: String?,
            glob: String?,
            pickedIndex: Int?,
            siblingCount: Int?,
        ) = Unit

        override suspend fun clearPreferredVariant(packageName: String) = Unit

        override suspend fun setSkippedReleaseTag(packageName: String, tag: String?) = Unit

        override fun getAppsWithSkippedReleaseTag(): Flow<List<InstalledApp>> = emptyFlow()

        override suspend fun previewMatchingAssets(
            owner: String,
            repo: String,
            regex: String?,
            includePreReleases: Boolean,
            fallbackToOlderReleases: Boolean,
        ): MatchingPreview = error("previewMatchingAssets is not part of the park path")

        override suspend fun <R> executeInTransaction(block: suspend () -> R): R = block()
    }

    private class FakeDownloader(private val filePath: String) : Downloader {
        override fun download(
            url: String,
            suggestedFileName: String?,
            bypassMirror: Boolean,
            identity: AssetIdentity?,
        ): Flow<DownloadProgress> =
            error("the single-source downloader is the fallback; the multi-source one succeeds")

        override suspend fun saveToFile(url: String, suggestedFileName: String?): String =
            error("saveToFile is not part of the park path")

        override suspend fun getDownloadedFilePath(fileName: String): String? = filePath

        override suspend fun cancelDownload(fileName: String): Boolean = false

        override suspend fun discardPartial(fileName: String): Boolean =
            error("the park path never discards a partial")

        override suspend fun reclaimOrphanedPartials(claimedNames: Set<String>): Int = 0
    }

    private object FakeMultiSourceDownloader : MultiSourceDownloader {
        override fun download(
            githubUrl: String,
            suggestedFileName: String?,
            identity: AssetIdentity?,
        ): Flow<DownloadProgress> =
            flowOf(
                DownloadProgress(bytesDownloaded = 1_000L, totalBytes = 1_000L, percent = 100),
            )
    }

    private object FakeDigestVerifier : DigestVerifier {
        override suspend fun verify(filePath: String, expectedDigest: String): String? = null
    }

    private object FakeInstaller : Installer {
        override suspend fun isSupported(extOrMime: String): Boolean = false

        override suspend fun ensurePermissionsOrThrow(extOrMime: String) =
            error("a parked policy must not install")

        override suspend fun install(filePath: String, extOrMime: String): InstallOutcome =
            error("a parked policy must not install")

        override fun uninstall(packageName: String) = Unit

        override fun isAssetInstallable(assetName: String): Boolean = false

        override fun choosePrimaryAsset(assets: List<GithubAsset>): GithubAsset? = null

        override fun detectSystemArchitecture(): SystemArchitecture = SystemArchitecture.UNKNOWN

        override fun isObtainiumInstalled(): Boolean = false

        override fun openInObtainium(
            repoOwner: String,
            repoName: String,
            onOpenInstaller: () -> Unit,
        ) = Unit

        override fun isAppManagerInstalled(): Boolean = false

        override fun openInAppManager(filePath: String, onOpenInstaller: () -> Unit) = Unit

        override fun getApkInfoExtractor(): InstallerInfoExtractor = NoOpInstallerInfoExtractor

        override fun openApp(packageName: String): Boolean = false

        override fun openWithExternalInstaller(filePath: String) = Unit
    }

    // A stand-in for the system installer on a stock device: it always hands the file over,
    // and everything it does not override delegates to FakeInstaller so the two cannot drift.
    private class SettlingInstaller(
        private val outcome: InstallOutcome,
    ) : Installer by FakeInstaller {
        override suspend fun ensurePermissionsOrThrow(extOrMime: String) = Unit

        override suspend fun install(filePath: String, extOrMime: String): InstallOutcome = outcome
    }

    // The install paths do serialize through the gate; this stand-in lets them, while
    // FakeSystemInstallSerializer keeps guarding the park path against serializing.
    private object SettledSystemInstallSerializer : SystemInstallSerializer {
        override suspend fun awaitFreeAndMarkPending(packageName: String, timeoutMs: Long) = Unit

        override fun markCompleted(packageName: String) = Unit
    }

    private object NoOpInstallerInfoExtractor : InstallerInfoExtractor {
        override suspend fun extractPackageInfo(filePath: String): ApkPackageInfo? = null
    }

    private class RecordingPendingInstallNotifier : PendingInstallNotifier {
        val notified = mutableListOf<String>()

        override fun notifyPending(
            packageName: String,
            repoOwner: String,
            repoName: String,
            appName: String,
            versionTag: String,
        ) {
            notified += packageName
        }

        override fun clearPending(packageName: String) = Unit
    }

    private object FakeSlowDownloadDetector : SlowDownloadDetector {
        override val suggestMirror: Flow<Unit> = emptyFlow()

        override suspend fun onProgress(progress: DownloadProgress) = Unit

        override suspend fun reset() = Unit
    }

    private object FakeSystemInstallSerializer : SystemInstallSerializer {
        override suspend fun awaitFreeAndMarkPending(packageName: String, timeoutMs: Long) =
            error("parkForUser must not serialize an install")

        override fun markCompleted(packageName: String) = Unit
    }

    private object FakeTokenStore : TokenStore {
        override fun tokenFlow(): Flow<GithubDeviceTokenSuccessDto?> = emptyFlow()

        override suspend fun currentToken(): GithubDeviceTokenSuccessDto? = null

        override fun blockingCurrentToken(): GithubDeviceTokenSuccessDto? = null

        override suspend fun save(token: GithubDeviceTokenSuccessDto) = Unit

        override suspend fun clear() = Unit

        override suspend fun isTokenExpired(): Boolean = false
    }
}
