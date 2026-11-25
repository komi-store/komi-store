package zed.rainxch.details.presentation.handlers

import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.apk.ApkInspection
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.isReallyInstalled
import zed.rainxch.core.domain.repository.ExternalImportRepository
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.ApkInspector
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.details.presentation.utils.recoverStepFailure
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.details_unlink_external_app_failure
import zed.rainxch.githubstore.core.presentation.res.details_unlink_external_app_success
import zed.rainxch.githubstore.core.presentation.res.failed_to_open_app
import zed.rainxch.githubstore.core.presentation.res.failed_to_uninstall

// The installed app itself: opening, uninstalling and unlinking it, and inspecting its APK.
internal class DetailsAppHandler(
    private val installer: Installer,
    private val installedAppsRepository: InstalledAppsRepository,
    private val externalImportRepository: ExternalImportRepository,
    private val apkInspector: ApkInspector,
    private val tweaksRepository: TweaksRepository,
    private val logger: KomiStoreLogger,
) {
    internal fun onAction(
        session: DetailsSession,
        action: DetailsAction.App,
    ) {
        when (action) {
            DetailsAction.OnRequestUninstall -> session.update { it.copy(showUninstallConfirmation = true) }
            DetailsAction.OnDismissUninstallConfirmation -> session.update { it.copy(showUninstallConfirmation = false) }
            DetailsAction.OnConfirmUninstall -> confirmUninstall(session)
            DetailsAction.UninstallApp -> session.current.installedApp?.let { uninstall(session, it.packageName) }
            DetailsAction.OnConfirmDowngradeUninstall -> confirmDowngradeUninstall(session)
            DetailsAction.OnUnlinkExternalApp -> session.update { it.copy(showUnlinkConfirmation = true) }
            DetailsAction.OnDismissUnlinkConfirmation -> session.update { it.copy(showUnlinkConfirmation = false) }
            DetailsAction.OnConfirmUnlinkExternalApp -> confirmUnlink(session)
            DetailsAction.OpenApp -> openApp(session)
            DetailsAction.OnInspectApk -> openApkInspectSheet(session)
            DetailsAction.OnDismissApkInspect -> session.update { it.copy(isApkInspectSheetVisible = false) }
            DetailsAction.OnAcknowledgeApkInspectCoachmark -> acknowledgeApkInspectCoachmark(session)
        }
    }

    private fun confirmUninstall(session: DetailsSession) {
        session.update { it.copy(showUninstallConfirmation = false) }
        val installedApp = session.current.installedApp ?: return
        logger.debug("Uninstalling app (confirmed): ${installedApp.packageName}")
        requestUninstall(session, installedApp.packageName)
    }

    private fun confirmDowngradeUninstall(session: DetailsSession) {
        val warning = session.current.downgradeWarning ?: return
        session.update { it.copy(downgradeWarning = null) }
        uninstall(session, warning.packageName)
    }

    private fun uninstall(
        session: DetailsSession,
        packageName: String,
    ) {
        logger.debug("Uninstalling app: $packageName")
        requestUninstall(session, packageName)
    }

    private fun requestUninstall(
        session: DetailsSession,
        packageName: String,
    ) {
        session.launch {
            recoverStepFailure(
                recover = { error ->
                    logger.error("Failed to request uninstall for $packageName: ${error.message}")
                    session.message(getString(Res.string.failed_to_uninstall, packageName))
                },
            ) { installer.uninstall(packageName) }
        }
    }

    private fun confirmUnlink(session: DetailsSession) {
        session.update { it.copy(showUnlinkConfirmation = false) }
        val packageName = session.current.installedApp?.packageName ?: return
        logger.debug("Unlinking externally-imported app: $packageName")
        session.launch {
            try {
                installedAppsRepository.executeInTransaction {
                    externalImportRepository.unlink(packageName)
                    installedAppsRepository.deleteInstalledApp(packageName)
                }
                session.message(getString(Res.string.details_unlink_external_app_success))
            } catch (e: LocalStoreException) {
                logger.error("Failed to unlink $packageName: ${e.message}")
                session.message(getString(Res.string.details_unlink_external_app_failure))
            }
        }
    }

    private fun openApp(session: DetailsSession) {
        val installedApp = session.current.installedApp ?: return
        if (!installer.openApp(installedApp.packageName)) {
            session.launch { session.message(getString(Res.string.failed_to_open_app, installedApp.appName)) }
        }
    }

    private fun openApkInspectSheet(session: DetailsSession) {
        val installed = session.current.installedApp
        if (installed == null) {
            logger.warn("openApkInspectSheet: nothing inspectable in current state")
            return
        }
        session.update { it.copy(isApkInspectSheetVisible = true, isApkInspectLoading = true, apkInspection = null) }
        session.launch {
            val inspection = inspect(installed)
            session.update { it.copy(isApkInspectLoading = false, apkInspection = inspection) }
            acknowledgeApkInspectCoachmark(session)
        }
    }

    // An installed app reads from the system first; a pending one from its parked file first.
    private suspend fun inspect(app: InstalledApp): ApkInspection? {
        val parkedPath = app.pendingInstallFilePath
        return if (!app.isPendingInstall) {
            apkInspector.inspectInstalled(app.packageName) ?: parkedPath?.let { apkInspector.inspectFile(it) }
        } else if (parkedPath != null) {
            apkInspector.inspectFile(parkedPath) ?: apkInspector.inspectInstalled(app.packageName)
        } else {
            apkInspector.inspectInstalled(app.packageName)
        }
    }

    // The settings flows fall back to their defaults on a storage failure, so these reads and writes can't fail.
    internal fun observeApkInspectCoachmark(session: DetailsSession) {
        session.launch {
            if (tweaksRepository.getApkInspectCoachmarkShown().first()) return@launch
            val firstLoaded = session.state.first { !it.isLoading }
            if (firstLoaded.installedApp.isReallyInstalled()) session.update { it.copy(isApkInspectCoachmarkPending = true) }
        }
    }

    private fun acknowledgeApkInspectCoachmark(session: DetailsSession) {
        if (!session.current.isApkInspectCoachmarkPending) return
        session.update { it.copy(isApkInspectCoachmarkPending = false) }
        session.launch { tweaksRepository.setApkInspectCoachmarkShown(true) }
    }
}
