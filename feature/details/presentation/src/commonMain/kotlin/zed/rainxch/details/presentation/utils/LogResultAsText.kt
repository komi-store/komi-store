package zed.rainxch.details.presentation.utils

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.details.presentation.model.LogResult
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.log_cancelled
import zed.rainxch.githubstore.core.presentation.res.log_download_started
import zed.rainxch.githubstore.core.presentation.res.log_downloaded
import zed.rainxch.githubstore.core.presentation.res.log_error
import zed.rainxch.githubstore.core.presentation.res.log_error_with_message
import zed.rainxch.githubstore.core.presentation.res.log_install_started
import zed.rainxch.githubstore.core.presentation.res.log_installed
import zed.rainxch.githubstore.core.presentation.res.log_opened_appmanager
import zed.rainxch.githubstore.core.presentation.res.log_opened_external_installer
import zed.rainxch.githubstore.core.presentation.res.log_permission_blocked
import zed.rainxch.githubstore.core.presentation.res.log_prepare_appmanager
import zed.rainxch.githubstore.core.presentation.res.log_update_started
import zed.rainxch.githubstore.core.presentation.res.log_updated

@Composable
fun LogResult.asText(): String =
    when (this) {
        LogResult.DownloadStarted -> stringResource(Res.string.log_download_started)
        LogResult.Downloaded -> stringResource(Res.string.log_downloaded)
        LogResult.InstallStarted -> stringResource(Res.string.log_install_started)
        LogResult.Installed -> stringResource(Res.string.log_installed)
        LogResult.Updated -> stringResource(Res.string.log_updated)
        LogResult.Cancelled -> stringResource(Res.string.log_cancelled)
        LogResult.OpenedInAppManager -> stringResource(Res.string.log_opened_appmanager)
        is LogResult.Error -> errorText(message)
        is LogResult.Info -> message
        LogResult.PreparingForAppManager -> stringResource(Res.string.log_prepare_appmanager)
        LogResult.UpdateStarted -> stringResource(Res.string.log_update_started)
        LogResult.PermissionBlocked -> stringResource(Res.string.log_permission_blocked)
        LogResult.OpenedInExternalInstaller -> stringResource(Res.string.log_opened_external_installer)
    }

@Composable
private fun errorText(message: String?): String =
    if (message != null) stringResource(Res.string.log_error_with_message, message) else stringResource(Res.string.log_error)
