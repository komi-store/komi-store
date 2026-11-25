package zed.rainxch.apps.presentation.settings

import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import zed.rainxch.apps.domain.repository.AppsRepository
import zed.rainxch.apps.presentation.AppsAction
import zed.rainxch.apps.presentation.AppsEvent
import zed.rainxch.apps.presentation.AppsStateHolder
import zed.rainxch.apps.presentation.mappers.toDomain
import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.SystemPackagesException
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.apps_skip_version_error
import zed.rainxch.githubstore.core.presentation.res.apps_skip_version_snackbar
import zed.rainxch.githubstore.core.presentation.res.apps_unskip_version_error
import zed.rainxch.githubstore.core.presentation.res.apps_unskip_version_snackbar
import zed.rainxch.githubstore.core.presentation.res.cannot_launch
import zed.rainxch.githubstore.core.presentation.res.failed_to_open
import zed.rainxch.githubstore.core.presentation.res.failed_to_uninstall

// A tracked app's own actions: open, uninstall, ignore updates, pre-releases, and skipping a release.
class AppRowActions(
    private val installedAppsRepository: InstalledAppsRepository,
    private val appsRepository: AppsRepository,
    private val installer: Installer,
    private val logger: KomiStoreLogger,
) {
    fun onAction(
        holder: AppsStateHolder,
        action: AppsAction.AppRow,
    ) {
        when (action) {
            is AppsAction.OnOpenApp -> {
                openApp(holder, action.app)
            }

            is AppsAction.OnUninstallApp -> {
                holder.update { it.copy(appPendingUninstall = action.app) }
            }

            is AppsAction.OnUninstallConfirmed -> {
                uninstallApp(holder, action.app)
                holder.update { it.copy(appPendingUninstall = null) }
            }

            AppsAction.OnDismissUninstallDialog -> {
                holder.update { it.copy(appPendingUninstall = null) }
            }

            is AppsAction.OnTogglePreReleases -> {
                togglePreReleases(holder, action.packageName, action.enabled)
            }

            is AppsAction.OnToggleUpdateCheck -> {
                toggleUpdateCheck(holder, action.packageName, action.enabled)
            }

            is AppsAction.OnSkipReleaseTag -> {
                skipReleaseTag(holder, action.packageName, action.tag)
            }

            is AppsAction.OnUnskipReleaseTag -> {
                unskipReleaseTag(holder, action.packageName)
            }
        }
    }

    private fun openApp(
        holder: AppsStateHolder,
        app: InstalledAppUi,
    ) {
        holder.scope.launch {
            try {
                appsRepository.openApp(
                    installedApp = app.toDomain(),
                    onCantLaunchApp = {
                        holder.scope.launch {
                            holder.send(AppsEvent.ShowError(getString(Res.string.cannot_launch, app.appName)))
                        }
                    },
                )
            } catch (e: SystemPackagesException) {
                logger.error("Failed to open app: ${e.message}")
                holder.send(AppsEvent.ShowError(getString(Res.string.failed_to_open, app.appName)))
            }
        }
    }

    private fun uninstallApp(
        holder: AppsStateHolder,
        app: InstalledAppUi,
    ) {
        holder.scope.launch {
            try {
                installer.uninstall(app.packageName)
                logger.debug("Requested uninstall for ${app.packageName}")
            } catch (e: SystemPackagesException) {
                logger.error("Failed to request uninstall for ${app.packageName}: ${e.message}")
                holder.send(AppsEvent.ShowError(getString(Res.string.failed_to_uninstall, app.appName)))
            }
        }
    }

    private fun togglePreReleases(
        holder: AppsStateHolder,
        packageName: String,
        enabled: Boolean,
    ) {
        holder.scope.launch {
            try {
                installedAppsRepository.setIncludePreReleases(packageName, enabled)
                installedAppsRepository.checkForUpdates(packageName)
            } catch (e: LocalStoreException) {
                logger.error("Failed to toggle pre-releases for $packageName: ${e.message}")
            } catch (e: SystemPackagesException) {
                logger.error("Failed to toggle pre-releases for $packageName: ${e.message}")
            }
        }
    }

    private fun toggleUpdateCheck(
        holder: AppsStateHolder,
        packageName: String,
        enabled: Boolean,
    ) {
        holder.scope.launch {
            try {
                installedAppsRepository.setUpdateCheckEnabled(packageName, enabled)
            } catch (e: LocalStoreException) {
                logger.error("Failed to toggle update check for $packageName: ${e.message}")
            }
        }
    }

    private fun skipReleaseTag(
        holder: AppsStateHolder,
        packageName: String,
        tag: String,
    ) {
        val trimmed = tag.trim()
        if (trimmed.isEmpty()) return
        holder.scope.launch {
            try {
                installedAppsRepository.setSkippedReleaseTag(packageName, trimmed)
                holder.send(AppsEvent.ShowSuccess(getString(Res.string.apps_skip_version_snackbar, trimmed)))
                installedAppsRepository.checkForUpdates(packageName)
            } catch (e: LocalStoreException) {
                skipFailed(holder, "Failed to skip release $trimmed for $packageName", e, Res.string.apps_skip_version_error)
            } catch (e: SystemPackagesException) {
                skipFailed(holder, "Failed to skip release $trimmed for $packageName", e, Res.string.apps_skip_version_error)
            }
        }
    }

    private fun unskipReleaseTag(
        holder: AppsStateHolder,
        packageName: String,
    ) {
        holder.scope.launch {
            try {
                installedAppsRepository.setSkippedReleaseTag(packageName, null)
                installedAppsRepository.checkForUpdates(packageName)
                holder.send(AppsEvent.ShowSuccess(getString(Res.string.apps_unskip_version_snackbar)))
            } catch (e: LocalStoreException) {
                skipFailed(holder, "Failed to unskip release for $packageName", e, Res.string.apps_unskip_version_error)
            } catch (e: SystemPackagesException) {
                skipFailed(holder, "Failed to unskip release for $packageName", e, Res.string.apps_unskip_version_error)
            }
        }
    }

    private suspend fun skipFailed(
        holder: AppsStateHolder,
        logMessage: String,
        cause: Exception,
        error: StringResource,
    ) {
        logger.error("$logMessage: ${cause.message}")
        holder.send(AppsEvent.ShowError(getString(error)))
    }
}
