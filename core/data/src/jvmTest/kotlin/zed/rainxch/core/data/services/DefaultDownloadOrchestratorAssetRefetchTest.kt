package zed.rainxch.core.data.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import zed.rainxch.core.data.data_source.TokenStore
import zed.rainxch.core.data.download.AssetSourceGoneException
import zed.rainxch.core.data.download.AssetSourceRefetcher
import zed.rainxch.core.data.download.assetReplacementFor
import zed.rainxch.core.data.dto.GithubDeviceTokenSuccessDto
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.installation.DownloadProgress
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.smart_detect.MatchingPreview
import zed.rainxch.core.domain.model.system.SystemArchitecture
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

// A download that was resolved from a cached release can be handed an asset URL that no longer
// exists: the release was replaced, or the asset re-uploaded, since the resolution was taken.
// These tests pin the recovery: the repository host is asked once for where the asset lives now,
// the download repeats there, and everything downstream — the digest check, the parked record,
// the notification — speaks about the replacement, not the dead URL. When nothing moved, the
// failure stands exactly as it did before.
class DefaultDownloadOrchestratorAssetRefetchTest {

    private companion object {
        const val PKG = "zed.rainxch.githubstore"
        const val OLD_TAG = "testbuild-all-prs-20261008"
        const val NEW_TAG = "testbuild-all-prs-20261009"
        const val OLD_URL =
            "https://github.com/illumiat/komi-store/releases/download/$OLD_TAG/composeApp-debug.apk"
        const val NEW_URL =
            "https://github.com/illumiat/komi-store/releases/download/$NEW_TAG/composeApp-debug.apk"
        const val OLD_DIGEST =
            "sha256:1111111111111111111111111111111111111111111111111111111111111111"
        const val NEW_DIGEST =
            "sha256:2222222222222222222222222222222222222222222222222222222222222222"
        const val AWAIT_MS = 10_000L
    }

    private fun oldAsset() =
        GithubAsset(
            id = 42L,
            name = "composeApp-debug.apk",
            contentType = "application/vnd.android.package-archive",
            size = 1_000L,
            downloadUrl = OLD_URL,
            digest = OLD_DIGEST,
        )

    private fun newAsset() =
        GithubAsset(
            id = 43L,
            name = "composeApp-debug.apk",
            contentType = "application/vnd.android.package-archive",
            size = 1_000L,
            downloadUrl = NEW_URL,
            digest = NEW_DIGEST,
        )

    private fun spec(): DownloadSpec =
        DownloadSpec(
            packageName = PKG,
            repoOwner = "illumiat",
            repoName = "komi-store",
            asset = oldAsset(),
            displayAppName = "Komi Store",
            installPolicy = InstallPolicy.DeferUntilUserAction,
            releaseTag = OLD_TAG,
        )

    private fun orchestrator(
        downloader: Downloader,
        multi: MultiSourceDownloader,
        verifier: DigestVerifier,
        repo: InstalledAppsRepository,
        notifier: PendingInstallNotifier,
        refetcher: AssetSourceRefetcher,
        scope: CoroutineScope,
    ) = DefaultDownloadOrchestrator(
        downloader = downloader,
        multiSourceDownloader = multi,
        assetSourceRefetcher = refetcher,
        digestVerifier = verifier,
        installer = StubInstaller,
        installedAppsRepository = repo,
        pendingInstallNotifier = notifier,
        slowDownloadDetector = StubSlowDownloadDetector,
        appScope = scope,
        systemInstallSerializer = StubInstallSerializer,
        tokenStore = StubTokenStore,
    )

    @Test
    fun theGoneAssetIsRefetchedFromTheHostAndTheDownloadEndsOnTheReplacement() =
        runBlocking {
            val downloader = TrackingDownloader()
            val multi = ThrowingOnceMultiSource()
            val verifier = RecordingVerifier()
            val repo = RecordingInstalledAppsRepository()
            val notifier = RecordingNotifier()
            val refetcher = RecordingRefetcher(
                spec().copy(asset = newAsset(), releaseTag = NEW_TAG, releaseId = 99L),
            )
            val orchestrator = orchestrator(downloader, multi, verifier, repo, notifier, refetcher, scope = this)

            orchestrator.enqueue(spec())
            val entry =
                withTimeout(AWAIT_MS) {
                    orchestrator.downloads.first { it[PKG]?.stage == DownloadStage.AwaitingInstall }
                }[PKG]!!

            assertEquals(
                listOf(OLD_URL, NEW_URL),
                multi.calls,
                "the dead URL is abandoned for the replacement the host handed back",
            )
            assertEquals(1, refetcher.calls.size, "the host is asked exactly once")
            assertEquals(
                listOf(NEW_DIGEST),
                verifier.expectedDigests,
                "the replacement's digest is the one verified",
            )
            assertEquals(
                NEW_TAG,
                repo.pendingPathVersion,
                "the parked record names the replacement's release, not the dead one",
            )
            assertEquals(
                listOf(NEW_TAG),
                notifier.versionTags,
                "the notification names the replacement's release too",
            )
            assertEquals(
                NEW_URL,
                entry.downloadUrl,
                "the entry records the replacement, so a later retry starts from truth",
            )
            assertEquals(NEW_TAG, entry.releaseTag)
            assertEquals(DownloadStage.AwaitingInstall, entry.stage)
        }

