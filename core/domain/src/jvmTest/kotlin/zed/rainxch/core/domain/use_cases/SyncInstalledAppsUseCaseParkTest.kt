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
import zed.rainxch.core.domain.model.installation.DeviceApp
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.ParkedInstallDisposal
import zed.rainxch.core.domain.model.installation.SystemPackageInfo
import zed.rainxch.core.domain.model.installation.testInstalledApp
import zed.rainxch.core.domain.model.smart_detect.MatchingPreview
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.PackageMonitor

private data class DiscardCall(
    val packageName: String,
    val expectedPath: String?,
)

class SyncInstalledAppsUseCaseParkTest {

    @Test
    fun parkedUpdateSurvivesTheLibrarySyncWhileTheTargetIsNotOnTheSystem() = runBlocking {
        val parked = parked()
        val repo = RecordingInstalledAppsRepository(listOf(parked))
        val monitor = FakePackageMonitor(
            packageName = parked.packageName,
            systemInfo = systemInfo(parked.packageName, versionName = "1.0.0", versionCode = 100L),
        )

        SyncInstalledAppsUseCase(monitor, repo, Platform.ANDROID, NoOpLogger)()

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

        SyncInstalledAppsUseCase(monitor, repo, Platform.ANDROID, NoOpLogger)()

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

        SyncInstalledAppsUseCase(monitor, repo, Platform.ANDROID, NoOpLogger)()

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

        SyncInstalledAppsUseCase(monitor, repo, Platform.ANDROID, NoOpLogger)()

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

        SyncInstalledAppsUseCase(monitor, repo, Platform.ANDROID, NoOpLogger)()

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

        SyncInstalledAppsUseCase(monitor, repo, Platform.ANDROID, NoOpLogger)()

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

        SyncInstalledAppsUseCase(monitor, repo, Platform.ANDROID, NoOpLogger)()

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

        SyncInstalledAppsUseCase(monitor, repo, Platform.ANDROID, NoOpLogger)()

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

        SyncInstalledAppsUseCase(monitor, repo, Platform.ANDROID, NoOpLogger)()

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

        SyncInstalledAppsUseCase(monitor, repo, Platform.ANDROID, NoOpLogger)()

        assertEquals(listOf(DiscardCall(abandoned.packageName, null)), repo.discardCalls)
        assertEquals(listOf(abandoned.packageName), repo.deletedPackages)
    }

    @Test
    fun aParkedFileOnARowWithoutThePendingFlagSurvivesUntilTheTargetLands() = runBlocking {
        val ready = parked(isPendingInstall = false)
        val repo = RecordingInstalledAppsRepository(listOf(ready))
        val monitor = FakePackageMonitor(
            packageName = ready.packageName,
            systemInfo = systemInfo(ready.packageName, versionName = "1.0.0", versionCode = 100L),
            allPackageNames =
                setOf(ready.packageName) + (1..19).map { "com.example.filler$it" },
        )

        SyncInstalledAppsUseCase(monitor, repo, Platform.ANDROID, NoOpLogger)()

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

        SyncInstalledAppsUseCase(monitor, repo, Platform.ANDROID, NoOpLogger)()

        assertEquals(listOf(DiscardCall(ready.packageName, "/data/parked.apk")), repo.discardCalls)
        assertTrue(repo.deletedPackages.isEmpty(), "the row is not part of the discard")
    }

    @Test
    fun aPendingFlagWithoutAParkResolvesInsteadOfHoldingTheRow() = runBlocking {
        val orphaned = parked(pendingFilePath = null)
        val repo = RecordingInstalledAppsRepository(listOf(orphaned))
        val monitor = FakePackageMonitor(
            packageName = orphaned.packageName,
            systemInfo = systemInfo(orphaned.packageName, versionName = "1.0.0", versionCode = 100L),
            allPackageNames =
                setOf(orphaned.packageName) + (1..19).map { "com.example.filler$it" },
        )

        SyncInstalledAppsUseCase(monitor, repo, Platform.ANDROID, NoOpLogger)()

        assertEquals(listOf(orphaned.packageName to false), repo.pendingStatusWrites)
        assertTrue(repo.discardedPackages.isEmpty())
    }

    @Test
    fun aFreshParkIsLeftAloneBeforeItsInstallIsAttempted() = runBlocking {
        val fresh = parked(installedAt = System.currentTimeMillis())
        val repo = RecordingInstalledAppsRepository(listOf(fresh))
        val monitor = FakePackageMonitor(
            packageName = fresh.packageName,
            systemInfo = null,
            allPackageNames = (1..20).map { "com.example.filler$it" }.toSet(),
        )

        SyncInstalledAppsUseCase(monitor, repo, Platform.ANDROID, NoOpLogger)()

        assertTrue(repo.discardedPackages.isEmpty())
        assertTrue(repo.deletedPackages.isEmpty())
        assertTrue(repo.updatedApps.isEmpty())
        assertTrue(repo.pendingStatusWrites.isEmpty())
    }

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
    ): SystemPackageInfo = SystemPackageInfo(
        packageName = packageName,
        versionName = versionName,
        versionCode = versionCode,
        isInstalled = true,
        signingFingerprint = "SHA",
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

    private class RecordingInstalledAppsRepository(initial: List<InstalledApp>) :
        InstalledAppsRepository {
        var apps: List<InstalledApp> = initial
        val updatedApps = mutableListOf<InstalledApp>()
        val pendingPathWrites = mutableListOf<String?>()
        val pendingStatusWrites = mutableListOf<Pair<String, Boolean>>()
        val discardCalls = mutableListOf<DiscardCall>()
        val deletedPackages = mutableListOf<String>()

        val cleanupLog = mutableListOf<String>()

        var discardOutcome: ParkedInstallDisposal = ParkedInstallDisposal.Discarded

        val discardedPackages: List<String> get() = discardCalls.map { it.packageName }

        override suspend fun resolveTrackedRelease(
            packageName: String,
        ): zed.rainxch.core.domain.utils.ResolvedRelease? = null

        override fun getAllInstalledApps(): Flow<List<InstalledApp>> = flow { emit(apps) }

        override suspend fun updateApp(app: InstalledApp) {
            updatedApps += app
            apps = apps.map { if (it.packageName == app.packageName) app else it }
        }

        override suspend fun checkForUpdatesWithReleases(
            packageName: String,
            releases: List<zed.rainxch.core.domain.model.account.github.GithubRelease>,
        ): Boolean = false

        override suspend fun setPendingInstallFilePath(
            packageName: String,
            path: String?,
            version: String?,
            assetName: String?,
        ) {
            pendingPathWrites += path
        }

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
            releaseId: Long?,
            assetId: Long?,
            releasePublishedAt: String?,
            assetDigest: String?,
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
            newReleasePublishedAt: String?,
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

        override suspend fun updateSigningFingerprint(
            packageName: String,
            signingFingerprint: String,
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
