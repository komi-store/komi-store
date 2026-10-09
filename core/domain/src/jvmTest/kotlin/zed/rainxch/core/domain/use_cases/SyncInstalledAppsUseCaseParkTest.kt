package zed.rainxch.core.domain.use_cases

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.installation.DeviceApp
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.ParkedInstallDisposal
import zed.rainxch.core.domain.model.installation.SystemPackageInfo
import zed.rainxch.core.domain.model.installation.testInstalledApp
import zed.rainxch.core.domain.model.smart_detect.MatchingPreview
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.InstallerInfoExtractor
import zed.rainxch.core.domain.system.PackageMonitor

// What a fake discard call carried: the sync has to decide against the path it observed, so the
// fake records path and package together instead of just the package name.
private data class DiscardCall(
    val packageName: String,
    val expectedPath: String?,
)

// The keep-or-discard decision is a property of the sync that performs it, not of the model
// helpers under it: the model never sees the file path, and the routing between the pointer and
// the APK is the behaviour at stake. One case per arm, plus the row-deletion path that must not
// leave an APK on disk with nothing naming it.
class SyncInstalledAppsUseCaseParkTest {

    @Test
    fun parkedUpdateSurvivesTheLibrarySyncWhileTheTargetIsNotOnTheSystem() = runBlocking {
        val parked = parked()
        val repo = RecordingInstalledAppsRepository(listOf(parked))
        val monitor = FakePackageMonitor(
            packageName = parked.packageName,
            systemInfo = systemInfo(parked.packageName, versionName = "1.0.0", versionCode = 100L),
        )

        SyncInstalledAppsUseCase(monitor, repo, NoApkInfoExtractor, Platform.ANDROID, NoOpLogger)()

        assertTrue(repo.discardedPackages.isEmpty(), "the park must not be discarded")
        assertTrue(repo.updatedApps.isEmpty(), "the record must be left as it was")
        assertTrue(repo.pendingPathWrites.isEmpty(), "the pointer must not be cleared")
        assertEquals("/data/parked.apk", repo.apps.single().pendingInstallFilePath)
        assertTrue(repo.apps.single().isPendingInstall)
    }

    @Test
    fun parkedUpdateSurvivesTheLibrarySyncWhenTheSystemReportsNoInfo() = runBlocking {
        val parked = parked()
        val repo = RecordingInstalledAppsRepository(listOf(parked))
        val monitor = FakePackageMonitor(packageName = parked.packageName, systemInfo = null)

        SyncInstalledAppsUseCase(monitor, repo, NoApkInfoExtractor, Platform.ANDROID, NoOpLogger)()

        assertTrue(repo.updatedApps.isEmpty())
        assertTrue(repo.discardedPackages.isEmpty())
        assertTrue(repo.pendingPathWrites.isEmpty())
    }

    @Test
    fun parkedUpdateSurvivesTheLibrarySyncWhenTheLookupFails() = runBlocking {
        val parked = parked()
        val repo = RecordingInstalledAppsRepository(listOf(parked))
        val monitor = FakePackageMonitor(
            packageName = parked.packageName,
            systemInfo = null,
            lookupFails = true,
        )

        SyncInstalledAppsUseCase(monitor, repo, NoApkInfoExtractor, Platform.ANDROID, NoOpLogger)()

        assertTrue(repo.updatedApps.isEmpty())
        assertTrue(repo.discardedPackages.isEmpty())
        assertTrue(repo.pendingPathWrites.isEmpty())
    }

    @Test
    fun parkedUpdateIsDiscardedOnlyOnceTheTargetReachesTheSystem() = runBlocking {
        val parked = parked()
        val repo = RecordingInstalledAppsRepository(listOf(parked))
        val monitor = FakePackageMonitor(
            packageName = parked.packageName,
            systemInfo = systemInfo(parked.packageName, versionName = "2.0.0", versionCode = 200L),
        )

        SyncInstalledAppsUseCase(monitor, repo, NoApkInfoExtractor, Platform.ANDROID, NoOpLogger)()

        assertFalse(repo.updatedApps.single().isPendingInstall)
        assertFalse(repo.updatedApps.single().isUpdateAvailable)
        assertEquals(listOf(parked.packageName), repo.discardedPackages)
        assertEquals(listOf(DiscardCall(parked.packageName, "/data/parked.apk")), repo.discardCalls)
        assertTrue(
            repo.pendingPathWrites.isEmpty(),
            "the park must be cleared through the discard, not by clearing the path alone",
        )
    }

