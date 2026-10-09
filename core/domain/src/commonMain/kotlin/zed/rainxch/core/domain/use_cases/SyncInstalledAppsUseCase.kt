package zed.rainxch.core.domain.use_cases

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.apk.ApkPackageInfo
import zed.rainxch.core.domain.model.installation.BindingStatus
import zed.rainxch.core.domain.model.installation.DeviceChange
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.ParkedInstallDisposal
import zed.rainxch.core.domain.model.installation.PendingInstallResolution
import zed.rainxch.core.domain.model.installation.SystemPackageInfo
import zed.rainxch.core.domain.model.installation.bindingStatusAgainst
import zed.rainxch.core.domain.model.installation.clearPending
import zed.rainxch.core.domain.model.installation.deviceChangeAgainst
import zed.rainxch.core.domain.model.installation.observeExternalInstall
import zed.rainxch.core.domain.model.installation.pendingInstallResolution
import zed.rainxch.core.domain.model.installation.resolvePendingFromSystem
import zed.rainxch.core.domain.model.installation.withMigratedVersionInfo
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.InstallerInfoExtractor
import zed.rainxch.core.domain.system.PackageMonitor

// The surviving proof for a stale parked file, next to the resolution gate the row's park was
// admitted under: the system running the file's build (or a newer one), installed after the file
// finished writing, means the download has served its purpose — the bytes are on the device as an
// app, not just on disk as a file. A file whose build is newer than the system, or that predates
// the system's last install, is still what the user may install, so it stays.
internal fun parkedFileSuperseded(
    packageName: String,
    file: ApkPackageInfo,
    system: SystemPackageInfo?,
): Boolean {
    if (system == null || !system.isInstalled) return false
    if (file.packageName != packageName) return false
    if (file.versionCode <= 0L || system.versionCode <= 0L) return false
    if (system.versionCode < file.versionCode) return false
    val installedAt = system.lastUpdateTime ?: return false
    val fileAt = file.fileLastModified ?: return false
    return installedAt >= fileAt
}

