package zed.rainxch.core.data.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import zed.rainxch.core.data.data_source.TokenStore
import zed.rainxch.core.data.dto.GithubDeviceTokenSuccessDto
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.installation.DownloadProgress
import zed.rainxch.core.domain.model.installation.InstalledApp
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

class DefaultDownloadOrchestratorDigestRefetchTest {

    private val digest =
        "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    private fun spec(policy: InstallPolicy = InstallPolicy.DeferUntilUserAction) =
        DownloadSpec(
            packageName = "net.cozic.joplin",
            repoOwner = "laurent22",
            repoName = "joplin",
            asset =
                GithubAsset(
                    id = 42L,
                    name = "joplin-3.4.1.apk",
                    contentType = "application/vnd.android.package-archive",
                    size = 1_000L,
                    downloadUrl =
                        "https://github.com/laurent22/joplin/releases/download/v3.4.1/joplin-3.4.1.apk",
                    digest = digest,
                ),
            displayAppName = "Joplin",
            installPolicy = policy,
            releaseTag = "v3.4.1",
        )

    private fun orchestrator(
        downloader: Downloader,
        multi: MultiSourceDownloader,
        verifier: DigestVerifier,
        scope: CoroutineScope,
    ) = DefaultDownloadOrchestrator(
        downloader = downloader,
        multiSourceDownloader = multi,
        digestVerifier = verifier,
        installer = StubInstaller,
        installedAppsRepository = StubInstalledAppsRepository(),
        pendingInstallNotifier = StubNotifier,
        slowDownloadDetector = StubSlowDownloadDetector,
        appScope = scope,
        systemInstallSerializer = StubInstallSerializer,
        tokenStore = StubTokenStore,
    )

    @Test
    fun bytes_that_fail_verification_are_refetched_from_the_source_and_the_download_proceeds() =
        runBlocking {
            val downloader = TrackingDownloader()
            val multi = TrackingMultiSource()
            val verifier = HandedVerifier("Digest mismatch (mirror bytes)", null)

            val orchestrator = orchestrator(downloader, multi, verifier, scope = this)
            orchestrator.enqueue(spec())

            val entry =
                withTimeout(AWAIT_MS) {
                    orchestrator.downloads.first {
                        it["net.cozic.joplin"]?.stage == DownloadStage.AwaitingInstall
                    }
                }["net.cozic.joplin"]!!

            assertEquals(1, multi.calls.size, "the first attempt goes through the multi-source path")
            assertEquals(1, downloader.calls.size, "exactly one direct attempt")
            assertTrue(downloader.calls.single().bypassMirror, "the re-fetch must bypass the mirror")
            assertEquals(1, downloader.discardedPartials.size, "the partial is cleared before re-fetching")
            assertEquals(2, verifier.calls.size, "the mirror bytes and the source bytes are both checked")
            assertEquals(DownloadStage.AwaitingInstall, entry.stage)
        }

    @Test
    fun a_source_refetch_that_still_mismatches_is_an_integrity_failure() = runBlocking {
        val downloader = TrackingDownloader()
        val multi = TrackingMultiSource()
        val verifier = HandedVerifier("Digest mismatch (mirror bytes)", "still mismatched")

        val orchestrator = orchestrator(downloader, multi, verifier, scope = this)
        orchestrator.enqueue(spec())

        val entry =
            withTimeout(AWAIT_MS) {
                orchestrator.downloads.first {
                    it["net.cozic.joplin"]?.stage == DownloadStage.Failed
                }
            }["net.cozic.joplin"]!!

        assertEquals(1, downloader.calls.size, "the source is tried exactly once")
        assertEquals(2, verifier.calls.size)
        assertEquals("Checksum mismatch — file may have been tampered with", entry.errorMessage)
    }

    private companion object {
        const val AWAIT_MS = 10_000L
    }

    private class TrackingDownloader : Downloader {
        data class Call(val url: String, val bypassMirror: Boolean)

        val calls = mutableListOf<Call>()
        val discardedPartials = mutableListOf<String>()

        override fun download(
            url: String,
            suggestedFileName: String?,
            bypassMirror: Boolean,
            identity: AssetIdentity?,
        ): Flow<DownloadProgress> {
            calls += Call(url, bypassMirror)
            return flowOf(
                DownloadProgress(bytesDownloaded = 1_000L, totalBytes = 1_000L, percent = 100),
            )
        }

        override suspend fun saveToFile(url: String, suggestedFileName: String?): String =
            error("saveToFile is not part of this path")

