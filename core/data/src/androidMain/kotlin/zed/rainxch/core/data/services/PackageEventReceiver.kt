package zed.rainxch.core.data.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import zed.rainxch.core.data.local.db.dao.ExternalLinkDao
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.ParkedInstallDisposal
import zed.rainxch.core.domain.model.installation.PendingInstallResolution
import zed.rainxch.core.domain.model.installation.SystemPackageInfo
import zed.rainxch.core.domain.model.installation.externalInstallUpdateFlag
import zed.rainxch.core.domain.model.installation.hasPendingIdentity
import zed.rainxch.core.domain.model.installation.pendingInstallResolution
import zed.rainxch.core.domain.model.installation.resolvePendingFromProvenEvent
import zed.rainxch.core.domain.model.installation.tagForObservedBuild
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.ExternalLinkState
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.system.SystemInstallSerializer
import zed.rainxch.core.domain.utils.VersionVerdict
import zed.rainxch.core.domain.utils.resolveExternalInstallVerdict
import java.util.concurrent.ConcurrentHashMap

class PackageEventReceiver() :
    BroadcastReceiver(),
    KoinComponent {
    private val installedAppsRepositoryKoin: InstalledAppsRepository by inject()
    private val packageMonitorKoin: PackageMonitor by inject()
    private val appScopeKoin: CoroutineScope by inject()
    private val externalImportRepositoryKoin: ExternalImportRepository by inject()
    private val externalLinkDaoKoin: ExternalLinkDao by inject()
    private val systemInstallSerializerKoin: SystemInstallSerializer by inject()

    private var explicitRepository: InstalledAppsRepository? = null
    private var explicitMonitor: PackageMonitor? = null
    private var explicitExternalImport: ExternalImportRepository? = null
    private var explicitExternalLinkDao: ExternalLinkDao? = null
    private var explicitAppScope: CoroutineScope? = null
    private var explicitSystemInstallSerializer: SystemInstallSerializer? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val eventLocks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(packageName: String): Mutex = eventLocks.getOrPut(packageName) { Mutex() }

    constructor(
        installedAppsRepository: InstalledAppsRepository,
        packageMonitor: PackageMonitor,
        externalImportRepository: ExternalImportRepository,
        externalLinkDao: ExternalLinkDao,
        appScope: CoroutineScope,
        systemInstallSerializer: SystemInstallSerializer,
    ) : this() {
        this.explicitRepository = installedAppsRepository
        this.explicitMonitor = packageMonitor
        this.explicitExternalImport = externalImportRepository
        this.explicitExternalLinkDao = externalLinkDao
        this.explicitAppScope = appScope
        this.explicitSystemInstallSerializer = systemInstallSerializer
    }

    private fun getRepository(): InstalledAppsRepository = explicitRepository ?: installedAppsRepositoryKoin

    private fun getMonitor(): PackageMonitor = explicitMonitor ?: packageMonitorKoin

    private fun getExternalImport(): ExternalImportRepository = explicitExternalImport ?: externalImportRepositoryKoin

    private fun getExternalLinkDao(): ExternalLinkDao = explicitExternalLinkDao ?: externalLinkDaoKoin

    private fun getSystemInstallSerializer(): SystemInstallSerializer = explicitSystemInstallSerializer ?: systemInstallSerializerKoin

    private fun getBackstopScope(): CoroutineScope = explicitAppScope ?: runCatching { appScopeKoin }.getOrElse { scope }

    override fun onReceive(
        context: Context?,
        intent: Intent?,
    ) {
        val packageName =
            intent?.data?.schemeSpecificPart
                ?: if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                    context?.packageName
                } else {
                    null
                }
                ?: return

        Logger.d { "PackageEventReceiver: ${intent?.action} for $packageName" }

        // Keep the process alive until the DB settlement finishes. Doing the work in a
        // fire-and-forget launch lets Android kill the process as soon as onReceive returns.
        val pendingResult = goAsync()
        scope.launch {
            try {
                lockFor(packageName).withLock {
                    when (intent?.action) {
                        Intent.ACTION_PACKAGE_ADDED,
                        Intent.ACTION_PACKAGE_REPLACED,
                        Intent.ACTION_MY_PACKAGE_REPLACED,
                        -> onPackageInstalled(packageName)

                        Intent.ACTION_PACKAGE_FULLY_REMOVED -> onPackageRemoved(packageName)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e { "PackageEventReceiver: Failed to handle ${intent?.action}: ${e.message}" }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun onPackageInstalled(packageName: String) {
        getSystemInstallSerializer().markCompleted(packageName)

        try {
            val repo = getRepository()
            val monitor = getMonitor()
            val app = repo.getAppByPackage(packageName)

            if (app != null) {
                handleTrackedInstall(packageName, app, repo, monitor)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e { "PackageEventReceiver error for $packageName: ${e.message}" }
        }

        getBackstopScope().launch {
            runCatching {
                val rescan = shouldRescan(packageName)
                if (rescan) {
                    getExternalImport().runDeltaScan(setOf(packageName))
                }
            }.onFailure {
                Logger.w(it) { "Delta scan failed for $packageName" }
            }
        }
    }

    private suspend fun shouldRescan(packageName: String): Boolean {
        val tracked =
            runCatching { getRepository().getAppByPackage(packageName) }
                .getOrNull()
        if (tracked != null) return false
        val link = runCatching { getExternalLinkDao().get(packageName) }.getOrNull()
        val state = link?.state ?: return true
        return state != ExternalLinkState.MATCHED.name &&
            state != ExternalLinkState.NEVER_ASK.name
    }

    private suspend fun handleTrackedInstall(
        packageName: String,
        app: InstalledApp,
        repo: InstalledAppsRepository,
        monitor: PackageMonitor,
    ) {
        if (!app.isPendingInstall && !app.hasPendingIdentity) {
            handleExternalInstall(packageName, app, repo, monitor)
            return
        }
        if (app.pendingInstallStartedAt == null) {
            // A park that was never handed to an installer cannot be settled by an
            // unrelated install event: treat that event as an external install instead.
            handleExternalInstall(packageName, app, repo, monitor)
            return
        }
        val systemInfo = monitor.getInstalledPackageInfo(packageName)
        val resolution = app.pendingInstallResolution(systemInfo)
        val tag =
            when (resolution) {
                is PendingInstallResolution.Reached -> {
                    resolution.resolvedTag
                }

                // Keep means the parked target is not proven on the device. Never settle a
                // park from an unrelated install; leave it for a real proof.
                PendingInstallResolution.Keep -> {
                    null
                }
            }
        if (tag == null || systemInfo == null) {
            Logger.i { "Kept parked install via broadcast (target not proven): $packageName" }
        } else {
            settlePendingFromEvent(packageName, app, repo, tag, systemInfo)
        }
    }

    private suspend fun settlePendingFromEvent(
        packageName: String,
        app: InstalledApp,
        repo: InstalledAppsRepository,
        tag: String,
        systemInfo: SystemPackageInfo,
    ) {
        val settled =
            app.resolvePendingFromProvenEvent(
                tag = tag,
                versionName = systemInfo.versionName,
                versionCode = systemInfo.versionCode,
                at = systemInfo.lastUpdateTime ?: System.currentTimeMillis(),
            )
        repo.updateApp(settled)
        try {
            val disposal = repo.discardParkedInstall(packageName, app.pendingInstallFilePath)
            if (disposal == ParkedInstallDisposal.Retained) {
                Logger.w { "Parked install not fully discarded for $packageName; the next sync will retry" }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e) { "Parked install not discarded for $packageName; the next sync will retry" }
        }
        Logger.i {
            "Update settled via broadcast: $packageName (v${systemInfo.versionName}, tag=$tag)"
        }
    }

    private suspend fun handleExternalInstall(
        packageName: String,
        app: zed.rainxch.core.domain.model.installation.InstalledApp,
        repo: InstalledAppsRepository,
        monitor: PackageMonitor,
    ) {
        val systemInfo = monitor.getInstalledPackageInfo(packageName) ?: return
        val versionChanged =
            systemInfo.versionCode != app.installedVersionCode ||
                systemInfo.versionName != app.installedVersionName
        if (!versionChanged) {
            Logger.d {
                "Broadcast touch with no version change: $packageName (v${systemInfo.versionName})"
            }
            return
        }

        val verdict =
            resolveExternalInstallVerdict(
                app = app,
                newVersionName = systemInfo.versionName,
                newVersionCode = systemInfo.versionCode,
            )

        val newIsUpdateAvailable =
            when (verdict) {
                VersionVerdict.UP_TO_DATE -> false
                VersionVerdict.UPDATE_AVAILABLE -> true
                VersionVerdict.UNKNOWN -> app.isUpdateAvailable
            }

        val installedSigner = systemInfo.signingFingerprint

        repo.executeInTransaction {
            repo.updateInstalledVersion(
                packageName = packageName,
                installedVersion = app.tagForObservedBuild(systemInfo.versionName, systemInfo.versionCode),
                installedVersionName = systemInfo.versionName,
                installedVersionCode = systemInfo.versionCode,
                isUpdateAvailable = newIsUpdateAvailable,
            )
            // AutoUpdateWorker checks the signer only when one is stored, so an unreadable
            // signer (null) must never erase it.
            if (installedSigner != null && !installedSigner.equals(app.signingFingerprint, ignoreCase = true)) {
                repo.updateSigningFingerprint(packageName, installedSigner)
            }
            repo.clearInstallBinding(packageName)
        }

        Logger.i {
            "External version change via broadcast: $packageName " +
                "DB v${app.installedVersionName}(${app.installedVersionCode}) → " +
                "System v${systemInfo.versionName}(${systemInfo.versionCode}), " +
                "verdict=$verdict, updateAvailable=$newIsUpdateAvailable"
        }

        getBackstopScope().launch {
            try {
                repo.checkForUpdates(packageName)
                val refreshed = repo.getAppByPackage(packageName)
                if (refreshed != null) {
                    repo.updateInstalledVersion(
                        packageName = packageName,
                        installedVersion = refreshed.tagForObservedBuild(systemInfo.versionName, systemInfo.versionCode),
                        installedVersionName = systemInfo.versionName,
                        installedVersionCode = systemInfo.versionCode,
                        isUpdateAvailable =
                            refreshed.externalInstallUpdateFlag(
                                newVersionName = systemInfo.versionName,
                                newVersionCode = systemInfo.versionCode,
                            ),
                    )
                }
                Logger.d {
                    "External-install re-validation completed for $packageName"
                }
            } catch (e: Exception) {
                Logger.w {
                    "External-install re-validation failed for $packageName: ${e.message}"
                }
            }
        }
    }

    private suspend fun onPackageRemoved(packageName: String) {
        getSystemInstallSerializer().markCompleted(packageName)

        try {
            val repo = getRepository()
            val parkedPath = repo.getAppByPackage(packageName)?.pendingInstallFilePath
            var mayDropRow = parkedPath == null
            if (parkedPath != null) {
                val disposal =
                    try {
                        repo.discardParkedInstall(packageName, parkedPath)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.e(e) { "Parked file not discarded for $packageName; the row stays for the next sync" }
                        null
                    }
                mayDropRow = disposal == ParkedInstallDisposal.Discarded
                if (disposal == ParkedInstallDisposal.Retained) {
                    Logger.w {
                        "Parked file for $packageName survived the uninstall; the row stays for the next sync"
                    }
                }
            }
            if (mayDropRow) {
                repo.deleteInstalledApp(packageName)
            }
            runCatching { getExternalImport().unlink(packageName) }
                .onFailure { initialError ->
                    Logger.w(initialError) { "External link cleanup failed for $packageName; scheduling retry" }

                    getBackstopScope().launch {
                        kotlinx.coroutines.delay(UNLINK_RETRY_DELAY_MS)
                        runCatching { getExternalImport().unlink(packageName) }
                            .onSuccess {
                                Logger.i { "External link cleanup retry succeeded for $packageName" }

                                runCatching {
                                    if (shouldRescan(packageName)) {
                                        getExternalImport().runDeltaScan(setOf(packageName))
                                    }
                                }.onFailure { e ->
                                    Logger.w(e) { "Post-retry delta scan failed for $packageName" }
                                }
                            }.onFailure { retryError ->
                                Logger.w(retryError) {
                                    "External link cleanup final failure for $packageName; " +
                                        "row may persist until next periodic scan"
                                }
                            }
                    }
                }
            if (mayDropRow) {
                Logger.i { "Removed uninstalled app via broadcast: $packageName" }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e { "PackageEventReceiver remove error for $packageName: ${e.message}" }
        }
    }

    companion object {
        private const val UNLINK_RETRY_DELAY_MS: Long = 1_000

        fun createIntentFilter(): IntentFilter =
            IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
                addAction(Intent.ACTION_MY_PACKAGE_REPLACED)
                addDataScheme("package")
            }
    }
}