class SyncInstalledAppsUseCase(
    private val packageMonitor: PackageMonitor,
    private val installedAppsRepository: InstalledAppsRepository,
    private val apkInfoExtractor: InstallerInfoExtractor,
    private val platform: Platform,
    private val logger: KomiStoreLogger,
) {
    companion object {
        private const val PENDING_TIMEOUT_MS = 24 * 60 * 60 * 1000L

        private const val MIN_PLAUSIBLE_SCAN_SIZE = 20
    }

    suspend operator fun invoke(): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val appsInDb = installedAppsRepository.getAllInstalledApps().first()
                if (appsInDb.isEmpty()) return@withContext Result.success(Unit)

                if (packageMonitor.canEnumerateInstalledPackages()) {
                    reconcileEnumerable(appsInDb)
                } else {
                    resolveUntrackablePending(appsInDb)
                }

                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Sync failed: ${e.message}")
                Result.failure(e)
            }
        }

    private suspend fun resolveUntrackablePending(appsInDb: List<InstalledApp>) {
        val orphanPending =
            appsInDb.filter { it.isPendingInstall && it.pendingInstallFilePath == null }
        if (orphanPending.isEmpty()) {
            logger.info("Sync (no enumeration): nothing to resolve, 0 deleted")
            return
        }
        installedAppsRepository.executeInTransaction {
            orphanPending.forEach { app ->
                try {
                    installedAppsRepository.updatePendingStatus(app.packageName, false)
                    logger.info("Resolved un-confirmable pending install: ${app.packageName}")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    logger.error("Failed to resolve pending ${app.packageName}: ${e.message}")
                }
            }
        }
        logger.info("Sync (no enumeration): ${orphanPending.size} pending resolved, 0 deleted")
    }

    private suspend fun reconcileEnumerable(appsInDb: List<InstalledApp>) {
        val installedPackageNames = packageMonitor.getAllInstalledPackageNames()
        val now = System.currentTimeMillis()

        val deleteCandidates = mutableListOf<InstalledApp>()
        val staleCandidates = mutableListOf<InstalledApp>()
        val toResolvePending = mutableListOf<InstalledApp>()
        val toMigrate = mutableListOf<Pair<String, MigrationResult>>()
        val toSyncVersions = mutableListOf<InstalledApp>()
        val toClearStaleParkedFile = mutableListOf<InstalledApp>()

        appsInDb.forEach { app ->
            val isOnSystem = installedPackageNames.contains(app.packageName)
            when {
                app.isPendingInstall -> {
                    if (isOnSystem) {
                        toResolvePending.add(app)
                    } else if (now - app.installedAt > PENDING_TIMEOUT_MS) {
                        staleCandidates.add(app)
                    }
                }

                !isOnSystem -> deleteCandidates.add(app)

                app.installedVersionName == null ->
                    toMigrate.add(app.packageName to determineMigrationData(app))

                platform == Platform.ANDROID -> toSyncVersions.add(app)
            }
            if (isOnSystem && !app.isPendingInstall && app.pendingInstallFilePath != null) {
                toClearStaleParkedFile.add(app)
            }
        }

        guardUntrustworthyScan(
            appsInDb = appsInDb,
            installedPackageNames = installedPackageNames,
            deleteCandidates = deleteCandidates,
            staleCandidates = staleCandidates
        )

        val confirmedDeletes =
            deleteCandidates.filter { app ->
                val absent = presenceOf(app.packageName) is Presence.Absent
                if (!absent) {
                    logger.info("Kept ${app.packageName}: not positively absent (installed, hidden, or lookup failed)")
                }
                absent
            }
        val confirmedStaleDeletes = mutableListOf<InstalledApp>()
        staleCandidates.forEach { app ->
            when (presenceOf(app.packageName)) {
                is Presence.Absent -> confirmedStaleDeletes.add(app)
                is Presence.Present -> toResolvePending.add(app)
                is Presence.Unknown -> Unit
            }
        }

        val systemInfoByPackage =
            (toResolvePending.map { it.packageName } + toSyncVersions.map { it.packageName } +
                toClearStaleParkedFile.map { it.packageName })
                .toSet()
                .associateWith { (presenceOf(it) as? Presence.Present)?.info }

        // Every park cleanup waits for the commit: deleting a parked file is a side effect a
        // rolled-back transaction cannot undo, and for a row that is leaving the table the
        // disposal's outcome decides whether it may actually go.
        val parkedCleanups = mutableListOf<ParkedCleanup>()
        confirmedDeletes.forEach {
            parkedCleanups += ParkedCleanup(
                packageName = it.packageName,
                expectedPath = it.pendingInstallFilePath,
                deleteRow = true,
                reason = "uninstalled app",
            )
        }
        confirmedStaleDeletes.forEach {
            parkedCleanups += ParkedCleanup(
                packageName = it.packageName,
                expectedPath = it.pendingInstallFilePath,
                deleteRow = true,
                reason = "stale pending install (>24h)",
            )
        }
        toClearStaleParkedFile.forEach { app ->
            // A row on the system with the flag down but a parked file is not provably stale: a
            // failed or cancelled install lands exactly here (those paths lower the flag but keep
            // the pointer, and the UI still shows the file as ready to install), so the file only
            // goes when the system proves the target build already landed there.
            val resolution = app.pendingInstallResolution(systemInfoByPackage[app.packageName])
            val landed = resolution is PendingInstallResolution.Reached
            // The gate's proof is re-derived from data that can stop existing: the version name
            // did not move, the target is a rolling tag no name can match, and a cleared check
            // can leave no code behind either. A landing that was already proven once must not
            // strand its file just because the proof cannot be written twice: the parked file
            // itself still shows the system now runs its build, installed after it was written.
            val superseded =
                !landed &&
                    parkedFileSupersededBySystem(app, systemInfoByPackage[app.packageName])
            if (!landed && !superseded) {
                logger.info(
                    "Kept parked file for ${app.packageName}: the target is not proven on the system",
                )
                return@forEach
            }
            parkedCleanups += ParkedCleanup(
                packageName = app.packageName,
                expectedPath = app.pendingInstallFilePath,
                deleteRow = false,
                reason = if (landed) "stale parked file" else "superseded parked file",
            )
            logger.info("Queued stale parked install for discard: ${app.packageName}")
        }

        installedAppsRepository.executeInTransaction {
            toResolvePending.forEach {
                resolvePending(it, systemInfoByPackage[it.packageName], parkedCleanups)
            }
            toMigrate.forEach { (packageName, result) -> migrate(appsInDb, packageName, result) }
            toSyncVersions.forEach { syncVersion(it, systemInfoByPackage[it.packageName]) }
        }

        var droppedRows = 0
        parkedCleanups.forEach { cleanup ->
            try {
                val disposal =
                    installedAppsRepository.discardParkedInstall(cleanup.packageName, cleanup.expectedPath)
                if (cleanup.deleteRow) {
                    when (disposal) {
                        ParkedInstallDisposal.Discarded -> {
                            installedAppsRepository.deleteInstalledApp(cleanup.packageName)
                            droppedRows += 1
                            logger.info("Removed ${cleanup.reason}: ${cleanup.packageName}")
                        }
                        ParkedInstallDisposal.Retained ->
                            logger.info(
                                "Kept ${cleanup.packageName}: something is still parked for it, " +
                                    "so its row stays for the next sync",
                            )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to clean up the parked install for ${cleanup.packageName}: ${e.message}")
            }
        }

        logger.info(
            "Sync completed: $droppedRows rows dropped " +
                "(${confirmedDeletes.size} uninstalled + ${confirmedStaleDeletes.size} stale pending " +
                "candidates), ${toResolvePending.size} pending resolved, ${toMigrate.size} migrated, " +
                "${toSyncVersions.size} version-checked, " +
                "${toClearStaleParkedFile.size} stale parked files checked",
        )
    }

    private suspend fun parkedFileSupersededBySystem(
        app: InstalledApp,
        systemInfo: SystemPackageInfo?,
    ): Boolean {
        val path = app.pendingInstallFilePath ?: return false
        val fileInfo =
            try {
                apkInfoExtractor.extractPackageInfo(path)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("Could not inspect the parked file for ${app.packageName}: ${e.message}")
                null
            } ?: return false
        return parkedFileSuperseded(app.packageName, fileInfo, systemInfo)
    }

    private fun guardUntrustworthyScan(
        appsInDb: List<InstalledApp>,
        installedPackageNames: Set<String>,
        deleteCandidates: MutableList<InstalledApp>,
        staleCandidates: MutableList<InstalledApp>,
    ) {
        val nonPendingCount = appsInDb.count { !it.isPendingInstall }
        val recognizedCount =
            appsInDb.count { !it.isPendingInstall && installedPackageNames.contains(it.packageName) }

        val scanUntrustworthy =
            installedPackageNames.size < MIN_PLAUSIBLE_SCAN_SIZE ||
                (nonPendingCount >= 1 && recognizedCount * 2 < nonPendingCount)
        if (scanUntrustworthy) {
            logger.error(
                "Installed-apps sync: package scan untrustworthy (recognised $recognizedCount of " +
                    "$nonPendingCount tracked, scanSize=${installedPackageNames.size}). Skipping " +
                    "${deleteCandidates.size} deletions + ${staleCandidates.size} stale-pending " +
                    "removals to avoid wiping the library (GH#748).",
            )
            deleteCandidates.clear()
            staleCandidates.clear()
        }
    }

    private suspend fun resolvePending(
        app: InstalledApp,
        systemInfo: SystemPackageInfo?,
        parkedCleanups: MutableList<ParkedCleanup>,
    ) {
        try {
            val resolution = app.pendingInstallResolution(systemInfo)
            if (resolution !is PendingInstallResolution.Reached) {
                if (app.pendingInstallFilePath == null) {
                    // A flag with no park protects no file: it is the leftover of an install that
                    // stopped before parking, so it resolves instead of pinning the row in the
                    // pending group forever (the untrackable path resolves this shape too).
                    installedAppsRepository.updatePendingStatus(app.packageName, false)
                    logger.info("Resolved pending install without a parked file: ${app.packageName}")
                } else if (parkedFileSupersededBySystem(app, systemInfo)) {
                    // The gate's proof can be gone for good while the flag is still up: the
                    // version name did not move and the target is a rolling tag no name can
                    // match, so a successful self-update would pin its card forever. The parked
                    // file carries the re-proof the stale-file sweep already trusts — the system
                    // runs its build, installed after the file was written. The install's own run
                    // already recorded the version row, so settling the pending state is all that
                    // is left; the file goes through the shared discard queue.
                    installedAppsRepository.updateApp(app.clearPending())
                    logger.info("Resolved pending install by superseded parked file: ${app.packageName}")
                    parkedCleanups += ParkedCleanup(
                        packageName = app.packageName,
                        expectedPath = app.pendingInstallFilePath,
                        deleteRow = false,
                        reason = "superseded parked install",
                    )
                } else {
                    // No answer or no proof it landed: the file on disk is still what the user
                    // needs, so the park stands. Clearing the pointer would fall the card back to
                    // a download.
                    logger.info("Kept parked install (target not proven): ${app.packageName}")
                }
                return
            }
            installedAppsRepository.updateApp(app.resolvePendingFromSystem(resolution))
            logger.info(
                "Resolved pending install: ${app.packageName} " +
                    "(v${resolution.versionName}, code=${resolution.versionCode}, tag=${resolution.resolvedTag})",
            )
            // The file deletion that completes the discard is a side effect a rolled-back
            // transaction cannot undo, so it waits until the transaction has committed.
            parkedCleanups += ParkedCleanup(
                packageName = app.packageName,
                expectedPath = app.pendingInstallFilePath,
                deleteRow = false,
                reason = "resolved pending install",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to resolve pending ${app.packageName}: ${e.message}")
        }
    }

    private suspend fun migrate(
        appsInDb: List<InstalledApp>,
        packageName: String,
        migrationResult: MigrationResult,
    ) {
        try {
            val app = appsInDb.find { it.packageName == packageName } ?: return
            installedAppsRepository.updateApp(
                app.withMigratedVersionInfo(
                    versionName = migrationResult.versionName,
                    versionCode = migrationResult.versionCode,
                ),
            )
            logger.info(
                "Migrated $packageName: ${migrationResult.source} " +
                    "(versionName=${migrationResult.versionName}, code=${migrationResult.versionCode})",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to migrate $packageName: ${e.message}")
        }
    }

    private suspend fun syncVersion(app: InstalledApp, systemInfo: SystemPackageInfo?) {
        try {
            val local = systemInfo ?: return

            val binding = app.bindingStatusAgainst(local)
            val change = app.deviceChangeAgainst(local)

            if (binding is BindingStatus.Intact && change == DeviceChange.NONE) {
                logger.debug("Binding intact and unchanged for ${app.packageName}; no write")
                return
            }

            if (binding is BindingStatus.Broken) {
                logger.warn(
                    "Install binding broken for ${app.packageName}: ${binding.reason} " +
                        "(DB v${app.installedVersionName}(${app.installedVersionCode}) vs " +
                        "System v${local.versionName}(${local.versionCode}))",
                )
            }

            if (change != DeviceChange.NONE) {
                val observed = app.observeExternalInstall(
                    versionName = local.versionName,
                    versionCode = local.versionCode,
                    signingFingerprint = local.signingFingerprint,
                )
                installedAppsRepository.updateApp(observed)

                val action =
                    when (change) {
                        DeviceChange.DOWNGRADE -> "downgrade"
                        DeviceChange.SIGNER_CHANGE ->
                            "signer change " +
                                "(stored=${app.signingFingerprint} now=${local.signingFingerprint})"
                        DeviceChange.NONE,
                        DeviceChange.VERSION_CHANGE,
                        -> "external update"
                    }
                logger.info(
                    "Detected $action for ${app.packageName}: " +
                        "DB v${app.installedVersionName}(${app.installedVersionCode}) → " +
                        "System v${local.versionName}(${local.versionCode}), " +
                        "binding=${binding::class.simpleName}, updateAvailable=${observed.isUpdateAvailable}",
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to sync version for ${app.packageName}: ${e.message}")
        }
    }

    private suspend fun presenceOf(packageName: String): Presence =
        runCatching { packageMonitor.getInstalledPackageInfo(packageName) }
            .fold(
                onSuccess = { info -> if (info == null) Presence.Absent else Presence.Present(info) },
                onFailure = { e ->
                    if (e is CancellationException) throw e
                    logger.warn("Package lookup failed for $packageName; treated as unknown: ${e.message}")
                    Presence.Unknown
                },
            )

    private sealed interface Presence {
        data object Absent : Presence

        data class Present(val info: SystemPackageInfo) : Presence

        data object Unknown : Presence
    }

    private suspend fun determineMigrationData(app: InstalledApp): MigrationResult =
        if (platform == Platform.ANDROID) {
            val systemInfo = (presenceOf(app.packageName) as? Presence.Present)?.info
            if (systemInfo != null) {
                MigrationResult(
                    versionName = systemInfo.versionName,
                    versionCode = systemInfo.versionCode,
                    source = "system package manager",
                )
            } else {
                MigrationResult(
                    versionName = app.installedVersion,
                    versionCode = 0L,
                    source = "fallback to release tag",
                )
            }
        } else {
            MigrationResult(
                versionName = app.installedVersion,
                versionCode = 0L,
                source = "desktop fallback to release tag",
            )
        }

    private data class MigrationResult(
        val versionName: String,
        val versionCode: Long,
        val source: String,
    )

    // A park cleanup that has to wait for the commit, and, when the row is leaving the table, the
    // path the disposal has to prove gone before the row may follow it.
    private data class ParkedCleanup(
        val packageName: String,
        val expectedPath: String?,
        val deleteRow: Boolean,
        val reason: String,
    )
}