        override suspend fun getDownloadedFilePath(fileName: String): String =
            "/tmp/komi-refetch-test/$fileName"

        override suspend fun cancelDownload(fileName: String): Boolean = false

        override suspend fun discardPartial(fileName: String): Boolean {
            discardedPartials += fileName
            return true
        }

        override suspend fun reclaimOrphanedPartials(claimedNames: Set<String>): Int = 0
    }

    private class TrackingMultiSource : MultiSourceDownloader {
        val calls = mutableListOf<String>()

        override fun download(
            githubUrl: String,
            suggestedFileName: String?,
            identity: AssetIdentity?,
        ): Flow<DownloadProgress> {
            calls += githubUrl
            return flowOf(
                DownloadProgress(bytesDownloaded = 1_000L, totalBytes = 1_000L, percent = 100),
            )
        }
    }

    private class HandedVerifier(vararg results: String?) : DigestVerifier {
        private val queue = ArrayDeque(results.toList())
        val calls = mutableListOf<String>()

        override suspend fun verify(filePath: String, expectedDigest: String): String? {
            calls += filePath
            return queue.removeFirstOrNull()
        }
    }

    private object StubInstaller : Installer {
        override suspend fun isSupported(extOrMime: String): Boolean = false

        override suspend fun ensurePermissionsOrThrow(extOrMime: String) =
            error("a deferred download must not install")

        override suspend fun install(filePath: String, extOrMime: String): InstallOutcome =
            error("a deferred download must not install")

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

        override fun getApkInfoExtractor(): InstallerInfoExtractor = NoOpExtractor

        override fun openApp(packageName: String): Boolean = false

        override fun openWithExternalInstaller(filePath: String) = Unit
    }

    private object NoOpExtractor : InstallerInfoExtractor {
        override suspend fun extractPackageInfo(filePath: String): ApkPackageInfo? = null
    }

    private class StubInstalledAppsRepository : InstalledAppsRepository {
        override suspend fun setPendingInstallFilePath(
            packageName: String,
            path: String?,
            version: String?,
            assetName: String?,
        ) = Unit

        override fun getAllInstalledApps(): Flow<List<InstalledApp>> = emptyFlow()

        override fun getAppsWithUpdates(): Flow<List<InstalledApp>> = emptyFlow()

        override fun getUpdateCount(): Flow<Int> = flowOf(0)

        override suspend fun getAppByPackage(packageName: String): InstalledApp? = null

        override suspend fun getAppByRepoId(repoId: Long): InstalledApp? = null

        override fun getAppByRepoIdAsFlow(repoId: Long): Flow<InstalledApp?> = flowOf(null)

        override suspend fun getAppsByRepoId(repoId: Long): List<InstalledApp> = emptyList()

        override fun getAppsByRepoIdAsFlow(repoId: Long): Flow<List<InstalledApp>> = emptyFlow()

        override suspend fun isAppInstalled(repoId: Long): Boolean = false

        override suspend fun saveInstalledApp(app: InstalledApp) = Unit

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
        ): MatchingPreview = error("previewMatchingAssets is not part of this path")

        override suspend fun <R> executeInTransaction(block: suspend () -> R): R = block()
    }

    private object StubNotifier : PendingInstallNotifier {
        override fun notifyPending(
            packageName: String,
            repoOwner: String,
            repoName: String,
            appName: String,
            versionTag: String,
        ) = Unit

        override fun clearPending(packageName: String) = Unit
    }

    private object StubSlowDownloadDetector : SlowDownloadDetector {
        override val suggestMirror: Flow<Unit> = emptyFlow()

        override suspend fun onProgress(progress: DownloadProgress) = Unit

        override suspend fun reset() = Unit
    }

    private object StubInstallSerializer : SystemInstallSerializer {
        override suspend fun awaitFreeAndMarkPending(packageName: String, timeoutMs: Long) = Unit

        override fun markCompleted(packageName: String) = Unit
    }

    private object StubTokenStore : TokenStore {
        override fun tokenFlow(): Flow<GithubDeviceTokenSuccessDto?> = emptyFlow()

        override suspend fun currentToken(): GithubDeviceTokenSuccessDto? = null

        override fun blockingCurrentToken(): GithubDeviceTokenSuccessDto? = null

        override suspend fun save(token: GithubDeviceTokenSuccessDto) = Unit

        override suspend fun clear() = Unit

        override suspend fun isTokenExpired(): Boolean = false
    }
}