    @Test
    fun parkedUpdateIsDiscardedWhenTheSystemIsAheadOfTheTarget() = runBlocking {
        val parked = parked()
        val repo = RecordingInstalledAppsRepository(listOf(parked))
        val monitor = FakePackageMonitor(
            packageName = parked.packageName,
            systemInfo = systemInfo(parked.packageName, versionName = "3.0.0", versionCode = 300L),
        )

        SyncInstalledAppsUseCase(monitor, repo, NoApkInfoExtractor, Platform.ANDROID, NoOpLogger)()

        assertEquals(listOf(parked.packageName), repo.discardedPackages)
    }

    @Test
    fun parkedUpdateWithAnUnprovableTargetSurvivesWhileTheNameIsUnchanged() = runBlocking {
        val parked = parked(latestVersionCode = null)
        val repo = RecordingInstalledAppsRepository(listOf(parked))
        val monitor = FakePackageMonitor(
            packageName = parked.packageName,
            systemInfo = systemInfo(parked.packageName, versionName = "1.0.0", versionCode = 100L),
        )

        SyncInstalledAppsUseCase(monitor, repo, NoApkInfoExtractor, Platform.ANDROID, NoOpLogger)()

        assertTrue(repo.updatedApps.isEmpty(), "the record must be left as it was")
        assertTrue(repo.discardedPackages.isEmpty(), "the park must not be discarded")
    }

    @Test
    fun parkedUpdateWithAnUnprovableTargetConvergesWhenTheNameMoved() = runBlocking {
        val parked = parked(latestVersionCode = null)
        val repo = RecordingInstalledAppsRepository(listOf(parked))
        val monitor = FakePackageMonitor(
            packageName = parked.packageName,
            systemInfo = systemInfo(parked.packageName, versionName = "2.0.0", versionCode = 100L),
        )

        SyncInstalledAppsUseCase(monitor, repo, NoApkInfoExtractor, Platform.ANDROID, NoOpLogger)()

        assertEquals("2.0.0", repo.updatedApps.single().installedVersion)
        assertEquals(listOf(parked.packageName), repo.discardedPackages)
    }

    @Test
    fun deletingAnUninstalledRowDiscardsItsParkBeforeTheRowGoes() = runBlocking {
        val abandoned = parked(packageName = "com.example.abandoned", installedAt = 0L)
        val tracked = parked(
            packageName = "com.example.tracked",
            isPendingInstall = false,
            pendingFilePath = null,
        )
        val repo = RecordingInstalledAppsRepository(listOf(abandoned, tracked))
        val monitor = FakePackageMonitor(
            packageName = tracked.packageName,
            systemInfo = null,
            allPackageNames =
                setOf(tracked.packageName) + (1..19).map { "com.example.filler$it" },
        )

        SyncInstalledAppsUseCase(monitor, repo, NoApkInfoExtractor, Platform.ANDROID, NoOpLogger)()

        // One ordered sequence, with the path that was passed in and the commit in front: a
        // regression that drops the row first, discards a path the caller never saw, or runs the
        // cleanup before the transaction ends cannot pass.
        assertEquals(
            listOf(
                "commit",
                "discard:${abandoned.packageName}:/data/parked.apk",
                "delete:${abandoned.packageName}",
            ),
            repo.cleanupLog,
        )
        assertEquals(listOf(DiscardCall(abandoned.packageName, "/data/parked.apk")), repo.discardCalls)
    }

