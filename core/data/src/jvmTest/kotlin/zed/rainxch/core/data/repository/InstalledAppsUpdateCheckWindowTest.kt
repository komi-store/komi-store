package zed.rainxch.core.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import zed.rainxch.core.data.data_source.TokenStore
import zed.rainxch.core.data.dto.GithubDeviceTokenSuccessDto
import zed.rainxch.core.data.local.db.AppDatabase
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.ForgejoClientRegistry
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.domain.model.account.SessionSnapshot
import zed.rainxch.core.domain.model.account.UserProfile
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.error.RateLimitInfo
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.settings.ProxyConfig
import zed.rainxch.core.domain.model.system.SystemArchitecture
import zed.rainxch.core.domain.repository.RateLimitRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.InstallerInfoExtractor

// The window-fed check is the other half of the details refresh: the page already holds the
// repository host's own release list, and these tests pin that the list — and nothing else —
// decides the verdict and writes the row. Every client here is pointed at a discard port, so
// a path that tried to fetch instead of judging the window would fail loudly, not silently
// reach the network.
class InstalledAppsUpdateCheckWindowTest {

    @Test
    fun a_fed_window_settles_the_stored_update_state() =
        runBlocking {
            withRepo { repo, db ->
                db.installedAppDao.insertApp(
                    app(packageName = PKG, installed = "1.0.0", installedCode = 100L),
                )

                val updated =
                    repo.checkForUpdatesWithReleases(
                        PKG,
                        listOf(release(id = 200L, tag = "2.0.0", publishedAt = "2026-10-01T00:00:00Z")),
                    )

                assertTrue(updated)
                val row = db.installedAppDao.getAppByPackage(PKG)!!
                assertTrue(row.isUpdateAvailable)
                assertEquals("2.0.0", row.latestVersion)
                assertEquals("https://example.test/2.0.0/app.apk", row.latestAssetUrl)
                assertEquals(200L, row.latestReleaseId)
                assertEquals("2026-10-01T00:00:00Z", row.latestReleasePublishedAt)
                assertTrue(row.lastCheckedAt > 0L)
            }
        }

    @Test
    fun a_window_the_device_already_runs_clears_a_stale_report() =
        runBlocking {
            withRepo { repo, db ->
                db.installedAppDao.insertApp(
                    app(packageName = PKG, installed = "2.0.0", installedCode = 200L)
                        .copy(
                            latestVersion = "2.0.0",
                            latestVersionCode = 200L,
                            isUpdateAvailable = true,
                        ),
                )

                val updated =
                    repo.checkForUpdatesWithReleases(
                        PKG,
                        listOf(release(id = 200L, tag = "2.0.0", publishedAt = "2026-10-01T00:00:00Z")),
                    )

                assertFalse(updated)
                val row = db.installedAppDao.getAppByPackage(PKG)!!
                assertFalse(row.isUpdateAvailable)
                assertEquals("2.0.0", row.latestVersion)
            }
        }

    @Test
    fun an_empty_window_changes_nothing() =
        runBlocking {
            withRepo { repo, db ->
                db.installedAppDao.insertApp(
                    app(packageName = PKG, installed = "1.0.0", installedCode = 100L)
                        .copy(latestVersion = "9.9.9", isUpdateAvailable = true, lastCheckedAt = 7L),
                )

                val updated = repo.checkForUpdatesWithReleases(PKG, emptyList())

                assertTrue(updated)
                val row = db.installedAppDao.getAppByPackage(PKG)!!
                assertTrue(row.isUpdateAvailable)
                assertEquals("9.9.9", row.latestVersion)
                assertEquals(7L, row.lastCheckedAt)
            }
        }

    @Test
    fun a_disabled_check_is_not_run_by_the_window() =
        runBlocking {
            withRepo { repo, db ->
                db.installedAppDao.insertApp(
                    app(packageName = PKG, installed = "1.0.0", installedCode = 100L)
                        .copy(updateCheckEnabled = false),
                )

                val updated =
                    repo.checkForUpdatesWithReleases(
                        PKG,
                        listOf(release(id = 200L, tag = "2.0.0", publishedAt = "2026-10-01T00:00:00Z")),
                    )

                assertFalse(updated)
                val row = db.installedAppDao.getAppByPackage(PKG)!!
                assertFalse(row.isUpdateAvailable)
                assertNull(row.latestVersion)
            }
        }

    @Test
    fun a_window_with_no_matching_asset_reports_nothing() =
        runBlocking {
            withRepo { repo, db ->
                db.installedAppDao.insertApp(
                    app(packageName = PKG, installed = "1.0.0", installedCode = 100L)
                        .copy(latestVersion = "9.9.9", isUpdateAvailable = true),
                )

                val updated =
                    repo.checkForUpdatesWithReleases(
                        PKG,
                        listOf(
                            release(
                                id = 200L,
                                tag = "2.0.0",
                                publishedAt = "2026-10-01T00:00:00Z",
                                assetName = "release-notes.txt",
                            ),
                        ),
                    )

                assertFalse(updated)
                val row = db.installedAppDao.getAppByPackage(PKG)!!
                assertFalse(row.isUpdateAvailable)
                assertEquals("9.9.9", row.latestVersion)
            }
        }

    private suspend fun withRepo(block: suspend (InstalledAppsRepositoryImpl, AppDatabase) -> Unit) {
        val db =
            Room.inMemoryDatabaseBuilder<AppDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        try {
            block(newRepo(db), db)
        } finally {
            db.close()
        }
    }

    // The proxy points at a discard port on purpose: construction is pure wiring, and any
    // request that ever leaves one of these clients fails at once instead of hitting a real
    // host.
    private val offlineProxy = ProxyConfig.Http(host = "127.0.0.1", port = 9)

