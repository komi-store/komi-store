package zed.rainxch.core.data.services

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import zed.rainxch.core.data.download.AssetSourceRefetcher
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.installation.DownloadProgress
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.ParkedInstallDisposal
import zed.rainxch.core.domain.model.smart_detect.MatchingPreview
import zed.rainxch.core.domain.model.system.SystemArchitecture
import zed.rainxch.core.domain.network.AssetIdentity
import zed.rainxch.core.domain.network.DigestVerifier
import zed.rainxch.core.domain.network.Downloader
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
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class DownloadLifecycleTest {
    private val dir = createTempDirectory("komi-lifecycle").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun a_handed_off_install_whose_file_is_gone_downloads_again() =
        runBlocking {
            val source = FileWritingSource(dir)
            val orchestrator = orchestrator(source)

            val firstId = orchestrator.enqueue(spec())
            awaitStage(orchestrator, DownloadStage.AwaitingInstall)
            orchestrator.installPending(PACKAGE)
            val handedOff = orchestrator.downloads.value.getValue(PACKAGE)
            assertEquals(DownloadStage.Completed, handedOff.stage)

            File(handedOff.filePath!!).delete()
            val secondId = orchestrator.enqueue(spec())

            assertNotEquals(firstId, secondId)
            awaitStage(orchestrator, DownloadStage.AwaitingInstall)
            assertEquals(2, source.calls)
        }

    @Test
    fun pause_keeps_the_download_and_resume_finishes_it() =
        runBlocking {
            val source = FileWritingSource(dir).apply { gate = CompletableDeferred() }
            val orchestrator = orchestrator(source)

            orchestrator.enqueue(spec())
            awaitStage(orchestrator, DownloadStage.Downloading)
            orchestrator.cancel(PACKAGE)
            assertEquals(DownloadStage.Paused, orchestrator.downloads.value[PACKAGE]?.stage)

            source.gate = null
            orchestrator.resume(PACKAGE)
            awaitStage(orchestrator, DownloadStage.AwaitingInstall)
            assertEquals(2, source.calls)
        }

    @Test
    fun cancelling_a_finished_download_removes_it_instead_of_pausing_it() =
        runBlocking {
            val orchestrator = orchestrator(FileWritingSource(dir))

            orchestrator.enqueue(spec())
            awaitStage(orchestrator, DownloadStage.AwaitingInstall)
            orchestrator.cancel(PACKAGE)

            assertNull(orchestrator.downloads.value[PACKAGE])
        }

    @Test
    fun delete_task_removes_a_finished_file() =
        runBlocking {
            val orchestrator = orchestrator(FileWritingSource(dir))

            orchestrator.enqueue(spec())
            awaitStage(orchestrator, DownloadStage.AwaitingInstall)
            val path =
                orchestrator.downloads.value
                    .getValue(PACKAGE)
                    .filePath!!
            orchestrator.discard(PACKAGE)

            assertNull(orchestrator.downloads.value[PACKAGE])
            assertFalse(File(path).exists())
        }

    @Test
    fun retry_finishes_a_failed_download_and_keeps_its_release() =
        runBlocking {
            val source = FileWritingSource(dir).apply { failNext = true }
            val orchestrator = orchestrator(source)

            orchestrator.enqueue(spec())
            awaitStage(orchestrator, DownloadStage.Failed)
            orchestrator.retry(PACKAGE)
            awaitStage(orchestrator, DownloadStage.AwaitingInstall)

            assertEquals(RELEASE_ID, orchestrator.downloads.value[PACKAGE]?.releaseId)
            assertEquals(2, source.calls)
        }

    private fun kotlinx.coroutines.CoroutineScope.orchestrator(source: FileWritingSource) =
        DefaultDownloadOrchestrator(
            downloader = DirDownloader(dir),
            multiSourceDownloader = source,
            assetSourceRefetcher = StubAssetSourceRefetcher,
            digestVerifier = UnusedVerifier,
            installer = HandOffInstaller,
            installedAppsRepository = StubInstalledAppsRepository,
            pendingInstallNotifier = StubNotifier,
            appScope = this,
            systemInstallSerializer = StubInstallSerializer,
            tokenStore = FakeTokenStore(),
        )

    private suspend fun awaitStage(
        orchestrator: DefaultDownloadOrchestrator,
        stage: DownloadStage,
    ) {
        withTimeout(AWAIT_MS) {
            orchestrator.downloads.first { it[PACKAGE]?.stage == stage }
        }
    }

    private fun spec() =
        DownloadSpec(
            packageName = PACKAGE,
            repoOwner = "TeamNewPipe",
            repoName = "NewPipe",
            asset =
                GithubAsset(
                    id = 7L,
                    name = "NewPipe_v0.29.1.apk",
                    contentType = "application/vnd.android.package-archive",
                    size = 4L,
                    downloadUrl =
                        "https://github.com/TeamNewPipe/NewPipe/releases/download/v0.29.1/NewPipe_v0.29.1.apk",
                ),
            displayAppName = "NewPipe",
            installPolicy = InstallPolicy.InstallWhileForeground,
            releaseTag = "v0.29.1",
            releaseId = RELEASE_ID,
        )

    private class FileWritingSource(
        private val dir: File,
    ) : MultiSourceDownloader {
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null
        var failNext = false

        override fun download(
            githubUrl: String,
            suggestedFileName: String?,
            identity: AssetIdentity?,
        ): Flow<DownloadProgress> =
            flow {
                calls++
                if (failNext) {
                    failNext = false
                    throw java.io.IOException("network dropped")
                }
                gate?.let { held ->
                    emit(DownloadProgress(bytesDownloaded = 1L, totalBytes = 4L, percent = 25))
                    held.await()
                }
                File(dir, suggestedFileName!!).writeBytes(byteArrayOf(1, 2, 3, 4))
                emit(DownloadProgress(bytesDownloaded = 4L, totalBytes = 4L, percent = 100))
            }
    }

    private class DirDownloader(
        private val dir: File,
    ) : Downloader {
        override fun download(
            url: String,
            suggestedFileName: String?,
            bypassMirror: Boolean,
            identity: AssetIdentity?,
        ): Flow<DownloadProgress> = throw java.io.IOException("no fallback source in this test")

        override suspend fun saveToFile(
            url: String,
            suggestedFileName: String?,
        ): String = error("unused")

        override suspend fun getDownloadedFilePath(fileName: String): String? = File(dir, fileName).takeIf { it.exists() }?.absolutePath

        override suspend fun cancelDownload(fileName: String): Boolean = false

        override suspend fun discardPartial(fileName: String): Boolean = true

        override suspend fun reclaimOrphanedPartials(claimedNames: Set<String>): Int = 0
    }

    private object UnusedVerifier : DigestVerifier {
        override suspend fun verify(
            filePath: String,
            expectedDigest: String,
        ): String? = error("an asset without a digest is not verified")

        override suspend fun computeSha256(filePath: String): String? = null
    }

    private object HandOffInstaller : Installer {
        override suspend fun isSupported(extOrMime: String): Boolean = true

        override suspend fun ensurePermissionsOrThrow(extOrMime: String) = Unit

        override suspend fun install(
            filePath: String,
            extOrMime: String,
        ): InstallOutcome = InstallOutcome.DELEGATED_TO_SYSTEM

        override fun uninstall(packageName: String) = Unit

        override fun isAssetInstallable(assetName: String): Boolean = true

        override fun choosePrimaryAsset(assets: List<GithubAsset>): GithubAsset? = null

        override fun detectSystemArchitecture(): SystemArchitecture = SystemArchitecture.UNKNOWN

        override fun isObtainiumInstalled(): Boolean = false

        override fun openInObtainium(
            repoOwner: String,
            repoName: String,
            onOpenInstaller: () -> Unit,
        ) = Unit

        override fun isAppManagerInstalled(): Boolean = false

        override fun openInAppManager(
            filePath: String,
            onOpenInstaller: () -> Unit,
        ) = Unit

        override fun getApkInfoExtractor(): InstallerInfoExtractor = NoOpExtractor

        override fun openApp(packageName: String): Boolean = false

        override fun openWithExternalInstaller(filePath: String) = Unit
    }

    private object NoOpExtractor : InstallerInfoExtractor {
        override suspend fun extractPackageInfo(filePath: String): ApkPackageInfo? = null
    }

    private object StubInstalledAppsRepository : InstalledAppsRepository {
        override suspend fun checkForUpdatesWithReleases(
            packageName: String,
            releases: List<zed.rainxch.core.domain.model.account.github.GithubRelease>,
        ): Boolean = false

        override suspend fun resolveTrackedRelease(packageName: String): zed.rainxch.core.domain.utils.ResolvedRelease? = null

        override suspend fun setPendingInstallFilePath(
            packageName: String,
            path: String?,
            version: String?,
            assetName: String?,
        ) = Unit

        override suspend fun markAwaitingInstall(
            packageName: String,
            path: String,
            version: String?,
            assetName: String?,
            releaseId: Long?,
            assetId: Long?,
            releasePublishedAt: String?,
            assetDigest: String?,
        ) = Unit

        override suspend fun discardParkedInstall(
            packageName: String,
            expectedPath: String?,
        ) = ParkedInstallDisposal.Discarded

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
            newReleasePublishedAt: String?,
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

        override suspend fun updateSigningFingerprint(
            packageName: String,
            signingFingerprint: String,
        ) = Unit

        override suspend fun updatePendingStatus(
            packageName: String,
            isPending: Boolean,
        ) = Unit

        override suspend fun setIncludePreReleases(
            packageName: String,
            enabled: Boolean,
        ) = Unit

        override suspend fun setUpdateCheckEnabled(
            packageName: String,
            enabled: Boolean,
        ) = Unit

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

        override suspend fun setSkippedReleaseTag(
            packageName: String,
            tag: String?,
        ) = Unit

        override fun getAppsWithSkippedReleaseTag(): Flow<List<InstalledApp>> = emptyFlow()

        override suspend fun previewMatchingAssets(
            owner: String,
            repo: String,
            regex: String?,
            includePreReleases: Boolean,
            fallbackToOlderReleases: Boolean,
        ): MatchingPreview = error("unused")

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

    private object StubInstallSerializer : SystemInstallSerializer {
        override suspend fun awaitFreeAndMarkPending(
            packageName: String,
            timeoutMs: Long,
        ) = Unit

        override fun markCompleted(packageName: String) = Unit

        override fun onAppResumed() = Unit
    }

    private object StubAssetSourceRefetcher : AssetSourceRefetcher {
        override suspend fun refetch(spec: DownloadSpec): DownloadSpec? = null
    }

    private companion object {
        const val PACKAGE = "org.schabi.newpipe"
        const val AWAIT_MS = 10_000L
        const val RELEASE_ID = 99L
    }
}