    @Test
    fun aParkThatIsNotGoneKeepsItsRow() = runBlocking {
        val abandoned = parked(packageName = "com.example.abandoned", installedAt = 0L)
        val repo = RecordingInstalledAppsRepository(listOf(abandoned))
        repo.discardOutcome = ParkedInstallDisposal.Retained
        val monitor = FakePackageMonitor(
            packageName = abandoned.packageName,
            systemInfo = null,
            allPackageNames = (1..20).map { "com.example.filler$it" }.toSet(),
        )

        SyncInstalledAppsUseCase(monitor, repo, NoApkInfoExtractor, Platform.ANDROID, NoOpLogger)()

        assertEquals(listOf(abandoned.packageName), repo.discardedPackages)
        assertTrue(
            repo.deletedPackages.isEmpty(),
            "the row is the only handle to a parked file that is not gone, so it must stay",
        )
    }

    @Test
    fun aRowWithNoParkedFileIsStillCheckedBeforeItsRowGoes() = runBlocking {
        val abandoned = parked(
            packageName = "com.example.abandoned",
            isPendingInstall = false,
            pendingFilePath = null,
        )
        val tracked = parked(
            packageName = "com.example.tracked",
            isPendingInstall = false,
            pendingFilePath = null,
        )
        val repo = RecordingInstalledAppsRepository(listOf(abandoned, tracked))
        val monitor = FakePackageMonitor(
            packageName = tracked.packageName,
            systemInfo = null,
            allPackageNames =
                setOf(tracked.packageName) + (1..19).map { "com.example.filler$it" },
        )

        SyncInstalledAppsUseCase(monitor, repo, NoApkInfoExtractor, Platform.ANDROID, NoOpLogger)()

        // Even a row that names no park goes through the discard first, so a park written between
        // the scan and the delete still stops the row from going.
        assertEquals(listOf(DiscardCall(abandoned.packageName, null)), repo.discardCalls)
        assertEquals(listOf(abandoned.packageName), repo.deletedPackages)
    }

    @Test
    fun aParkedFileOnARowWithoutThePendingFlagSurvivesUntilTheTargetLands() = runBlocking {
        // The cancelled/failed-install shape: the flag is down but the file is still the one the
        // user may install (the UI keys "ready to install" off the path alone), so a sync without
        // proof of the target must leave it alone.
        val ready = parked(isPendingInstall = false)
        val repo = RecordingInstalledAppsRepository(listOf(ready))
        val monitor = FakePackageMonitor(
            packageName = ready.packageName,
            systemInfo = systemInfo(ready.packageName, versionName = "1.0.0", versionCode = 100L),
            allPackageNames =
                setOf(ready.packageName) + (1..19).map { "com.example.filler$it" },
        )

        SyncInstalledAppsUseCase(monitor, repo, NoApkInfoExtractor, Platform.ANDROID, NoOpLogger)()

        assertTrue(repo.discardedPackages.isEmpty(), "the file is still what the user needs")
        assertEquals("/data/parked.apk", repo.apps.single().pendingInstallFilePath)
    }

    @Test
    fun aParkedFileOnARowWithoutThePendingFlagGoesOnceTheTargetLands() = runBlocking {
        val ready = parked(isPendingInstall = false)
        val repo = RecordingInstalledAppsRepository(listOf(ready))
        val monitor = FakePackageMonitor(
            packageName = ready.packageName,
            systemInfo = systemInfo(ready.packageName, versionName = "2.0.0", versionCode = 200L),
            allPackageNames =
                setOf(ready.packageName) + (1..19).map { "com.example.filler$it" },
        )

        SyncInstalledAppsUseCase(monitor, repo, NoApkInfoExtractor, Platform.ANDROID, NoOpLogger)()

        // The row only carries the file, so the park is discarded (file and pointer together)
        // and the row itself stays.
        assertEquals(listOf(DiscardCall(ready.packageName, "/data/parked.apk")), repo.discardCalls)
        assertTrue(repo.deletedPackages.isEmpty(), "the row is not part of the discard")
    }