    @Test
    fun whenNothingMovedTheFailureStandsJustAsItWas() =
        runBlocking {
            val downloader = TrackingDownloader()
            val multi = ThrowingOnceMultiSource()
            val verifier = RecordingVerifier()
            val repo = RecordingInstalledAppsRepository()
            val notifier = RecordingNotifier()
            val refetcher = RecordingRefetcher(null)
            val orchestrator = orchestrator(downloader, multi, verifier, repo, notifier, refetcher, scope = this)

            orchestrator.enqueue(spec())
            val entry =
                withTimeout(AWAIT_MS) {
                    orchestrator.downloads.first { it[PKG]?.stage == DownloadStage.Failed }
                }[PKG]!!

            assertEquals(listOf(OLD_URL), multi.calls, "the dead URL is attempted only once")
            assertEquals(1, refetcher.calls.size)
            assertEquals(
                "Unexpected code 404",
                entry.errorMessage,
                "the user-visible failure keeps its exact message when no replacement exists",
            )
        }

    @Test
    fun aReuploadedAssetInsideTheSameReleaseIsTheReplacement() {
        val replacement = newAsset().copy(downloadUrl = NEW_URL)
        val window =
            listOf(
                release(tag = OLD_TAG, assets = listOf(replacement)),
            )

        val refetched = assetReplacementFor(spec(), window)

        assertEquals(replacement, refetched?.asset)
        assertEquals(OLD_TAG, refetched?.releaseTag, "a re-upload does not move the release")
    }

    @Test
    fun aDeletedReleaseFallsToTheNewestCarryingTheSameName() {
        val window =
            listOf(
                release(tag = NEW_TAG, assets = listOf(newAsset()), publishedAt = "2026-10-09T08:02:05Z"),
                release(tag = "older", assets = listOf(newAsset().copy(downloadUrl = "https://example.invalid/older.apk")), publishedAt = "2026-10-01T00:00:00Z"),
            )

        val refetched = assetReplacementFor(spec(), window)

        assertEquals(newAsset(), refetched?.asset)
        assertEquals(NEW_TAG, refetched?.releaseTag)
    }

    @Test
    fun theSameUrlIsNoReplacementAtAll() {
        val window = listOf(release(tag = OLD_TAG, assets = listOf(oldAsset())))

        assertEquals(null, assetReplacementFor(spec(), window))
    }

    @Test
    fun anUnrelatedAssetNameIsNoReplacementEither() {
        val window =
            listOf(
                release(tag = NEW_TAG, assets = listOf(newAsset().copy(name = "composeApp-release.apk"))),
            )

        assertEquals(null, assetReplacementFor(spec(), window))
    }

    private fun release(
        tag: String,
        assets: List<GithubAsset>,
        publishedAt: String = "2026-10-08T00:00:00Z",
    ) = GithubRelease(
        id = tag.hashCode().toLong(),
        tagName = tag,
        name = tag,
        publishedAt = publishedAt,
        description = null,
        assets = assets,
        tarballUrl = "",
        zipballUrl = "",
        htmlUrl = "",
    )

    private class ThrowingOnceMultiSource : MultiSourceDownloader {
        val calls = mutableListOf<String>()

        override fun download(githubUrl: String, suggestedFileName: String?): Flow<DownloadProgress> {
            calls += githubUrl
            if (githubUrl == OLD_URL) {
                throw AssetSourceGoneException(404)
            }
            return flowOf(
                DownloadProgress(bytesDownloaded = 1_000L, totalBytes = 1_000L, percent = 100),
            )
        }
    }

    private class RecordingRefetcher(private val result: DownloadSpec?) : AssetSourceRefetcher {
        val calls = mutableListOf<DownloadSpec>()

        override suspend fun refetch(spec: DownloadSpec): DownloadSpec? {
            calls += spec
            return result
        }
    }

    private class RecordingVerifier : DigestVerifier {
        val expectedDigests = mutableListOf<String>()

        override suspend fun verify(filePath: String, expectedDigest: String): String? {
            expectedDigests += expectedDigest
            return null
        }
    }

    private class TrackingDownloader : Downloader {
        override fun download(
            url: String,
            suggestedFileName: String?,
            bypassMirror: Boolean,
        ): Flow<DownloadProgress> = error("the direct fallback is not part of these paths")

        override suspend fun saveToFile(url: String, suggestedFileName: String?): String =
            error("saveToFile is not part of this path")

        override suspend fun getDownloadedFilePath(fileName: String): String =
            "/tmp/komi-refetch-test/$fileName"

        override suspend fun cancelDownload(fileName: String): Boolean = false
    }

    private class RecordingInstalledAppsRepository : InstalledAppsRepository {
        var pendingPathVersion: String? = null

        override suspend fun setPendingInstallFilePath(
            packageName: String,
            path: String?,
            version: String?,
            assetName: String?,
        ) {
            pendingPathVersion = version
        }

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

    private class RecordingNotifier : PendingInstallNotifier {
        val versionTags = mutableListOf<String>()

        override fun notifyPending(
            packageName: String,
            repoOwner: String,
            repoName: String,
            appName: String,
            versionTag: String,
        ) {
            versionTags += versionTag
        }

        override fun clearPending(packageName: String) = Unit
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
