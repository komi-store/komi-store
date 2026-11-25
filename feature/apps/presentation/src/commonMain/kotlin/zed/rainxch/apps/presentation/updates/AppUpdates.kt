package zed.rainxch.apps.presentation.updates

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import zed.rainxch.apps.presentation.AppsAction
import zed.rainxch.apps.presentation.AppsEvent
import zed.rainxch.apps.presentation.AppsStateHolder
import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.apps.presentation.model.UpdateAllProgress
import zed.rainxch.apps.presentation.model.UpdateState
import zed.rainxch.apps.presentation.withDownloadProgress
import zed.rainxch.apps.presentation.withUpdateState
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.DownloadOrchestrator
import zed.rainxch.core.domain.system.InstallPolicy
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.all_apps_updated_successfully
import zed.rainxch.githubstore.core.presentation.res.apps_no_release_found
import zed.rainxch.githubstore.core.presentation.res.failed_to_discard_pending
import zed.rainxch.githubstore.core.presentation.res.failed_to_update
import zed.rainxch.githubstore.core.presentation.res.no_updates_available
import zed.rainxch.githubstore.core.presentation.res.rate_limit_exceeded
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.milliseconds

// Updates, Update all, parked installs and the in-progress download rows of the Library screen.
class AppUpdates(
    private val targets: UpdateTargetResolver,
    private val downloads: UpdateDownloads,
    private val installs: UpdateInstalls,
    private val installedAppsRepository: InstalledAppsRepository,
    private val downloadOrchestrator: DownloadOrchestrator,
    private val logger: KomiStoreLogger,
) {
    private val activeUpdates = mutableMapOf<String, Job>()
    private var updateAllJob: Job? = null
    private val mirror = DownloadsMirror(downloadOrchestrator) { activeUpdates.containsKey(it) }

    fun observeDownloads(holder: AppsStateHolder) {
        mirror.observe(holder)
    }

    suspend fun reapplyDownloads(holder: AppsStateHolder) {
        mirror.reapply(holder)
    }

    fun onAction(
        holder: AppsStateHolder,
        action: AppsAction.Update,
    ) {
        when (action) {
            is AppsAction.OnInstallPendingApp -> {
                installPendingApp(holder, action.app)
            }

            is AppsAction.OnDiscardPendingInstall -> {
                holder.update { it.copy(appPendingDiscard = action.app) }
            }

            is AppsAction.OnConfirmDiscardPendingInstall -> {
                holder.update { it.copy(appPendingDiscard = null) }
                discardPendingInstall(holder, action.app)
            }

            AppsAction.OnDismissDiscardPendingDialog -> {
                holder.update { it.copy(appPendingDiscard = null) }
            }

            AppsAction.OnUpdateAll -> {
                updateAllApps(holder)
            }

            AppsAction.OnCancelUpdateAll -> {
                cancelAllUpdates(holder)
            }
        }
    }

    fun onAction(
        holder: AppsStateHolder,
        action: AppsAction.DownloadTask,
    ) {
        when (action) {
            is AppsAction.OnCancelInProgressDownload -> {
                runOrchestratorAction(holder, "cancel", action.key) { downloadOrchestrator.cancel(it) }
            }

            is AppsAction.OnResumeInProgressDownload -> {
                runOrchestratorAction(holder, "resume", action.key) { downloadOrchestrator.resume(it) }
            }

            is AppsAction.OnRetryInProgressDownload -> {
                retryInProgressDownload(holder, action.key)
            }

            is AppsAction.OnDiscardInProgressDownload -> {
                runOrchestratorAction(holder, "discard", action.key) { downloadOrchestrator.discard(it) }
            }

            is AppsAction.OnInstallInProgressDownload -> {
                runOrchestratorAction(holder, "installPending", action.key) { downloadOrchestrator.installPending(it) }
            }

            is AppsAction.OnDismissInProgressDownload -> {
                runOrchestratorAction(holder, "dismiss", action.key) { downloadOrchestrator.dismiss(it) }
            }
        }
    }

    fun update(
        holder: AppsStateHolder,
        app: InstalledAppUi,
        waitForInstallGate: Boolean = false,
    ) {
        if (activeUpdates.containsKey(app.packageName)) {
            logger.debug("Update already in progress for ${app.packageName}")
            return
        }
        val job =
            holder.scope.launch {
                try {
                    runUpdate(holder, app, waitForInstallGate)
                } finally {
                    activeUpdates.remove(app.packageName)
                }
            }
        activeUpdates[app.packageName] = job
    }

    // Hands unfinished downloads to the orchestrator's deferred mode, as the screen that drove them is going away.
    fun release() {
        updateAllJob?.cancel()
        val packageNames = activeUpdates.keys.toList()
        activeUpdates.values.forEach { it.cancel() }
        downloads.deferAll(packageNames)
    }

    private suspend fun runUpdate(
        holder: AppsStateHolder,
        app: InstalledAppUi,
        waitForInstallGate: Boolean,
    ) {
        try {
            recoverUpdateFailure({ e -> failUpdate(holder, app, e) }) {
                holder.setUpdateState(app.packageName, UpdateState.CheckingUpdate)
                val target = targets.resolve(app)
                if (target == null) {
                    reportMissingRelease(holder, app)
                } else {
                    downloadAndInstall(holder, app, target, waitForInstallGate)
                }
            }
        } catch (e: CancellationException) {
            logger.debug("Update cancelled for ${app.packageName}")
            downloads.cleanup(app.packageName, app.latestAssetName)
            holder.setUpdateState(app.packageName, UpdateState.Idle)
            clearPendingStatus(app.packageName, "on cancellation")
            throw e
        } catch (_: RateLimitException) {
            logger.debug("Rate limited during update for ${app.packageName}")
            clearPendingStatus(app.packageName, "on rate limit")
            holder.setUpdateState(app.packageName, UpdateState.Idle)
            holder.send(AppsEvent.ShowError(getString(Res.string.rate_limit_exceeded)))
        }
    }

    private suspend fun downloadAndInstall(
        holder: AppsStateHolder,
        app: InstalledAppUi,
        target: UpdateTarget,
        waitForInstallGate: Boolean,
    ) {
        logger.debug(
            "Update: ${app.appName} from ${app.installedVersion} to ${target.tag}, " +
                "asset: ${target.asset.name}, stored match: ${target.fromStoredMatch}",
        )
        downloads.ensurePermissions(target)
        downloads.discardStaleCopy(target)
        holder.setUpdateState(app.packageName, UpdateState.Downloading)

        val result = downloads.download(app, target, downloads.installPolicy()) { holder.setProgress(app.packageName, it) }
        if (result !is UpdateDownloadResult.Ready) {
            settleUnfinishedDownload(holder, app, target, result)
            return
        }

        installs.recordDownloaded(app, result)
        holder.setUpdateState(app.packageName, UpdateState.Installing)
        installs.install(app.packageName, result.filePath, target.extension, waitForInstallGate)
        holder.setUpdateState(app.packageName, UpdateState.Idle)
        logger.debug("Launched installer for ${app.appName} ${target.tag}, waiting for system confirmation")
    }

    private fun settleUnfinishedDownload(
        holder: AppsStateHolder,
        app: InstalledAppUi,
        target: UpdateTarget,
        result: UpdateDownloadResult,
    ) {
        if (result is UpdateDownloadResult.Failed) {
            if (target.fromStoredMatch) targets.storedMatchFailed(app.packageName)
            holder.setUpdateState(app.packageName, UpdateState.Error(result.message ?: "Download failed"))
        } else {
            holder.setUpdateState(app.packageName, UpdateState.Idle)
        }
    }

    private suspend fun failUpdate(
        holder: AppsStateHolder,
        app: InstalledAppUi,
        cause: Exception,
    ) {
        logger.error("Update failed for ${app.packageName}: ${cause.message}", cause)
        downloads.cleanup(app.packageName, app.latestAssetName)
        clearPendingStatus(app.packageName, "on error")
        holder.setUpdateState(app.packageName, UpdateState.Error(cause.message ?: "Update failed"))
        holder.send(AppsEvent.ShowError(getString(Res.string.failed_to_update, app.appName, cause.message ?: "")))
    }

    // An empty release window also means a failed fetch, so this leaves the park alone and reports it once.
    private suspend fun reportMissingRelease(
        holder: AppsStateHolder,
        app: InstalledAppUi,
    ) {
        logger.warn("No release to install for ${app.packageName}: no release found for ${app.appName}")
        holder.setUpdateState(app.packageName, UpdateState.Idle)
        holder.send(AppsEvent.ShowError(getString(Res.string.apps_no_release_found)))
    }

    private suspend fun clearPendingStatus(
        packageName: String,
        reason: String,
    ) {
        try {
            installedAppsRepository.updatePendingStatus(packageName, false)
        } catch (e: LocalStoreException) {
            logger.error("Failed to clear pending status $reason: ${e.message}")
        }
    }

    private fun updateAllApps(holder: AppsStateHolder) {
        if (holder.current.isUpdatingAll) {
            logger.error("Update all already in progress")
            return
        }

        updateAllJob =
            holder.scope.launch {
                try {
                    holder.update { it.copy(isUpdatingAll = true) }
                    val appsToUpdate =
                        holder.current.apps.filter {
                            it.installedApp.isUpdateAvailable && it.updateState !is UpdateState.Success
                        }
                    if (appsToUpdate.isEmpty()) {
                        holder.send(AppsEvent.ShowError(getString(Res.string.no_updates_available)))
                        return@launch
                    }

                    logger.debug("Starting update all for ${appsToUpdate.size} apps")
                    appsToUpdate.forEachIndexed { index, appItem ->
                        if (!isActive) {
                            logger.debug("Update all cancelled")
                            return@launch
                        }
                        val progress = UpdateAllProgress(index + 1, appsToUpdate.size, appItem.installedApp.appName)
                        holder.update { it.copy(updateAllProgress = progress) }
                        logger.debug("Updating ${index + 1}/${appsToUpdate.size}: ${appItem.installedApp.appName}")
                        update(holder, appItem.installedApp, waitForInstallGate = true)
                        activeUpdates[appItem.installedApp.packageName]?.join()
                        delay(UPDATE_ALL_GAP)
                    }

                    logger.debug("Update all completed successfully")
                    holder.send(AppsEvent.ShowSuccess(getString(Res.string.all_apps_updated_successfully)))
                } catch (e: CancellationException) {
                    logger.debug("Update all cancelled")
                    throw e
                } finally {
                    holder.update { it.copy(isUpdatingAll = false, updateAllProgress = null) }
                    updateAllJob = null
                }
            }
    }

    private fun cancelAllUpdates(holder: AppsStateHolder) {
        updateAllJob?.cancel()
        updateAllJob = null

        activeUpdates.values.forEach { it.cancel() }
        activeUpdates.clear()

        holder.scope.launch {
            holder.current.apps
                .filter { it.updateState != UpdateState.Idle && it.updateState != UpdateState.Success }
                .forEach { appItem ->
                    val packageName = appItem.installedApp.packageName
                    downloads.cleanup(packageName, appItem.installedApp.latestAssetName)
                    recoverUpdateFailure({ e ->
                        logger.warn("cancelAllUpdates: orchestrator discard failed for $packageName: ${e.message}")
                    }) {
                        downloadOrchestrator.discard(packageName)
                    }
                    holder.setUpdateState(packageName, UpdateState.Idle)
                }
        }

        holder.update { it.copy(isUpdatingAll = false, updateAllProgress = null) }
    }

    private fun runOrchestratorAction(
        holder: AppsStateHolder,
        what: String,
        key: String,
        block: suspend (String) -> Unit,
    ) {
        holder.scope.launch {
            recoverUpdateFailure({ e -> logger.warn("$what failed for $key: ${e.message}") }) {
                block(key)
            }
        }
    }

    private fun retryInProgressDownload(
        holder: AppsStateHolder,
        key: String,
    ) {
        if (activeUpdates.containsKey(key)) return
        val app =
            holder.current.apps
                .firstOrNull { it.installedApp.packageName == key }
                ?.installedApp
        when {
            app == null -> runOrchestratorAction(holder, "retry", key) { downloadOrchestrator.retry(it) }
            app.pendingInstallFilePath != null -> redownloadPendingApp(holder, app)
            else -> update(holder, app)
        }
    }

    private fun installPendingApp(
        holder: AppsStateHolder,
        app: InstalledAppUi,
    ) {
        if (activeUpdates.containsKey(app.packageName)) {
            logger.debug("Install already in progress for ${app.packageName}")
            return
        }
        if (app.pendingInstallFilePath?.let { !File(it).exists() } == true) {
            logger.debug("Parked file for ${app.packageName} is gone, downloading it again")
            redownloadPendingApp(holder, app)
            return
        }
        holder.scope.launch {
            recoverUpdateFailure({ e ->
                logger.error("Failed to install pending app ${app.packageName}: ${e.message}")
                holder.setUpdateState(app.packageName, UpdateState.Error(e.message ?: "Install failed"))
            }) {
                installParked(holder, app)
            }
        }
    }

    private suspend fun installParked(
        holder: AppsStateHolder,
        app: InstalledAppUi,
    ) {
        holder.setUpdateState(app.packageName, UpdateState.Installing)
        val outcome = downloadOrchestrator.installPending(app.packageName)
        holder.setUpdateState(app.packageName, if (outcome == null) UpdateState.Error("Install failed") else UpdateState.Idle)
    }

    // Downloads a parked install's missing file again in its pinned variant; the row stays parked.
    private fun redownloadPendingApp(
        holder: AppsStateHolder,
        app: InstalledAppUi,
    ) {
        val job =
            holder.scope.launch {
                try {
                    recoverUpdateFailure({ e ->
                        logger.error("Downloading ${app.packageName} again failed: ${e.message}")
                        holder.setUpdateState(app.packageName, UpdateState.Error(e.message ?: "Download failed"))
                    }) {
                        redownload(holder, app)
                    }
                } catch (e: CancellationException) {
                    holder.setUpdateState(app.packageName, UpdateState.Idle)
                    throw e
                } catch (_: RateLimitException) {
                    holder.setUpdateState(app.packageName, UpdateState.Idle)
                    holder.send(AppsEvent.ShowError(getString(Res.string.rate_limit_exceeded)))
                } finally {
                    activeUpdates.remove(app.packageName)
                }
            }
        activeUpdates[app.packageName] = job
    }

    private suspend fun redownload(
        holder: AppsStateHolder,
        app: InstalledAppUi,
    ) {
        holder.setUpdateState(app.packageName, UpdateState.CheckingUpdate)
        val target = targets.resolve(app)
        if (target == null) {
            reportMissingRelease(holder, app)
            return
        }
        logger.debug(
            "Downloading ${app.packageName} again: ${target.tag} ${target.asset.name}, stored match: ${target.fromStoredMatch}",
        )
        downloads.ensurePermissions(target)
        holder.setUpdateState(app.packageName, UpdateState.Downloading)
        val result =
            downloads.download(app, target, InstallPolicy.InstallWhileForeground) { holder.setProgress(app.packageName, it) }
        if (result is UpdateDownloadResult.Ready) {
            installParked(holder, app)
        } else {
            settleUnfinishedDownload(holder, app, target, result)
        }
    }

    private fun discardPendingInstall(
        holder: AppsStateHolder,
        app: InstalledAppUi,
    ) {
        holder.scope.launch {
            recoverUpdateFailure({ e -> logger.warn("discardPendingInstall: orchestrator discard failed: ${e.message}") }) {
                downloadOrchestrator.discard(app.packageName)
            }
            app.pendingInstallFilePath?.let { path ->
                try {
                    File(path).takeIf { it.exists() }?.delete()
                } catch (e: SecurityException) {
                    logger.warn("discardPendingInstall: failed to delete parked file: ${e.message}")
                }
            }
            try {
                installedAppsRepository.deleteInstalledApp(app.packageName)
            } catch (e: LocalStoreException) {
                logger.error("discardPendingInstall: row delete failed for ${app.packageName}: ${e.message}")
                holder.send(AppsEvent.ShowError(getString(Res.string.failed_to_discard_pending, app.appName)))
            }
        }
    }

    private fun AppsStateHolder.setUpdateState(
        packageName: String,
        updateState: UpdateState,
    ) {
        update { it.withUpdateState(packageName, updateState) }
    }

    private fun AppsStateHolder.setProgress(
        packageName: String,
        progress: Int,
    ) {
        update { it.withDownloadProgress(packageName, progress) }
    }

    private companion object {
        val UPDATE_ALL_GAP = 1_000.milliseconds
    }
}