    @Test
    fun aPendingFlagWithoutAParkResolvesInsteadOfHoldingTheRow() = runBlocking {
        // An install that stopped before parking: the flag protects no file, so it resolves
        // instead of pinning the row in the pending group forever.
        val orphaned = parked(pendingFilePath = null)
        val repo = RecordingInstalledAppsRepository(listOf(orphaned))
        val monitor = FakePackageMonitor(
            packageName = orphaned.packageName,
            systemInfo = systemInfo(orphaned.packageName, versionName = "1.0.0", versionCode = 100L),
            allPackageNames =
                setOf(orphaned.packageName) + (1..19).map { "com.example.filler$it" },
        )

        SyncInstalledAppsUseCase(monitor, repo, NoApkInfoExtractor, Platform.ANDROID, NoOpLogger)()

        assertEquals(listOf(orphaned.packageName to false), repo.pendingStatusWrites)
        assertTrue(repo.discardedPackages.isEmpty())
    }

    @Test
    fun aFreshParkIsLeftAloneBeforeItsInstallIsAttempted() = runBlocking {
        // Just parked, not on the system yet, younger than the stale timeout: the scan must skip
        // the row entirely or a fresh park would be swept before the user installs it.
        val fresh = parked(installedAt = System.currentTimeMillis())
        val repo = RecordingInstalledAppsRepository(listOf(fresh))
        val monitor = FakePackageMonitor(
            packageName = fresh.packageName,
            systemInfo = null,
            allPackageNames = (1..20).map { "com.example.filler$it" }.toSet(),
        )

        SyncInstalledAppsUseCase(monitor, repo, NoApkInfoExtractor, Platform.ANDROID, NoOpLogger)()

        assertTrue(repo.discardedPackages.isEmpty())
        assertTrue(repo.deletedPackages.isEmpty())
        assertTrue(repo.updatedApps.isEmpty())
        assertTrue(repo.pendingStatusWrites.isEmpty())
    }

    @Test
    fun aPendingParkSettlesWhenTheParkedFileProvesTheSystemAlreadyRunsItsBuild() = runBlocking {
        // The same device shape as the stranded file below, with the flag still up: the
        // install succeeded (its run wrote the optimistic version) and the flag was never
        // lowered, but the gate can never mint — the target is a rolling tag, the name did not
        // move, and no code was recorded. The parked file carries the re-proof the sweep
        // trusts, so the pending state must settle instead of pinning the card forever.
        val pending =
            testInstalledApp(
                packageName = "zed.rainxch.githubstore",
                installedVersion = "testbuild-all-prs-20261009b",
                installedVersionName = "1.9.3",
                installedVersionCode = 22L,
                latestVersion = "testbuild-all-prs-20261009b",
                latestVersionCode = null,
                pendingInstallFilePath = "/data/parked.apk",
                pendingInstallVersion = "testbuild-all-prs-20261009b",
                isPendingInstall = true,
                installedAt = 1_000L,
            )
        val repo = RecordingInstalledAppsRepository(listOf(pending))
        val monitor = FakePackageMonitor(
            packageName = pending.packageName,
            systemInfo = systemInfo(
                pending.packageName,
                versionName = "1.9.3",
                versionCode = 22L,
                lastUpdateTime = 2_000L,
            ),
            allPackageNames = setOf(pending.packageName) + (1..19).map { "com.example.filler$it" },
        )
        val extractor = FakeApkInfoExtractor(
            apkFile(
                versionCode = 22L,
                fileAt = 1_000L,
                packageName = "zed.rainxch.githubstore",
            ),
        )

        SyncInstalledAppsUseCase(monitor, repo, extractor, Platform.ANDROID, NoOpLogger)()

        assertFalse(repo.updatedApps.single().isPendingInstall, "the pending state must settle")
        assertEquals(
            listOf(DiscardCall(pending.packageName, "/data/parked.apk")),
            repo.discardCalls,
        )
        assertTrue(repo.deletedPackages.isEmpty(), "only the park goes, not the row")
    }

