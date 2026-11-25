package zed.rainxch.core.data.repository

import androidx.sqlite.SQLiteException
import co.touchlab.kermit.Logger
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toEntity
import zed.rainxch.core.domain.model.installation.SystemPackageInfo
import zed.rainxch.core.domain.model.installation.clearPending
import zed.rainxch.core.domain.model.installation.settlePendingFromSystem
import zed.rainxch.core.domain.network.DigestVerifier
import zed.rainxch.core.domain.system.PackageMonitor

// Test builds reuse a versionCode, so the bytes prove a park landed: parked digest, then parked file, then release digest.
internal class ParkedInstallSettler(
    private val dao: InstalledAppDao,
    private val packageMonitor: PackageMonitor,
    private val digestVerifier: DigestVerifier,
) {
    suspend fun settleIfInstalled(app: InstalledAppEntity): InstalledAppEntity {
        if (!app.isPendingInstall) return app
        val parkedPath = app.pendingInstallFilePath
        val system = packageMonitor.getInstalledPackageInfo(app.packageName)
        return when {
            parkedPath.isNullOrBlank() -> {
                clearDeadPark(app)
            }

            system != null && matchesParkedBytes(app, parkedPath, system) -> {
                settleFinishedPark(app, parkedPath, system)
            }

            else -> {
                app
            }
        }
    }

    private suspend fun settleFinishedPark(
        app: InstalledAppEntity,
        parkedPath: String,
        system: SystemPackageInfo,
    ): InstalledAppEntity {
        try {
            dao.discardParkedInstall(app.packageName, parkedPath)
        } catch (e: SQLiteException) {
            // A parked file that cannot be removed must not keep an installed build parked.
            Logger.w(e) { "Updates: could not discard the parked file for ${app.packageName}" }
        }
        dao.updateApp(
            app
                .toDomain()
                .settlePendingFromSystem(
                    versionName = system.versionName,
                    versionCode = system.versionCode,
                ).clearPending()
                .toEntity(),
        )
        Logger.i {
            "Updates: the parked build for ${app.packageName} is the installed one; settled"
        }
        return dao.getAppByPackage(app.packageName) ?: app
    }

    // DigestVerifier hashes to null when it can't read a file, which counts as no proof.
    private suspend fun matchesParkedBytes(
        app: InstalledAppEntity,
        parkedPath: String,
        system: SystemPackageInfo,
    ): Boolean {
        val installedHash = system.apkPath?.let { digestVerifier.computeSha256(it) }
        val parkedDigest = app.pendingInstallAssetDigest
        val parkedFileHash = if (parkedDigest == null) digestVerifier.computeSha256(parkedPath) else null
        return parkedBytesMatch(
            installedHash = installedHash,
            parkedDigest = parkedDigest,
            parkedFileHash = parkedFileHash,
            releaseDigest = app.latestAssetDigest,
        )
    }

    private suspend fun clearDeadPark(app: InstalledAppEntity): InstalledAppEntity {
        dao.updateApp(app.toDomain().clearPending().toEntity())
        return dao.getAppByPackage(app.packageName) ?: app
    }
}
