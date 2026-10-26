package zed.rainxch.core.data.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.SQLException
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import zed.rainxch.core.data.local.db.dao.ExternalLinkDao
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.SystemPackagesException
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.InstalledBuild
import zed.rainxch.core.domain.model.installation.ParkedInstallDisposal
import zed.rainxch.core.domain.model.installation.PendingInstallResolution
import zed.rainxch.core.domain.model.installation.SystemPackageInfo
import zed.rainxch.core.domain.model.installation.externalInstallUpdateFlag
import zed.rainxch.core.domain.model.installation.pendingInstallResolution
import zed.rainxch.core.domain.model.installation.tagForObservedBuild
import zed.rainxch.core.domain.model.installation.withSettledInstallIdentity
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.ExternalLinkState
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.system.SystemInstallSerializer
import zed.rainxch.core.domain.utils.VersionVerdict
import zed.rainxch.core.domain.utils.resolveExternalInstallVerdict
import kotlin.coroutines.cancellation.CancellationException

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

        when (intent?.action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            -> {
                scope.launch(logsFailure { "PackageEventReceiver error for $packageName" }) {
                    onPackageInstalled(packageName)
                }
            }

            Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                scope.launch(logsFailure { "PackageEventReceiver remove error for $packageName" }) {
                    onPackageRemoved(packageName)
                }
            }
        }
    }

    // These coroutines run unattended on a scope without a handler, so a failure is logged here instead of crashing the app.
    private fun logsFailure(message: () -> String): CoroutineExceptionHandler =
        CoroutineExceptionHandler { _, e -> Logger.w(e, message = message) }

    private suspend fun onPackageInstalled(packageName: String) {
        getSystemInstallSerializer().markCompleted(packageName)

        try {
            reconcileInstalled(packageName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: LocalStoreException) {
            logInstallFailure(packageName, e)
        } catch (e: SystemPackagesException) {
            logInstallFailure(packageName, e)
        } catch (e: SecurityException) {
            logInstallFailure(packageName, e)
        } catch (e: IllegalStateException) {
            logInstallFailure(packageName, e)
        }

        rescanInBackground(packageName) { "Delta scan failed for $packageName" }
    }

    private fun rescanInBackground(
        packageName: String,
        failure: () -> String,
    ) {
        getBackstopScope().launch(logsFailure(failure)) {
            if (shouldRescan(packageName)) {
                getExternalImport().runDeltaScan(setOf(packageName))
            }
        }
    }

    private fun logInstallFailure(
        packageName: String,
        e: Exception,
    ) {
        Logger.e { "PackageEventReceiver error for $packageName: ${e.message}" }
    }

    private suspend fun reconcileInstalled(packageName: String) {
        val repo = getRepository()
        val monitor = getMonitor()
        val app = repo.getAppByPackage(packageName) ?: return

        if (!app.isPendingInstall) {
            handleExternalInstall(packageName, app, repo, monitor)
            return
        }
        when (val resolution = app.pendingInstallResolution(monitor.getInstalledPackageInfo(packageName))) {
            PendingInstallResolution.Keep -> {
                Logger.i {
                    "Kept parked install via broadcast (target not proven): $packageName"
                }
            }

            is PendingInstallResolution.Reached -> {
                settleReachedInstall(packageName, app, repo, resolution)
            }
        }
    }

    private suspend fun settleReachedInstall(
        packageName: String,
        app: InstalledApp,
        repo: InstalledAppsRepository,
        resolution: PendingInstallResolution.Reached,
    ) {
        val settled = app.withSettledInstallIdentity(resolution.versionCode)
        repo.updateAppVersion(
            packageName = packageName,
            build =
                InstalledBuild(
                    tag = resolution.resolvedTag,
                    releaseId = settled.installedReleaseId,
                    assetId = settled.installedAssetId,
                    assetDigest = settled.installedAssetDigest,
                    releasePublishedAt = settled.installedReleasePublishedAt,
                    assetName = app.latestAssetName ?: "",
                    assetUrl = app.latestAssetUrl ?: "",
                    versionName = resolution.versionName,
                    versionCode = resolution.versionCode,
                    signingFingerprint = app.signingFingerprint,
                ),
            isPendingInstall = true,
        )
        try {
            val disposal = repo.discardParkedInstall(packageName, app.pendingInstallFilePath)
            if (disposal == ParkedInstallDisposal.Retained) {
                Logger.w {
                    "Parked install not fully discarded for $packageName; " +
                        "the next sync will retry"
                }
            }
        } catch (e: LocalStoreException) {
            Logger.e(e) { "Parked install not discarded for $packageName; the next sync will retry" }
        } catch (e: SecurityException) {
            Logger.e(e) { "Parked install not discarded for $packageName; the next sync will retry" }
        }
        Logger.i {
            "Update confirmed via broadcast: $packageName " +
                "(v${resolution.versionName}, tag=${resolution.resolvedTag})"
        }
    }

    // An unreadable row counts as untracked and unlinked, so the scan still runs.
    private suspend fun shouldRescan(packageName: String): Boolean {
        val tracked =
            try {
                getRepository().getAppByPackage(packageName)
            } catch (e: LocalStoreException) {
                Logger.w(e) { "Tracked lookup failed for $packageName; rescanning" }
                null
            }
        if (tracked != null) return false
        val state = externalLinkStateOrNull(packageName)
        return state == null ||
            (state != ExternalLinkState.MATCHED.name && state != ExternalLinkState.NEVER_ASK.name)
    }

    private suspend fun externalLinkStateOrNull(packageName: String): String? =
        try {
            getExternalLinkDao().get(packageName)?.state
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLException) {
            logLinkLookupFailure(packageName, e)
        } catch (e: IllegalStateException) {
            logLinkLookupFailure(packageName, e)
        }

    private fun logLinkLookupFailure(
        packageName: String,
        e: RuntimeException,
    ): String? {
        Logger.w(e) { "External link lookup failed for $packageName; rescanning" }
        return null
    }

    private suspend fun handleExternalInstall(
        packageName: String,
        app: InstalledApp,
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
            // AutoUpdateWorker checks the signer only when one is stored, so an unreadable (null) signer never erases it.
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

        revalidateExternalInstall(packageName, systemInfo, repo)
    }

    private fun revalidateExternalInstall(
        packageName: String,
        systemInfo: SystemPackageInfo,
        repo: InstalledAppsRepository,
    ) {
        getBackstopScope().launch(logsFailure { "External-install re-validation failed for $packageName" }) {
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
        }
    }

    private suspend fun onPackageRemoved(packageName: String) {
        getSystemInstallSerializer().markCompleted(packageName)

        try {
            val repo = getRepository()
            val parkedPath = repo.getAppByPackage(packageName)?.pendingInstallFilePath
            val mayDropRow = parkedPath == null || discardParkedFile(repo, packageName, parkedPath)
            if (mayDropRow) {
                repo.deleteInstalledApp(packageName)
            }
            try {
                getExternalImport().unlink(packageName)
            } catch (e: LocalStoreException) {
                Logger.w(e) { "External link cleanup failed for $packageName; scheduling retry" }
                retryUnlink(packageName)
            }
            if (mayDropRow) {
                Logger.i { "Removed uninstalled app via broadcast: $packageName" }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: LocalStoreException) {
            logRemoveFailure(packageName, e)
        } catch (e: SecurityException) {
            logRemoveFailure(packageName, e)
        } catch (e: IllegalStateException) {
            logRemoveFailure(packageName, e)
        }
    }

    private fun logRemoveFailure(
        packageName: String,
        e: Exception,
    ) {
        Logger.e { "PackageEventReceiver remove error for $packageName: ${e.message}" }
    }

    private suspend fun discardParkedFile(
        repo: InstalledAppsRepository,
        packageName: String,
        parkedPath: String,
    ): Boolean {
        val disposal =
            try {
                repo.discardParkedInstall(packageName, parkedPath)
            } catch (e: LocalStoreException) {
                Logger.e(e) { "Parked file not discarded for $packageName; the row stays for the next sync" }
                null
            } catch (e: SecurityException) {
                Logger.e(e) { "Parked file not discarded for $packageName; the row stays for the next sync" }
                null
            }
        if (disposal == ParkedInstallDisposal.Retained) {
            Logger.w {
                "Parked file for $packageName survived the uninstall; the row stays for the next sync"
            }
        }
        return disposal == ParkedInstallDisposal.Discarded
    }

    private fun retryUnlink(packageName: String) {
        val finalFailure = {
            "External link cleanup final failure for $packageName; " +
                "row may persist until next periodic scan"
        }
        getBackstopScope().launch(logsFailure(finalFailure)) {
            delay(UNLINK_RETRY_DELAY_MS)
            getExternalImport().unlink(packageName)
            Logger.i { "External link cleanup retry succeeded for $packageName" }
            rescanInBackground(packageName) { "Post-retry delta scan failed for $packageName" }
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