    @Test
    fun aPendingParkSurvivesWhenTheParkedFileIsNewerThanTheSystemBuild() = runBlocking {
        // The inverse guard: a file written after the system's install is not superseded — it is
        // still the user's next install, and the settle must not clear it.
        val pending =
            testInstalledApp(
                packageName = "zed.rainxch.githubstore",
                installedVersion = "testbuild-all-prs-20261009b",
                installedVersionName = "1.9.3",
                installedVersionCode = 22L,
                latestVersion = "testbuild-all-prs-20261009b",
                latestVersionCode = null,
                pendingInstallFilePath = "/data/parked.apk",
                pendingInstallVersion = "testbuild-all-prs-20261009b",
                isPendingInstall = true,
                installedAt = 1_000L,
            )
        val repo = RecordingInstalledAppsRepository(listOf(pending))
        val monitor = FakePackageMonitor(
            packageName = pending.packageName,
            systemInfo = systemInfo(
                pending.packageName,
                versionName = "1.9.3",
                versionCode = 22L,
                lastUpdateTime = 2_000L,
            ),
            allPackageNames = setOf(pending.packageName) + (1..19).map { "com.example.filler$it" },
        )
        val extractor = FakeApkInfoExtractor(
            apkFile(
                versionCode = 22L,
                fileAt = 3_000L,
                packageName = "zed.rainxch.githubstore",
            ),
        )

        SyncInstalledAppsUseCase(monitor, repo, extractor, Platform.ANDROID, NoOpLogger)()

        assertTrue(repo.updatedApps.isEmpty(), "nothing may be written while the target is unproven")
        assertTrue(repo.discardedPackages.isEmpty(), "the park must stay")
        assertTrue(repo.pendingPathWrites.isEmpty(), "the pointer must not be cleared")
    }

    @Test
    fun aStrandedParkedFileGoesWhenTheSystemAlreadyRunsItsBuild() = runBlocking {
        // The stranded shape from the device: the flag was lowered by a resolution whose file
        // delete failed, and the gate can no longer re-derive a proof (the target is a rolling
        // tag, the name did not move, and a cleared check left no code). The parked file itself
        // still says it is obsolete: the system runs its build and was installed after the file
        // was written.
        val stranded =
            testInstalledApp(
                packageName = "zed.rainxch.githubstore",
                installedVersion = "testbuild-all-prs-20261007",
                installedVersionName = "1.9.3",
                installedVersionCode = 22L,
                latestVersion = "testbuild-all-prs-20261007",
                latestVersionCode = null,
                pendingInstallFilePath = "/data/parked.apk",
                pendingInstallVersion = "testbuild-all-prs-20261007",
                isPendingInstall = false,
                installedAt = 1_000L,
            )
        val repo = RecordingInstalledAppsRepository(listOf(stranded))
        val monitor = FakePackageMonitor(
            packageName = stranded.packageName,
            systemInfo = systemInfo(
                stranded.packageName,
                versionName = "1.9.3",
                versionCode = 22L,
                lastUpdateTime = 2_000L,
            ),
            allPackageNames =
                setOf(stranded.packageName) + (1..19).map { "com.example.filler$it" },
        )
        val extractor = FakeApkInfoExtractor(
            apkFile(
                versionCode = 22L,
                fileAt = 1_000L,
                packageName = "zed.rainxch.githubstore",
            ),
        )

        SyncInstalledAppsUseCase(monitor, repo, extractor, Platform.ANDROID, NoOpLogger)()

        assertEquals(
            listOf(DiscardCall(stranded.packageName, "/data/parked.apk")),
            repo.discardCalls,
        )
        assertTrue(repo.deletedPackages.isEmpty(), "only the park goes, not the row")
    }

    @Test
    fun aStrandedParkedFileSurvivesWhileTheSystemRunsAnOlderBuild() = runBlocking {
        // The failed-or-cancelled install: the file is still the user's next install, so the
        // sweep must keep it even though the flag is down.
        val ready = parked(isPendingInstall = false, latestVersionCode = null)
        val repo = RecordingInstalledAppsRepository(listOf(ready))
        val monitor = FakePackageMonitor(
            packageName = ready.packageName,
            systemInfo = systemInfo(
                ready.packageName,
                versionName = "1.0.0",
                versionCode = 100L,
                lastUpdateTime = 2_000L,
            ),
            allPackageNames =
                setOf(ready.packageName) + (1..19).map { "com.example.filler$it" },
        )
        val extractor = FakeApkInfoExtractor(apkFile(versionCode = 200L, fileAt = 1_000L))

        SyncInstalledAppsUseCase(monitor, repo, extractor, Platform.ANDROID, NoOpLogger)()

        assertTrue(repo.discardedPackages.isEmpty(), "the file is still what the user needs")
        assertEquals("/data/parked.apk", repo.apps.single().pendingInstallFilePath)
    }

