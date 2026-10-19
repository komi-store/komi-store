package zed.rainxch.core.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import zed.rainxch.core.domain.model.installation.InstallSource
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.nameWithoutExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppDatabaseMigrationTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val schemaDir = Path("schemas")

    private val exportedVersions =
        schemaDir
            .resolve(checkNotNull(AppDatabase::class.qualifiedName))
            .listDirectoryEntries("*.json")
            .map { it.nameWithoutExtension.toInt() }
            .sorted()

    private val latestVersion = exportedVersions.last()

    @Test
    fun everyOlderVersionUpgradesWithoutLosingData() {
        // 10 never shipped. A database created at 10 lacks the DEFAULT 0 on fallbackToOlderReleases
        // that MIGRATION_9_10 adds, so it cannot pass schema validation at 11+.
        val versions = listOf(1, 2) + exportedVersions.filter { it < latestVersion && it != 10 }

        versions.forEach { version ->
            val file = File(tmp.root, "v$version.db")
            openAtVersion(file, version).use { it.insertRows() }

            val db = buildDatabase(file)
            try {
                runBlocking {
                    val app = assertNotNull(db.installedAppDao.getAppByPackage(PACKAGE), "v$version")
                    assertEquals("1.0.0", app.installedVersion, "v$version")
                    assertEquals(InstallSource.THIS_APP, app.installSource, "v$version")

                    val favourite = assertNotNull(db.favoriteRepoDao.getFavoriteById(REPO_ID), "v$version")
                    assertEquals("repo", favourite.repoName, "v$version")

                    val history = db.updateHistoryDao.getHistoryForApp(PACKAGE).first()
                    assertEquals(listOf("1.0.0"), history.map { it.toVersion }, "v$version")
                }
            } finally {
                db.close()
            }
        }
    }

    @Test
    fun downgradeRecreatesTheDatabase() {
        val file = File(tmp.root, "newer.db")
        openAtVersion(file, latestVersion).use { connection ->
            connection.insertRows()
            connection.execSQL("PRAGMA user_version = ${latestVersion + 1}")
        }

        val db = buildDatabase(file)
        try {
            runBlocking { assertNull(db.installedAppDao.getAppByPackage(PACKAGE)) }
        } finally {
            db.close()
        }
    }

    private fun openAtVersion(
        file: File,
        version: Int,
    ): SQLiteConnection =
        when (version) {
            1, 2 -> {
                BundledSQLiteDriver().open(file.path).also { connection ->
                    connection.execSQL(if (version == 1) INSTALLED_APPS_V1 else INSTALLED_APPS_V2)
                    connection.execSQL(FAVORITE_REPOS_V1)
                    connection.execSQL(UPDATE_HISTORY_V1)
                    connection.execSQL("PRAGMA user_version = $version")
                }
            }

            else -> {
                MigrationTestHelper(
                    schemaDirectoryPath = schemaDir,
                    databasePath = file.toPath(),
                    driver = BundledSQLiteDriver(),
                    databaseClass = AppDatabase::class,
                ).createDatabase(version)
            }
        }

    private fun SQLiteConnection.insertRows() {
        val app =
            linkedMapOf(
                "packageName" to "'$PACKAGE'",
                "repoId" to "$REPO_ID",
                "repoName" to "'repo'",
                "repoOwner" to "'owner'",
                "repoOwnerAvatarUrl" to "''",
                "repoUrl" to "'https://github.com/owner/repo'",
                "installedVersion" to "'1.0.0'",
                "appName" to "'App'",
                "installSource" to "'THIS_APP'",
                "installedAt" to "1",
                "lastCheckedAt" to "1",
                "lastUpdatedAt" to "1",
                "isUpdateAvailable" to "0",
                "updateCheckEnabled" to "1",
                "systemArchitecture" to "'X64'",
                "fileExtension" to "'msi'",
                "isPendingInstall" to "0",
            )
        prepare("PRAGMA table_info(installed_apps)").use { columns ->
            while (columns.step()) {
                val requiresValue = columns.getLong(3) == 1L && columns.isNull(4)
                if (requiresValue) app.putIfAbsent(columns.getText(1), "0")
            }
        }
        execSQL("INSERT INTO installed_apps (${app.keys.joinToString()}) VALUES (${app.values.joinToString()})")
        execSQL(
            """
            INSERT INTO favorite_repos (repoId, repoName, repoOwner, repoOwnerAvatarUrl, repoUrl,
                isInstalled, installedPackageName, addedAt, lastSyncedAt)
            VALUES ($REPO_ID, 'repo', 'owner', '', 'https://github.com/owner/repo', 1, '$PACKAGE', 1, 1)
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO update_history (packageName, appName, repoOwner, repoName, fromVersion,
                toVersion, updatedAt, updateSource, success)
            VALUES ('$PACKAGE', 'App', 'owner', 'repo', '0.9.0', '1.0.0', 1, 'THIS_APP', 1)
            """.trimIndent(),
        )
    }

    private companion object {
        const val PACKAGE = "com.example.app"
        const val REPO_ID = 42L

        const val INSTALLED_APPS_V1 =
            "CREATE TABLE IF NOT EXISTS `installed_apps` (`packageName` TEXT NOT NULL, " +
                "`repoId` INTEGER NOT NULL, `repoName` TEXT NOT NULL, `repoOwner` TEXT NOT NULL, " +
                "`repoOwnerAvatarUrl` TEXT NOT NULL, `repoDescription` TEXT, `primaryLanguage` TEXT, " +
                "`repoUrl` TEXT NOT NULL, `installedVersion` TEXT NOT NULL, `installedAssetName` TEXT, " +
                "`installedAssetUrl` TEXT, `latestVersion` TEXT, `latestAssetName` TEXT, " +
                "`latestAssetUrl` TEXT, `latestAssetSize` INTEGER, `appName` TEXT NOT NULL, " +
                "`installSource` TEXT NOT NULL, `installedAt` INTEGER NOT NULL, " +
                "`lastCheckedAt` INTEGER NOT NULL, `lastUpdatedAt` INTEGER NOT NULL, " +
                "`isUpdateAvailable` INTEGER NOT NULL, `updateCheckEnabled` INTEGER NOT NULL, " +
                "`releaseNotes` TEXT, `systemArchitecture` TEXT NOT NULL, `fileExtension` TEXT NOT NULL, " +
                "`isPendingInstall` INTEGER NOT NULL, PRIMARY KEY(`packageName`))"

        const val INSTALLED_APPS_V2 =
            "CREATE TABLE IF NOT EXISTS `installed_apps` (`packageName` TEXT NOT NULL, " +
                "`repoId` INTEGER NOT NULL, `repoName` TEXT NOT NULL, `repoOwner` TEXT NOT NULL, " +
                "`repoOwnerAvatarUrl` TEXT NOT NULL, `repoDescription` TEXT, `primaryLanguage` TEXT, " +
                "`repoUrl` TEXT NOT NULL, `installedVersion` TEXT NOT NULL, `installedAssetName` TEXT, " +
                "`installedAssetUrl` TEXT, `latestVersion` TEXT, `latestAssetName` TEXT, " +
                "`latestAssetUrl` TEXT, `latestAssetSize` INTEGER, `appName` TEXT NOT NULL, " +
                "`installSource` TEXT NOT NULL, `installedAt` INTEGER NOT NULL, " +
                "`lastCheckedAt` INTEGER NOT NULL, `lastUpdatedAt` INTEGER NOT NULL, " +
                "`isUpdateAvailable` INTEGER NOT NULL, `updateCheckEnabled` INTEGER NOT NULL, " +
                "`releaseNotes` TEXT, `systemArchitecture` TEXT NOT NULL, `fileExtension` TEXT NOT NULL, " +
                "`isPendingInstall` INTEGER NOT NULL, `installedVersionName` TEXT, " +
                "`installedVersionCode` INTEGER NOT NULL, `latestVersionName` TEXT, " +
                "`latestVersionCode` INTEGER, PRIMARY KEY(`packageName`))"

        const val FAVORITE_REPOS_V1 =
            "CREATE TABLE IF NOT EXISTS `favorite_repos` (`repoId` INTEGER NOT NULL, " +
                "`repoName` TEXT NOT NULL, `repoOwner` TEXT NOT NULL, `repoOwnerAvatarUrl` TEXT NOT NULL, " +
                "`repoDescription` TEXT, `primaryLanguage` TEXT, `repoUrl` TEXT NOT NULL, " +
                "`isInstalled` INTEGER NOT NULL, `installedPackageName` TEXT, `latestVersion` TEXT, " +
                "`latestReleaseUrl` TEXT, `addedAt` INTEGER NOT NULL, `lastSyncedAt` INTEGER NOT NULL, " +
                "PRIMARY KEY(`repoId`))"

        const val UPDATE_HISTORY_V1 =
            "CREATE TABLE IF NOT EXISTS `update_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`packageName` TEXT NOT NULL, `appName` TEXT NOT NULL, `repoOwner` TEXT NOT NULL, " +
                "`repoName` TEXT NOT NULL, `fromVersion` TEXT NOT NULL, `toVersion` TEXT NOT NULL, " +
                "`updatedAt` INTEGER NOT NULL, `updateSource` TEXT NOT NULL, `success` INTEGER NOT NULL, " +
                "`errorMessage` TEXT)"
    }
}
