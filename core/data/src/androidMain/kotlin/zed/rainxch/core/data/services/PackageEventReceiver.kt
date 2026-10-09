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
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import zed.rainxch.core.data.local.db.dao.ExternalLinkDao
import zed.rainxch.core.domain.model.installation.ParkedInstallDisposal
import zed.rainxch.core.domain.model.installation.PendingInstallResolution
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

    private fun getExternalImport(): ExternalImportRepository =
        explicitExternalImport ?: externalImportRepositoryKoin

    private fun getExternalLinkDao(): ExternalLinkDao =
        explicitExternalLinkDao ?: externalLinkDaoKoin

    private fun getSystemInstallSerializer(): SystemInstallSerializer =
        explicitSystemInstallSerializer ?: systemInstallSerializerKoin

    private fun getBackstopScope(): CoroutineScope =

        explicitAppScope ?: runCatching { appScopeKoin }.getOrElse { scope }

    override fun onReceive(
        context: Context?,
        intent: Intent?,
    ) {

        val packageName = intent?.data?.schemeSpecificPart
            ?: if (intent?.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                context?.packageName
            } else {
                null
            }
            ?: return

        Logger.d { "PackageEventReceiver: ${intent?.action} for $packageName" }

        try {
            when (intent?.action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REPLACED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
                -> {
                    scope.launch { onPackageInstalled(packageName) }
                }

                Intent.ACTION_PACKAGE_FULLY_REMOVED -> {
                    scope.launch { onPackageRemoved(packageName) }
                }
            }
        } catch (e: Exception) {
            Logger.e { "PackageEventReceiver: Failed to handle ${intent?.action}: ${e.message}" }
        }
    }

    private suspend fun onPackageInstalled(packageName: String) {

        getSystemInstallSerializer().markCompleted(packageName)

        try {
            val repo = getRepository()
            val monitor = getMonitor()
            val app = repo.getAppByPackage(packageName)

            if (app != null) {
                if (app.isPendingInstall) {
                    val resolution =
                        app.pendingInstallResolution(monitor.getInstalledPackageInfo(packageName))
                    when (resolution) {
                        PendingInstallResolution.Keep ->
                            Logger.i {
                                "Kept parked install via broadcast (target not proven): $packageName"
                            }

                        is PendingInstallResolution.Reached -> {
                            val settled = app.withSettledInstallIdentity(resolution.versionCode)
                            // The flag stays true on purpose: the discard below ends the
                            // pending state, and a file that survives its delete must leave
                            // the row pending for the next sync's retry.
                            repo.updateAppVersion(
                                packageName = packageName,
                                newTag = resolution.resolvedTag,
                                newReleaseId = settled.installedReleaseId,
                                newAssetId = settled.installedAssetId,
                                newAssetDigest = settled.installedAssetDigest,
                                newAssetName = app.latestAssetName ?: "",
                                newAssetUrl = app.latestAssetUrl ?: "",
                                newVersionName = resolution.versionName,
                                newVersionCode = resolution.versionCode,
                                signingFingerprint = app.signingFingerprint,
                                isPendingInstall = true,
                            )
                            // The discard drops the pointer and the file together, scoped to the
                            // park this broadcast looked at. Its failure is not fatal: the row
                            // keeps the park and the next sync retries.
                            try {
                                val disposal =
                                    repo.discardParkedInstall(packageName, app.pendingInstallFilePath)
                                if (disposal == ParkedInstallDisposal.Retained) {
                                    Logger.w {
                                        "Parked install not fully discarded for $packageName; " +
                                            "the next sync will retry"
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Logger.e(e) {
                                    "Parked install not discarded for $packageName; the next sync will retry"
                                }
                            }
                            Logger.i {
                                "Update confirmed via broadcast: $packageName " +
                                    "(v${resolution.versionName}, tag=${resolution.resolvedTag})"
                            }
                        }
                    }
                } else {
                    handleExternalInstall(packageName, app, repo, monitor)
                }
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
        val tracked = runCatching { getRepository().getAppByPackage(packageName) }
            .getOrNull()
        if (tracked != null) return false
        val link = runCatching { getExternalLinkDao().get(packageName) }.getOrNull()
        val state = link?.state ?: return true
        return state != ExternalLinkState.MATCHED.name &&
            state != ExternalLinkState.NEVER_ASK.name
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

        repo.executeInTransaction {
            repo.updateInstalledVersion(
                packageName = packageName,
                installedVersion = app.tagForObservedBuild(systemInfo.versionName, systemInfo.versionCode),
                installedVersionName = systemInfo.versionName,
                installedVersionCode = systemInfo.versionCode,
                isUpdateAvailable = newIsUpdateAvailable,
            )
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
            // The row is the only handle to a parked file; if one is named here, it has to go with
            // the row or become an orphan. A file that survives keeps the row so the next sync's
            // delete path retries it, the same contract the sync itself follows.
            val parkedPath = repo.getAppByPackage(packageName)?.pendingInstallFilePath
            var mayDropRow = parkedPath == null
            if (parkedPath != null) {
                val disposal = try {
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
                            }
                            .onFailure { retryError ->
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