    private fun newRepo(db: AppDatabase): InstalledAppsRepositoryImpl =
        InstalledAppsRepositoryImpl(
            database = db,
            installedAppsDao = db.installedAppDao,
            historyDao = db.updateHistoryDao,
            installer = FakeInstaller(),
            clientProvider =
                GitHubClientProvider(
                    tokenStore = FakeTokenStore(),
                    rateLimitRepository = FakeRateLimitRepository(),
                    userSessionRepository = FakeUserSessionRepository(),
                    proxyConfigFlow = MutableStateFlow(offlineProxy),
                ),
            backendApiClient =
                BackendApiClient(
                    proxyConfigFlow = MutableStateFlow(offlineProxy),
                    tokenStore = FakeTokenStore(),
                ),
            forgejoClientRegistry = ForgejoClientRegistry(MutableStateFlow(offlineProxy)),
        )

    private fun app(
        packageName: String,
        installed: String,
        installedCode: Long,
    ): InstalledAppEntity =
        InstalledAppEntity(
            packageName = packageName,
            repoId = 42L,
            repoName = "repo",
            repoOwner = "owner",
            repoOwnerAvatarUrl = "",
            repoDescription = null,
            primaryLanguage = null,
            repoUrl = "https://example.test/owner/repo",
            installedVersion = installed,
            installedAssetName = null,
            installedAssetUrl = null,
            latestVersion = null,
            latestAssetName = null,
            latestAssetUrl = null,
            latestAssetSize = null,
            appName = "App",
            installSource = InstallSource.THIS_APP,
            signingFingerprint = null,
            installedAt = 0L,
            lastCheckedAt = 0L,
            lastUpdatedAt = 0L,
            isUpdateAvailable = false,
            systemArchitecture = "AARCH64",
            fileExtension = "apk",
            installedVersionCode = installedCode,
        )

    private fun release(
        id: Long,
        tag: String,
        publishedAt: String,
        assetName: String = "app.apk",
        isPrerelease: Boolean = false,
    ): GithubRelease =
        GithubRelease(
            id = id,
            tagName = tag,
            name = tag,
            publishedAt = publishedAt,
            description = "notes for $tag",
            assets =
                listOf(
                    GithubAsset(
                        id = id * 10,
                        name = assetName,
                        contentType = "application/vnd.android.package-archive",
                        size = 1024L,
                        downloadUrl = "https://example.test/$tag/$assetName",
                        digest = null,
                    ),
                ),
            tarballUrl = "",
            zipballUrl = "",
            htmlUrl = "",
            isPrerelease = isPrerelease,
        )

    private class FakeInstaller : Installer {
        override suspend fun isSupported(extOrMime: String): Boolean = true

        override suspend fun ensurePermissionsOrThrow(extOrMime: String) = Unit

        override suspend fun install(
            filePath: String,
            extOrMime: String,
        ): InstallOutcome = InstallOutcome.COMPLETED

        override fun uninstall(packageName: String) = Unit

        override fun isAssetInstallable(assetName: String): Boolean =
            assetName.endsWith(".apk", ignoreCase = true)

        override fun choosePrimaryAsset(assets: List<GithubAsset>): GithubAsset? = assets.firstOrNull()

        override fun detectSystemArchitecture(): SystemArchitecture = SystemArchitecture.AARCH64

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

        override fun getApkInfoExtractor(): InstallerInfoExtractor =
            object : InstallerInfoExtractor {
                override suspend fun extractPackageInfo(filePath: String): ApkPackageInfo? = null
            }

        override fun openApp(packageName: String): Boolean = false

        override fun openWithExternalInstaller(filePath: String) = Unit
    }

    private class FakeTokenStore : TokenStore {
        override fun tokenFlow(): Flow<GithubDeviceTokenSuccessDto?> = emptyFlow()

        override suspend fun currentToken(): GithubDeviceTokenSuccessDto? = null

        override fun blockingCurrentToken(): GithubDeviceTokenSuccessDto? = null

        override suspend fun save(token: GithubDeviceTokenSuccessDto) = Unit

        override suspend fun clear() = Unit

        override suspend fun isTokenExpired(): Boolean = false
    }

    private class FakeRateLimitRepository : RateLimitRepository {
        override val rateLimitState: StateFlow<RateLimitInfo?> = MutableStateFlow(null)

        override val rateLimitExhaustedEvent: SharedFlow<RateLimitInfo> = MutableSharedFlow()

        override fun updateRateLimit(
            rateLimitInfo: RateLimitInfo?,
            notifyExhausted: Boolean,
        ) = Unit

        override fun getCurrentRateLimit(): RateLimitInfo? = null

        override fun isCurrentlyLimited(): Boolean = false

        override fun clear() = Unit
    }

    private class FakeUserSessionRepository : UserSessionRepository {
        override fun isUserLoggedIn(): Flow<Boolean> = flowOf(false)

        override fun getUser(): Flow<UserProfile?> = flowOf(null)

        override suspend fun isCurrentlyUserLoggedIn(): Boolean = false

        override val lastKnownSession: SessionSnapshot? = null

        override suspend fun primeSession() = Unit

        override fun clearLastKnownSession() = Unit

        override val sessionExpiredEvent: SharedFlow<Unit> = MutableSharedFlow()

        override suspend fun notifySessionExpired(tokenKey: String?) = Unit

        override suspend fun notifyRequestSucceeded(tokenKey: String?) = Unit

        override suspend fun logout() = Unit
    }

    private companion object {
        const val PKG = "com.example.app"
    }
}
