package zed.rainxch.core.data.local.db

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.domain.model.installation.InstallSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// The update check reads a row, judges it, then writes the result back. The write is a
// compare-and-set on updateStateRevision, so only the check that still holds the revision it
// read may persist its result.
class InstalledAppUpdateStateRevisionTest {
    @Test
    fun a_write_at_the_current_revision_is_accepted_and_advances_it() =
        runBlocking {
            withDao { dao ->
                dao.insertApp(app())

                val first = dao.writeVersionInfo(version = "2.0.0", expectedRevision = 0, timestamp = 5)
                assertEquals(1, first)
                val once = dao.getAppByPackage(PKG)!!
                assertEquals(1L, once.updateStateRevision)
                assertEquals("2.0.0", once.latestVersion)
                assertTrue(once.isUpdateAvailable)
                assertEquals(5L, once.lastCheckedAt)

                val second = dao.writeVersionInfo(version = "3.0.0", expectedRevision = 1, timestamp = 6)
                assertEquals(1, second)
                val twice = dao.getAppByPackage(PKG)!!
                assertEquals(2L, twice.updateStateRevision)
                assertEquals("3.0.0", twice.latestVersion)
            }
        }

    @Test
    fun a_write_from_an_older_revision_is_rejected() =
        runBlocking {
            withDao { dao ->
                dao.insertApp(app())
                assertEquals(1, dao.writeVersionInfo(version = "2.0.0", expectedRevision = 0, timestamp = 5))

                val stale =
                    dao.writeVersionInfo(
                        version = "9.9.9",
                        expectedRevision = 0,
                        timestamp = 100,
                        available = false,
                    )
                assertEquals(0, stale)

                val row = dao.getAppByPackage(PKG)!!
                assertEquals(1L, row.updateStateRevision)
                assertEquals("2.0.0", row.latestVersion)
                assertTrue(row.isUpdateAvailable)
                assertEquals(5L, row.lastCheckedAt)

                val recovered = dao.writeVersionInfo(version = "3.0.0", expectedRevision = 1, timestamp = 7)
                assertEquals(1, recovered)
                val after = dao.getAppByPackage(PKG)!!
                assertEquals(2L, after.updateStateRevision)
                assertEquals("3.0.0", after.latestVersion)
                assertNotEquals("9.9.9", after.latestVersion)
            }
        }

    private suspend fun withDao(block: suspend (InstalledAppDao) -> Unit) {
        val db =
            Room
                .inMemoryDatabaseBuilder<AppDatabase>()
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        try {
            block(db.installedAppDao)
        } finally {
            db.close()
        }
    }

    private suspend fun InstalledAppDao.writeVersionInfo(
        version: String,
        expectedRevision: Long,
        timestamp: Long,
        available: Boolean = true,
    ): Int =
        updateVersionInfo(
            packageName = PKG,
            available = available,
            version = version,
            assetName = "app.apk",
            assetUrl = "https://example.test/$version/app.apk",
            assetSize = 1024L,
            releaseId = 2L,
            assetId = 3L,
            assetDigest = null,
            releaseNotes = "notes",
            timestamp = timestamp,
            latestVersionName = version,
            latestVersionCode = null,
            latestReleasePublishedAt = null,
            expectedRevision = expectedRevision,
            nextRevision = expectedRevision + 1,
        )

    private fun app(): InstalledAppEntity =
        InstalledAppEntity(
            packageName = PKG,
            repoId = 42L,
            repoName = "repo",
            repoOwner = "owner",
            repoOwnerAvatarUrl = "",
            repoDescription = null,
            primaryLanguage = null,
            repoUrl = "https://example.test/owner/repo",
            installedVersion = "1.0.0",
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
            installedVersionCode = 100L,
        )

    private companion object {
        const val PKG = "com.example.app"
    }
}
