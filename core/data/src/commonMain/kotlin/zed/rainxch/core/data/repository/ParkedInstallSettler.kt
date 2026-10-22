package zed.rainxch.core.data.repository

import co.touchlab.kermit.Logger
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toEntity
import zed.rainxch.core.domain.model.installation.ParkedInstallDisposal
import zed.rainxch.core.domain.model.installation.SystemPackageInfo
import zed.rainxch.core.domain.model.installation.clearPending
import zed.rainxch.core.domain.model.installation.settlePendingFromSystem
import zed.rainxch.core.domain.network.DigestVerifier
import zed.rainxch.core.domain.system.PackageMonitor
import kotlin.coroutines.cancellation.CancellationException

// Test builds reuse one versionCode, so the bytes are the only proof a park landed: the parked
// digest is the strongest record, older parks fall back to their file, then to the release
// digest of the installed build.
internal class ParkedInstallSettler(
    private val dao: InstalledAppDao,
    private val packageMonitor: PackageMonitor,
    private val digestVerifier: DigestVerifier,
    private val discard: suspend (String, String?) -> ParkedInstallDisposal,
) {
    suspend fun settleIfInstalled(app: InstalledAppEntity): InstalledAppEntity {
        if (!app.isPendingInstall) return app
        val parkedPath = app.pendingInstallFilePath
        val system = packageMonitor.getInstalledPackageInfo(app.packageName)
        return when {
            parkedPath.isNullOrBlank() -> {
                clearDeadPark(app)
            }

            system != null && parkedBytesMatch(app, parkedPath, system) -> {
                settleFinishedPark(app, parkedPath, system)
            }

            else -> {
                app
            }
        }
    }

    // A parked file that cannot be removed must not keep an installed build parked.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun settleFinishedPark(
        app: InstalledAppEntity,
        parkedPath: String,
        system: SystemPackageInfo,
    ): InstalledAppEntity {
        try {
            discard(app.packageName, parkedPath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
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

    private suspend fun parkedBytesMatch(
        app: InstalledAppEntity,
        parkedPath: String,
        system: SystemPackageInfo,
    ): Boolean {
        val installedHash = system.apkPath?.let { hashOrNull(it) }
        val parkedDigest = app.pendingInstallAssetDigest
        val parkedFileHash = if (parkedDigest == null) hashOrNull(parkedPath) else null
        return zed.rainxch.core.data.repository.parkedBytesMatch(
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

    // Any hashing failure means "no proof", which must not crash the update check.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun hashOrNull(path: String): String? =
        try {
            digestVerifier.computeSha256(path)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e) { "Updates: could not hash $path" }
            null
        }
}