    @Test
    fun aStrandedParkedFileSurvivesWhenTheSystemsInstallPredatesIt() = runBlocking {
        // Same build on both sides, but the system's install predates the download: this is an
        // install the user has not taken yet, not a landed one.
        val ready = parked(isPendingInstall = false, latestVersionCode = null)
        val repo = RecordingInstalledAppsRepository(listOf(ready))
        val monitor = FakePackageMonitor(
            packageName = ready.packageName,
            systemInfo = systemInfo(
                ready.packageName,
                versionName = "1.0.0",
                versionCode = 200L,
                lastUpdateTime = 500L,
            ),
            allPackageNames =
                setOf(ready.packageName) + (1..19).map { "com.example.filler$it" },
        )
        val extractor = FakeApkInfoExtractor(apkFile(versionCode = 200L, fileAt = 1_000L))

        SyncInstalledAppsUseCase(monitor, repo, extractor, Platform.ANDROID, NoOpLogger)()

        assertTrue(repo.discardedPackages.isEmpty())
    }

    private fun apkFile(
        versionCode: Long,
        fileAt: Long?,
        packageName: String = "com.example.app",
    ): ApkPackageInfo = ApkPackageInfo(
        packageName = packageName,
        versionName = "1.9.3",
        versionCode = versionCode,
        appName = "App",
        signingFingerprint = "SHA",
        fileLastModified = fileAt,
    )

    private fun parked(
        packageName: String = "com.example.app",
        installedVersion: String = "1.0.0",
        installedVersionName: String = "1.0.0",
        installedVersionCode: Long = 100L,
        latestVersion: String? = "2.0.0",
        latestVersionCode: Long? = 200L,
        pendingFilePath: String? = "/data/parked.apk",
        isPendingInstall: Boolean = true,
        installedAt: Long = 1_000L,
    ): InstalledApp = testInstalledApp(
        packageName = packageName,
        installedVersion = installedVersion,
        installedVersionName = installedVersionName,
        installedVersionCode = installedVersionCode,
        latestVersion = latestVersion,
        latestVersionCode = latestVersionCode,
        pendingInstallFilePath = pendingFilePath,
        isPendingInstall = isPendingInstall,
        installedAt = installedAt,
        latestReleasePublishedAt = "2026-08-01T00:00:00Z",
    )

    private fun systemInfo(
        packageName: String,
        versionName: String,
        versionCode: Long,
        lastUpdateTime: Long? = null,
    ): SystemPackageInfo = SystemPackageInfo(
        packageName = packageName,
        versionName = versionName,
        versionCode = versionCode,
        isInstalled = true,
        signingFingerprint = "SHA",
        lastUpdateTime = lastUpdateTime,
    )

    private class FakePackageMonitor(
        private val packageName: String,
        private val systemInfo: SystemPackageInfo?,
        private val lookupFails: Boolean = false,
        private val allPackageNames: Set<String> = setOf(packageName),
    ) : PackageMonitor {
        override suspend fun isPackageInstalled(packageName: String): Boolean = true

        override suspend fun getInstalledPackageInfo(packageName: String): SystemPackageInfo? {
            if (lookupFails) error("package lookup failed for $packageName")
            return systemInfo?.takeIf { it.packageName == packageName }
        }

        override suspend fun getAllInstalledPackageNames(): Set<String> = allPackageNames

        override suspend fun getAllInstalledApps(): List<DeviceApp> = emptyList()

        override fun canEnumerateInstalledPackages(): Boolean = true
    }

    private object NoOpLogger : KomiStoreLogger {
        override fun debug(message: String) = Unit

        override fun info(message: String) = Unit

        override fun warn(message: String) = Unit

        override fun error(message: String, throwable: Throwable?) = Unit
    }

    private object NoApkInfoExtractor : InstallerInfoExtractor {
        override suspend fun extractPackageInfo(filePath: String): ApkPackageInfo? = null
    }

