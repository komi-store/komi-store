package zed.rainxch.core.data.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
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
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.DesktopDigestVerifier
import zed.rainxch.core.data.network.ForgejoClientRegistry
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.domain.model.account.HostToken
import zed.rainxch.core.domain.model.account.SessionSnapshot
import zed.rainxch.core.domain.model.account.TokenValidation
import zed.rainxch.core.domain.model.account.UserProfile
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.error.RateLimitInfo
import zed.rainxch.core.domain.model.installation.DeviceApp
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.SystemPackageInfo
import zed.rainxch.core.domain.model.settings.ProxyConfig
import zed.rainxch.core.domain.model.system.SystemArchitecture
import zed.rainxch.core.domain.repository.HostTokenRepository
import zed.rainxch.core.domain.repository.RateLimitRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.InstallerInfoExtractor
import zed.rainxch.core.domain.system.PackageMonitor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Every client points at a discard port, so a path that fetched instead of judging the fed
// list would fail loudly instead of reaching the network.
class InstalledAppsUpdateCheckWindowTest {
    @Test
    fun a_refreshed_same_tag_without_installed_identity_does_not_report_update() =
        runBlocking {
            withRepo { repo, db ->
                // A rolling tag reuses one string across builds, so the newer timestamp alone
                // would report an update the refresh cannot prove.
                db.installedAppDao.insertApp(
                    app(packageName = PKG, installed = "nightly", installedCode = 23L).copy(
                        latestVersion = "nightly",
                        latestReleaseId = 100L,
                        latestAssetId = 1000L,
                        latestAssetDigest = "sha256:old",
                        latestReleasePublishedAt = "2026-10-24T00:00:00Z",
                        isUpdateAvailable = true,
                    ),
                )
                val refreshed =
                    release(id = 200L, tag = "nightly", publishedAt = "2026-10-26T00:00:00Z").let { r ->
                        r.copy(assets = r.assets.map { it.copy(digest = "sha256:new") })
                    }

                val recorded =
                    repo.recordRefreshedRelease(
                        packageName = PKG,
                        release = refreshed,
                        asset = refreshed.assets.first(),
                    )

                assertTrue(recorded)
                val row = db.installedAppDao.getAppByPackage(PKG)!!
                assertFalse(row.isUpdateAvailable)
                // The pair the page shows is still recorded; only the update flag is withheld.
                assertEquals(200L, row.latestReleaseId)
                assertEquals(2000L, row.latestAssetId)
                assertEquals("sha256:new", row.latestAssetDigest)
            }
        }

    @Test
    fun a_forced_selection_is_written_when_the_window_filter_rejects_it() =
        runBlocking {
            withRepo { repo, db ->
                db.installedAppDao.insertApp(
                    app(packageName = PKG, installed = "1.0.0", installedCode = 100L)
                        .copy(
                            assetFilterRegex = ".*-sideload\\.apk$",
                            fallbackToOlderReleases = false,
                        ),
                )
                val release = release(id = 200L, tag = "2.0.0", publishedAt = "2026-10-01T00:00:00Z")
                val forced = release.assets.single()

                // The generic check cannot resolve a pair from a window its filter rejects.
                assertFalse(repo.checkForUpdatesWithReleases(PKG, listOf(release)))
                assertNull(db.installedAppDao.getAppByPackage(PKG)!!.latestVersion)

                assertTrue(repo.recordRefreshedRelease(PKG, release, forced))
                val row = db.installedAppDao.getAppByPackage(PKG)!!
                assertEquals("2.0.0", row.latestVersion)
                assertEquals(200L, row.latestReleaseId)
                assertEquals(forced.id, row.latestAssetId)
                assertTrue(row.isUpdateAvailable)
            }
        }

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
    fun a_successful_check_advances_the_revision() =
        runBlocking {
            withRepo { repo, db ->
                db.installedAppDao.insertApp(
                    app(packageName = PKG, installed = "1.0.0", installedCode = 100L),
                )

                repo.checkForUpdatesWithReleases(
                    PKG,
                    listOf(release(id = 200L, tag = "2.0.0", publishedAt = "2026-10-01T00:00:00Z")),
                )
                assertEquals(1L, db.installedAppDao.getAppByPackage(PKG)!!.updateStateRevision)

                repo.checkForUpdatesWithReleases(
                    PKG,
                    listOf(release(id = 300L, tag = "3.0.0", publishedAt = "2026-10-02T00:00:00Z")),
                )
                val row = db.installedAppDao.getAppByPackage(PKG)!!
                assertEquals(2L, row.updateStateRevision)
                assertEquals("3.0.0", row.latestVersion)
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
    fun a_window_without_this_apps_release_changes_nothing() =
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

                assertTrue(updated)
                val row = db.installedAppDao.getAppByPackage(PKG)!!
                assertTrue(row.isUpdateAvailable)
                assertEquals("9.9.9", row.latestVersion)
            }
        }

