package zed.rainxch.core.domain.use_cases

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.installation.BindingStatus
import zed.rainxch.core.domain.model.installation.DeviceChange
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.ParkedInstallDisposal
import zed.rainxch.core.domain.model.installation.PendingInstallResolution
import zed.rainxch.core.domain.model.installation.SystemPackageInfo
import zed.rainxch.core.domain.model.installation.bindingStatusAgainst
import zed.rainxch.core.domain.model.installation.deviceChangeAgainst
import zed.rainxch.core.domain.model.installation.hasPendingIdentity
import zed.rainxch.core.domain.model.installation.installedAfterBinding
import zed.rainxch.core.domain.model.installation.installedAfterPark
import zed.rainxch.core.domain.model.installation.observeExternalInstall
import zed.rainxch.core.domain.model.installation.pendingInstallResolution
import zed.rainxch.core.domain.model.installation.resolvePendingFromProvenEvent
import zed.rainxch.core.domain.model.installation.resolvePendingFromSystem
import zed.rainxch.core.domain.model.installation.withMigratedVersionInfo
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.PackageMonitor

class SyncInstalledAppsUseCase(
    private val packageMonitor: PackageMonitor,
    private val installedAppsRepository: InstalledAppsRepository,
    private val platform: Platform,
    private val logger: KomiStoreLogger,
) {
    companion object {
        // Counted from the hand-off to the installer, never from the download: a park the
        // user only deferred has no start time and stays until the user acts.
        private const val PENDING_INSTALL_TIMEOUT_MS = 24 * 60 * 60 * 1000L

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
                    } else {
                        // A park that was never handed to the installer is waiting for the
                        // user, not stale: only a handed-off install can time out.
                        val startedAt = app.pendingInstallStartedAt
                        if (startedAt != null && now - startedAt > PENDING_INSTALL_TIMEOUT_MS) {
                            staleCandidates.add(app)
                        }
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
            val resolution = app.pendingInstallResolution(systemInfoByPackage[app.packageName])
            if (resolution !is PendingInstallResolution.Reached) {
                logger.info(
                    "Kept parked file for ${app.packageName}: the target is not proven on the system",
                )
                return@forEach
            }
            parkedCleanups += ParkedCleanup(
                packageName = app.packageName,
                expectedPath = app.pendingInstallFilePath,
                deleteRow = false,
                reason = "stale parked file",
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
            if (resolution is PendingInstallResolution.Reached) {
                installedAppsRepository.updateApp(
                    app.resolvePendingFromSystem(
                        resolution,
                        at = systemInfo?.lastUpdateTime ?: System.currentTimeMillis(),
                    ),
                )
                logger.info(
                    "Resolved pending install: ${app.packageName} " +
                        "(v${resolution.versionName}, code=${resolution.versionCode}, tag=${resolution.resolvedTag})",
                )
                parkedCleanups += ParkedCleanup(
                    packageName = app.packageName,
                    expectedPath = app.pendingInstallFilePath,
                    deleteRow = false,
                    reason = "resolved pending install",
                )
            } else {
                resolveUnreachedPending(app, systemInfo, parkedCleanups)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to resolve pending ${app.packageName}: ${e.message}")
        }
    }

    private suspend fun resolveUnreachedPending(
        app: InstalledApp,
        systemInfo: SystemPackageInfo?,
        parkedCleanups: MutableList<ParkedCleanup>,
    ) {
        // Version fields did not prove the target, but PackageManager's install time did:
        // the park was handed to the installer after the version was recorded and the system
        // installed something after that hand-off.
        // The parked build's own versionCode is the minimum proof. A legacy park without it
        // stays parked; it must not inherit an unrelated install.
        val parkedCode = app.pendingInstallVersionCode
        val provedBySystemInstall =
            app.hasPendingIdentity &&
                systemInfo != null &&
                parkedCode != null &&
                systemInfo.versionCode == parkedCode &&
                app.installedAfterPark(systemInfo)
        val startedAt = app.pendingInstallStartedAt
        val abandoned =
            startedAt != null && System.currentTimeMillis() - startedAt > PENDING_INSTALL_TIMEOUT_MS
        when {
            provedBySystemInstall -> {
                settleFromSystemInstall(
                    repository = installedAppsRepository,
                    logger = logger,
                    app = app,
                    systemInfo = requireNotNull(systemInfo),
                    parkedCleanups = parkedCleanups,
                )
            }

            abandoned -> {
                // The install never reached the system (cancelled or dropped session). Drop every
                // trace of the park so the next unrelated install cannot adopt its identity.
                installedAppsRepository.updatePendingStatus(app.packageName, false)
                installedAppsRepository.setPendingInstallFilePath(app.packageName, null)
                parkedCleanups += ParkedCleanup(
                    packageName = app.packageName,
                    expectedPath = app.pendingInstallFilePath,
                    deleteRow = false,
                    reason = "abandoned pending install",
                )
                logger.info("Dropped an abandoned parked install: ${app.packageName}")
            }

            app.pendingInstallFilePath == null &&
                (app.pendingInstallVersionCode == null || !app.hasPendingIdentity) -> {
                // No file means there is nothing that could ever be installed. This also
                // clears legacy rows that predate pendingInstallVersionCode; rows that still
                // have a parked file are left for the user/settler.
                installedAppsRepository.updatePendingStatus(app.packageName, false)
                installedAppsRepository.setPendingInstallFilePath(app.packageName, null)
                logger.info("Resolved pending install without a parked file: ${app.packageName}")
            }

            app.hasPendingIdentity -> {
                logger.info("Kept parked identity awaiting the install event: ${app.packageName}")
            }

            else -> {
                logger.info("Kept parked install (target not proven): ${app.packageName}")
            }
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

            val current =
                if (!app.isPendingInstall && !app.hasPendingIdentity && app.installedAfterBinding(local)) {
                    // An install happened after the last recorded settlement. Drop the binding
                    // instead of trusting it (#998), and make the local snapshot match the
                    // cleared row so the following observeExternalInstall cannot write the old
                    // identity back.
                    installedAppsRepository.clearInstallBinding(app.packageName)
                    logger.info(
                        "Dropped the install binding for ${app.packageName}: " +
                            "an install happened after the recorded settlement",
                    )
                    app.withoutBinding()
                } else {
                    app
                }

            val binding = current.bindingStatusAgainst(local)
            val change = current.deviceChangeAgainst(local)

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
                val observed = current.observeExternalInstall(
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

}

// The version fields did not prove the target, but PackageManager's install time did. Settle by
// the broadcast's proof and record the install time as the settlement so the next sync does not
// read the same install as foreign and drop the binding it just adopted.
private suspend fun settleFromSystemInstall(
    repository: InstalledAppsRepository,
    logger: KomiStoreLogger,
    app: InstalledApp,
    systemInfo: SystemPackageInfo,
    parkedCleanups: MutableList<ParkedCleanup>,
) {
    val tag = app.pendingInstallVersion ?: app.latestVersion ?: systemInfo.versionName
    repository.updateApp(
        app.resolvePendingFromProvenEvent(
            tag = tag,
            versionName = systemInfo.versionName,
            versionCode = systemInfo.versionCode,
            at = systemInfo.lastUpdateTime ?: System.currentTimeMillis(),
        ),
    )
    parkedCleanups += ParkedCleanup(
        packageName = app.packageName,
        expectedPath = app.pendingInstallFilePath,
        deleteRow = false,
        reason = "same-version install proved by lastUpdateTime",
    )
    logger.info("Resolved same-version install via lastUpdateTime: ${app.packageName}")
}

private data class ParkedCleanup(
    val packageName: String,
    val expectedPath: String?,
    val deleteRow: Boolean,
    val reason: String,
)


private fun InstalledApp.withoutBinding(): InstalledApp =
    copy(
        installedReleaseId = null,
        installedAssetId = null,
        installedAssetDigest = null,
        installedReleasePublishedAt = null,
        installedAssetName = null,
        installedAssetUrl = null,
        boundAt = null,
    )