    private class FakeApkInfoExtractor(
        private val info: ApkPackageInfo?,
    ) : InstallerInfoExtractor {
        override suspend fun extractPackageInfo(filePath: String): ApkPackageInfo? = info
    }

    private class RecordingInstalledAppsRepository(initial: List<InstalledApp>) :
        InstalledAppsRepository {
        var apps: List<InstalledApp> = initial
        val updatedApps = mutableListOf<InstalledApp>()
        val pendingPathWrites = mutableListOf<String?>()
        val pendingStatusWrites = mutableListOf<Pair<String, Boolean>>()
        val discardCalls = mutableListOf<DiscardCall>()
        val deletedPackages = mutableListOf<String>()

        // One ordered sequence: the park has to be gone before its row is allowed to follow, and
        // every cleanup has to happen only after the commit — two independent lists cannot tell
        // whether either is the order the use case used. "commit" marks the transaction's end.
        val cleanupLog = mutableListOf<String>()

        // The disposal a test wants the fake to report; the default is a clean discard.
        var discardOutcome: ParkedInstallDisposal = ParkedInstallDisposal.Discarded

        val discardedPackages: List<String> get() = discardCalls.map { it.packageName }

        // Flow-of-the-list rather than a snapshot: each collection re-reads the field, so a test
        // that mutates the fake between collections sees the update.
        override fun getAllInstalledApps(): Flow<List<InstalledApp>> = flow { emit(apps) }

        override suspend fun updateApp(app: InstalledApp) {
            updatedApps += app
            apps = apps.map { if (it.packageName == app.packageName) app else it }
        }

        override suspend fun setPendingInstallFilePath(
            packageName: String,
            path: String?,
            version: String?,
            assetName: String?,
        ) {
            pendingPathWrites += path
        }

        // Records the routing only: the real file deletion lives in InstalledAppsRepositoryImpl
        // and is pinned by its own jvmTest, so this fake must not stand in for it.
        override suspend fun discardParkedInstall(
            packageName: String,
            expectedPath: String?,
        ): ParkedInstallDisposal {
            discardCalls += DiscardCall(packageName, expectedPath)
            cleanupLog += "discard:$packageName:${expectedPath ?: "none"}"
            return discardOutcome
        }

        override suspend fun markAwaitingInstall(
            packageName: String,
            path: String,
            version: String?,
            assetName: String?,
        ) = Unit

        override fun getAppsWithUpdates(): Flow<List<InstalledApp>> = emptyFlow()

        override fun getUpdateCount(): Flow<Int> = flowOf(0)

        override suspend fun getAppByPackage(packageName: String): InstalledApp? =
            apps.find { it.packageName == packageName }

        override suspend fun getAppByRepoId(repoId: Long): InstalledApp? = null

        override fun getAppByRepoIdAsFlow(repoId: Long): Flow<InstalledApp?> = flowOf(null)

        override suspend fun getAppsByRepoId(repoId: Long): List<InstalledApp> = emptyList()

        override fun getAppsByRepoIdAsFlow(repoId: Long): Flow<List<InstalledApp>> = emptyFlow()

        override suspend fun isAppInstalled(repoId: Long): Boolean = false

        override suspend fun saveInstalledApp(app: InstalledApp) = Unit

        override suspend fun deleteInstalledApp(packageName: String) {
            deletedPackages += packageName
            cleanupLog += "delete:$packageName"
            apps = apps.filterNot { it.packageName == packageName }
        }

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

        override suspend fun updateInstalledVersion(
            packageName: String,
            installedVersion: String,
            installedVersionName: String?,
            installedVersionCode: Long,
            isUpdateAvailable: Boolean,
        ) = Unit

        override suspend fun updatePendingStatus(packageName: String, isPending: Boolean) {
            pendingStatusWrites += packageName to isPending
        }

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
        ): MatchingPreview = error("previewMatchingAssets is not part of the sync path")

        override suspend fun <R> executeInTransaction(block: suspend () -> R): R {
            val result = block()
            cleanupLog += "commit"
            return result
        }
    }
}