    @Test
    fun a_rejected_write_reports_the_stored_state_and_skips_adoption() =
        runBlocking {
            withRepo(installedAppsDao = { db -> ConflictingRevisionDao(db.installedAppDao) }) { repo, db ->
                db.installedAppDao.insertApp(
                    app(packageName = PKG, installed = "1.0.0", installedCode = 200L)
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

                // The concurrent writer's flag is the stored truth, not this check's verdict.
                assertTrue(updated)
                val row = db.installedAppDao.getAppByPackage(PKG)!!
                assertEquals(1L, row.updateStateRevision)
                assertEquals("9.9.9", row.latestVersion)
                assertTrue(row.isUpdateAvailable)
                // The adoption that belonged to the discarded verdict must not run.
                assertEquals("1.0.0", row.installedVersion)
            }
        }

    @Test
    fun a_refreshed_release_persists_the_release_and_asset_the_page_shows() =
        runBlocking {
            withRepo { repo, db ->
                db.installedAppDao.insertApp(
                    app(packageName = PKG, installed = "1.0.0", installedCode = 100L),
                )
                val release = release(id = 200L, tag = "2.0.0", publishedAt = "2026-10-01T00:00:00Z")
                val chosen =
                    release.assets
                        .single()
                        .copy(
                            id = 77L,
                            name = "app-universal.apk",
                            downloadUrl = "https://example.test/2.0.0/app-universal.apk",
                        )
                val window = release.copy(assets = release.assets + chosen)

                val recorded = repo.recordRefreshedRelease(PKG, window, chosen)

                assertTrue(recorded)
                val row = db.installedAppDao.getAppByPackage(PKG)!!
                assertTrue(row.isUpdateAvailable)
                assertEquals("2.0.0", row.latestVersion)
                assertEquals("2.0.0", row.latestVersionName)
                assertEquals(200L, row.latestReleaseId)
                assertEquals(77L, row.latestAssetId)
                assertEquals("app-universal.apk", row.latestAssetName)
                assertEquals("https://example.test/2.0.0/app-universal.apk", row.latestAssetUrl)
                assertEquals("2026-10-01T00:00:00Z", row.latestReleasePublishedAt)
                assertEquals("notes for 2.0.0", row.releaseNotes)
                assertEquals(1L, row.updateStateRevision)
                assertTrue(row.lastCheckedAt > 0L)
            }
        }

    @Test
    fun a_refreshed_release_retries_once_after_a_revision_race() =
        runBlocking {
            var conflicting: FirstWriteConflictsDao? = null
            withRepo(
                installedAppsDao = { db ->
                    FirstWriteConflictsDao(db.installedAppDao).also { conflicting = it }
                },
            ) { repo, db ->
                db.installedAppDao.insertApp(
                    app(packageName = PKG, installed = "1.0.0", installedCode = 100L),
                )
                val release = release(id = 200L, tag = "2.0.0", publishedAt = "2026-10-01T00:00:00Z")

                val recorded = repo.recordRefreshedRelease(PKG, release, release.assets.single())

                assertTrue(recorded)
                assertEquals(2, checkNotNull(conflicting).writes)
                val row = db.installedAppDao.getAppByPackage(PKG)!!
                assertEquals(2L, row.updateStateRevision)
                assertEquals("2.0.0", row.latestVersion)
                assertEquals(200L, row.latestReleaseId)
            }
        }

    @Test
    fun a_refreshed_release_is_not_written_while_the_check_is_disabled() =
        runBlocking {
            withRepo { repo, db ->
                db.installedAppDao.insertApp(
                    app(packageName = PKG, installed = "1.0.0", installedCode = 100L)
                        .copy(updateCheckEnabled = false),
                )
                val release = release(id = 200L, tag = "2.0.0", publishedAt = "2026-10-01T00:00:00Z")

                val recorded = repo.recordRefreshedRelease(PKG, release, release.assets.single())

                assertFalse(recorded)
                val row = db.installedAppDao.getAppByPackage(PKG)!!
                assertNull(row.latestVersion)
                assertFalse(row.isUpdateAvailable)
                assertEquals(0L, row.updateStateRevision)
                assertEquals(0L, row.lastCheckedAt)
            }
        }

    @Test
    fun an_older_generic_window_keeps_the_fresher_stored_release() =
        runBlocking {
            withRepo { repo, db ->
                db.installedAppDao.insertApp(
                    app(packageName = PKG, installed = "1.0.0", installedCode = 100L)
                        .copy(
                            latestVersion = "2.0.0",
                            latestReleaseId = 200L,
                            latestReleasePublishedAt = "2026-10-02T00:00:00Z",
                            isUpdateAvailable = true,
                        ),
                )

                val updated =
                    repo.checkForUpdatesWithReleases(
                        PKG,
                        listOf(release(id = 100L, tag = "1.5.0", publishedAt = "2026-10-01T00:00:00Z")),
                    )

                assertTrue(updated)
                val row = db.installedAppDao.getAppByPackage(PKG)!!
                assertEquals("2.0.0", row.latestVersion)
                assertEquals(200L, row.latestReleaseId)
                assertEquals("2026-10-02T00:00:00Z", row.latestReleasePublishedAt)
                assertEquals(0L, row.updateStateRevision)
                assertEquals(0L, row.lastCheckedAt)
            }
        }

    @Test
    fun an_equal_timestamp_generic_window_still_writes() =
        runBlocking {
            withRepo { repo, db ->
                db.installedAppDao.insertApp(
                    app(packageName = PKG, installed = "1.0.0", installedCode = 100L)
                        .copy(
                            latestVersion = "1.0.0",
                            latestReleaseId = 100L,
                            latestReleasePublishedAt = "2026-10-01T00:00:00Z",
                        ),
                )

                val updated =
                    repo.checkForUpdatesWithReleases(
                        PKG,
                        listOf(release(id = 200L, tag = "2.0.0", publishedAt = "2026-10-01T00:00:00Z")),
                    )

                assertTrue(updated)
                val row = db.installedAppDao.getAppByPackage(PKG)!!
                assertEquals("2.0.0", row.latestVersion)
                assertEquals(200L, row.latestReleaseId)
                assertEquals(1L, row.updateStateRevision)
            }
        }

    // A forge can re-upload the asset of a release without touching the tag or the publish
    // time. The per-app switch decides whether the new bytes count as an update.
    @Test
    fun the_per_app_switch_keeps_a_rebuilt_asset_quiet_until_it_is_on() =
        runBlocking {
            withRepo { repo, db ->
                db.installedAppDao.insertApp(
                    app(packageName = PKG, installed = "2.0.5", installedCode = 205L)
                        .copy(
                            installedAssetName = "app-2.0.5.apk",
                            installedReleaseId = 100L,
                            installedAssetId = 11L,
                            installedAssetDigest = "sha256:old",
                            installedReleasePublishedAt = "2026-10-01T00:00:00Z",
                            detectAssetRebuildsInRelease = false,
                        ),
                )
                val rebuilt = rebuiltRelease()

                assertFalse(repo.checkForUpdatesWithReleases(PKG, listOf(rebuilt)))
                assertFalse(db.installedAppDao.getAppByPackage(PKG)!!.isUpdateAvailable)

                repo.setDetectAssetRebuildsInRelease(PKG, true)
                assertTrue(repo.checkForUpdatesWithReleases(PKG, listOf(rebuilt)))
                assertTrue(db.installedAppDao.getAppByPackage(PKG)!!.isUpdateAvailable)
            }
        }

    // Several installed apps can share one release tag: only the app whose own asset was
    // rebuilt may report.
    @Test
    fun a_rebuilt_asset_updates_only_the_package_that_owns_it() =
        runBlocking {
            withRepo { repo, db ->
                val release = twoAssetRelease()
                db.installedAppDao.insertApp(
                    app(packageName = ONE, installed = "1.0.0", installedCode = 100L)
                        .copy(
                            installedAssetName = "one-1.0.0.apk",
                            installedReleaseId = 100L,
                            installedAssetId = 11L,
                            installedAssetDigest = "sha256:one-old",
                            installedReleasePublishedAt = "2026-10-01T00:00:00Z",
                            assetFilterRegex = "one-.*\\.apk$",
                        ),
                )
                db.installedAppDao.insertApp(
                    app(packageName = TWO, installed = "1.0.0", installedCode = 100L)
                        .copy(
                            installedAssetName = "two-1.0.0.apk",
                            installedReleaseId = 100L,
                            installedAssetId = 12L,
                            installedAssetDigest = "sha256:two-old",
                            installedReleasePublishedAt = "2026-10-01T00:00:00Z",
                            assetFilterRegex = "two-.*\\.apk$",
                        ),
                )

                assertTrue(repo.checkForUpdatesWithReleases(ONE, listOf(release)))
                assertFalse(repo.checkForUpdatesWithReleases(TWO, listOf(release)))

                val changed = db.installedAppDao.getAppByPackage(ONE)!!
                assertTrue(changed.isUpdateAvailable)
                assertEquals(21L, changed.latestAssetId)
                val untouched = db.installedAppDao.getAppByPackage(TWO)!!
                assertFalse(untouched.isUpdateAvailable)
            }
        }

    private fun rebuiltRelease(): GithubRelease {
        val base =
            release(id = 100L, tag = "2.0.5", publishedAt = "2026-10-01T00:00:00Z", assetName = "app-2.0.5.apk")
        return base.copy(assets = base.assets.map { it.copy(id = 12L, digest = "sha256:new") })
    }

    private fun twoAssetRelease(): GithubRelease =
        release(id = 100L, tag = "1.0.0", publishedAt = "2026-10-01T00:00:00Z").copy(
            assets =
                listOf(
                    asset(id = 21L, name = "one-1.0.0.apk", digest = "sha256:one-new"),
                    asset(id = 12L, name = "two-1.0.0.apk", digest = "sha256:two-old"),
                ),
        )

    private suspend fun withRepo(
        installedAppsDao: (AppDatabase) -> InstalledAppDao = { it.installedAppDao },
        block: suspend (InstalledAppsRepositoryImpl, AppDatabase) -> Unit,
    ) {
        val db =
            Room
                .inMemoryDatabaseBuilder<AppDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        try {
            block(newRepo(db, installedAppsDao(db)), db)
        } finally {
            db.close()
        }
    }

    // The proxy points at a discard port on purpose: construction is pure wiring, and any
    // request that ever leaves one of these clients fails at once instead of hitting a real
    // host.
    private val offlineProxy = ProxyConfig.Http(host = "127.0.0.1", port = 9)

    private fun newRepo(
        db: AppDatabase,
        installedAppsDao: InstalledAppDao,
    ): InstalledAppsRepositoryImpl =
        InstalledAppsRepositoryImpl(
            database = db,
            installedAppsDao = installedAppsDao,
            historyDao = db.updateHistoryDao,
            installer = FakeInstaller(),
            clientProvider =
                GitHubClientProvider(
                    tokenStore = FakeTokenStore(),
                    rateLimitRepository = FakeRateLimitRepository(),
                    userSessionRepository = FakeUserSessionRepository(),
                    hostTokenRepository = FakeHostTokenRepository(),
                    proxyConfigFlow = MutableStateFlow(offlineProxy),
                ),
            backendApiClient =
                BackendApiClient(
                    proxyConfigFlow = MutableStateFlow(offlineProxy),
                    tokenStore = FakeTokenStore(),
                ),
            forgejoClientRegistry =
                ForgejoClientRegistry(
                    proxyConfigFlow = MutableStateFlow(offlineProxy),
                    hostTokenRepository = FakeHostTokenRepository(),
                ),
            packageMonitor = NoPackageMonitor,
            digestVerifier = DesktopDigestVerifier(),
        )

    private object NoPackageMonitor : PackageMonitor {
        override suspend fun isPackageInstalled(packageName: String): Boolean = false

        override suspend fun getInstalledPackageInfo(packageName: String): SystemPackageInfo? = null

        override suspend fun getAllInstalledPackageNames(): Set<String> = emptySet()

        override suspend fun getAllInstalledApps(): List<DeviceApp> = emptyList()

        override fun canEnumerateInstalledPackages(): Boolean = false
    }

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

    private fun asset(
        id: Long,
        name: String,
        digest: String?,
    ): GithubAsset =
        GithubAsset(
            id = id,
            name = name,
            contentType = "application/vnd.android.package-archive",
            size = 1024L,
            downloadUrl = "https://example.test/$name",
            digest = digest,
        )

    private class FakeInstaller : Installer {
        override suspend fun isSupported(extOrMime: String): Boolean = true

        override suspend fun ensurePermissionsOrThrow(extOrMime: String) = Unit

        override suspend fun install(
            filePath: String,
            extOrMime: String,
        ): InstallOutcome = InstallOutcome.COMPLETED

        override fun uninstall(packageName: String) = Unit

        override fun isAssetInstallable(assetName: String): Boolean = assetName.endsWith(".apk", ignoreCase = true)

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

    private class FakeHostTokenRepository : HostTokenRepository {
        override fun observeAll(): Flow<List<HostToken>> = emptyFlow()

        override suspend fun get(host: String): HostToken? = null

        override suspend fun set(
            host: String,
            token: String,
            displayName: String?,
        ) = Unit

        override suspend fun delete(host: String) = Unit

        override suspend fun validate(
            host: String,
            token: String,
        ): Result<TokenValidation> =
            Result.success(
                TokenValidation(login = null, scopes = emptyList(), rateLimitRemaining = null),
            )
    }

    private companion object {
        const val PKG = "com.example.app"
        const val ONE = "com.example.one"
        const val TWO = "com.example.two"
    }
}

// Simulates a concurrent writer that advances the revision between the check's read and its
// write, so the repository must notice the compare-and-set failing.
private class ConflictingRevisionDao(
    private val delegate: InstalledAppDao,
) : InstalledAppDao by delegate {
    @Suppress("LongParameterList")
    override suspend fun updateVersionInfo(
        packageName: String,
        available: Boolean,
        version: String?,
        assetName: String?,
        assetUrl: String?,
        assetSize: Long?,
        releaseId: Long?,
        assetId: Long?,
        assetDigest: String?,
        releaseNotes: String?,
        timestamp: Long,
        latestVersionName: String?,
        latestVersionCode: Long?,
        latestReleasePublishedAt: String?,
        expectedRevision: Long,
        nextRevision: Long,
    ): Int {
        val current = checkNotNull(delegate.getAppByPackage(packageName))
        delegate.updateApp(
            current.copy(
                updateStateRevision = current.updateStateRevision + 1,
                latestVersion = "9.9.9",
                isUpdateAvailable = true,
            ),
        )
        return delegate.updateVersionInfo(
            packageName = packageName,
            available = available,
            version = version,
            assetName = assetName,
            assetUrl = assetUrl,
            assetSize = assetSize,
            releaseId = releaseId,
            assetId = assetId,
            assetDigest = assetDigest,
            releaseNotes = releaseNotes,
            timestamp = timestamp,
            latestVersionName = latestVersionName,
            latestVersionCode = latestVersionCode,
            latestReleasePublishedAt = latestReleasePublishedAt,
            expectedRevision = expectedRevision,
            nextRevision = nextRevision,
        )
    }
}

// Simulates a concurrent writer that lands between a refreshed release read and its write. The
// first compare-and-set must be rejected; the retry re-reads the row and is accepted.
private class FirstWriteConflictsDao(
    private val delegate: InstalledAppDao,
) : InstalledAppDao by delegate {
    var writes = 0
        private set

    private var conflicted = false

    @Suppress("LongParameterList")
    override suspend fun updateVersionInfo(
        packageName: String,
        available: Boolean,
        version: String?,
        assetName: String?,
        assetUrl: String?,
        assetSize: Long?,
        releaseId: Long?,
        assetId: Long?,
        assetDigest: String?,
        releaseNotes: String?,
        timestamp: Long,
        latestVersionName: String?,
        latestVersionCode: Long?,
        latestReleasePublishedAt: String?,
        expectedRevision: Long,
        nextRevision: Long,
    ): Int {
        writes += 1
        if (!conflicted) {
            conflicted = true
            val current = checkNotNull(delegate.getAppByPackage(packageName))
            delegate.updateApp(current.copy(updateStateRevision = current.updateStateRevision + 1))
        }
        return delegate.updateVersionInfo(
            packageName = packageName,
            available = available,
            version = version,
            assetName = assetName,
            assetUrl = assetUrl,
            assetSize = assetSize,
            releaseId = releaseId,
            assetId = assetId,
            assetDigest = assetDigest,
            releaseNotes = releaseNotes,
            timestamp = timestamp,
            latestVersionName = latestVersionName,
            latestVersionCode = latestVersionCode,
            latestReleasePublishedAt = latestReleasePublishedAt,
            expectedRevision = expectedRevision,
            nextRevision = nextRevision,
        )
    }
}
