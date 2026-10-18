package zed.rainxch.core.data.services

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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

class InstallingDownloadWithMissingFileTest {
    private val dir = createTempDirectory("komi-missing-file").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun a_handed_off_install_whose_file_is_gone_downloads_again() =
        runBlocking {
            val source = FileWritingSource(dir)
            val orchestrator =
                DefaultDownloadOrchestrator(
                    downloader = DirDownloader(dir),
                    multiSourceDownloader = source,
                    digestVerifier = UnusedVerifier,
                    installer = HandOffInstaller,
                    installedAppsRepository = StubInstalledAppsRepository,
                    pendingInstallNotifier = StubNotifier,
                    slowDownloadDetector = StubSlowDownloadDetector,
                    appScope = this,
                    systemInstallSerializer = StubInstallSerializer,
                    tokenStore = FakeTokenStore(),
                )

            val firstId = orchestrator.enqueue(spec())
            awaitStage(orchestrator, DownloadStage.AwaitingInstall)
            orchestrator.installPending(PACKAGE)
            val handedOff = orchestrator.downloads.value.getValue(PACKAGE)
            assertEquals(DownloadStage.Installing, handedOff.stage)

            File(handedOff.filePath!!).delete()
            val secondId = orchestrator.enqueue(spec())

            assertNotEquals(firstId, secondId)
            awaitStage(orchestrator, DownloadStage.AwaitingInstall)
            assertEquals(2, source.calls)
        }

    private suspend fun awaitStage(orchestrator: DefaultDownloadOrchestrator, stage: DownloadStage) {
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
        )

    private class FileWritingSource(private val dir: File) : MultiSourceDownloader {
        var calls = 0

        override fun download(
            githubUrl: String,
            suggestedFileName: String?,
            identity: AssetIdentity?,
        ): Flow<DownloadProgress> =
            flow {
                calls++
                File(dir, suggestedFileName!!).writeBytes(byteArrayOf(1, 2, 3, 4))
                emit(DownloadProgress(bytesDownloaded = 4L, totalBytes = 4L, percent = 100))
            }
    }

    private class DirDownloader(private val dir: File) : Downloader {
        override fun download(
            url: String,
            suggestedFileName: String?,
            bypassMirror: Boolean,
            identity: AssetIdentity?,
        ): Flow<DownloadProgress> = error("the multi-source download does not fail here")

        override suspend fun saveToFile(url: String, suggestedFileName: String?): String = error("unused")

        override suspend fun getDownloadedFilePath(fileName: String): String? =
            File(dir, fileName).takeIf { it.exists() }?.absolutePath

        override suspend fun cancelDownload(fileName: String): Boolean = false

        override suspend fun discardPartial(fileName: String): Boolean = true

        override suspend fun reclaimOrphanedPartials(claimedNames: Set<String>): Int = 0
    }

    private object UnusedVerifier : DigestVerifier {
        override suspend fun verify(filePath: String, expectedDigest: String): String? =
            error("an asset without a digest is not verified")
    }

    private object HandOffInstaller : Installer {
        override suspend fun isSupported(extOrMime: String): Boolean = true

        override suspend fun ensurePermissionsOrThrow(extOrMime: String) = Unit

        override suspend fun install(filePath: String, extOrMime: String): InstallOutcome =
            InstallOutcome.DELEGATED_TO_SYSTEM

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

        override fun openInAppManager(filePath: String, onOpenInstaller: () -> Unit) = Unit

        override fun getApkInfoExtractor(): InstallerInfoExtractor = NoOpExtractor

        override fun openApp(packageName: String): Boolean = false

        override fun openWithExternalInstaller(filePath: String) = Unit
    }

    private object NoOpExtractor : InstallerInfoExtractor {
        override suspend fun extractPackageInfo(filePath: String): ApkPackageInfo? = null
    }

    private object StubInstalledAppsRepository : InstalledAppsRepository {
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

    private object StubSlowDownloadDetector : SlowDownloadDetector {
        override val suggestMirror: Flow<Unit> = emptyFlow()

        override suspend fun onProgress(progress: DownloadProgress) = Unit

        override suspend fun reset() = Unit
    }

    private object StubInstallSerializer : SystemInstallSerializer {
        override suspend fun awaitFreeAndMarkPending(packageName: String, timeoutMs: Long) = Unit

        override fun markCompleted(packageName: String) = Unit
    }

    private companion object {
        const val PACKAGE = "org.schabi.newpipe"
        const val AWAIT_MS = 10_000L
    }
}
