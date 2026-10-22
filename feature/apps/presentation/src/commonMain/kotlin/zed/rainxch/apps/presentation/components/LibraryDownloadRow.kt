package zed.rainxch.apps.presentation.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.isVersionDowngrade
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.OrchestratedDownload
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.buttons.KomiIconButtonSize
import zed.rainxch.core.presentation.utils.formatFileSize
import zed.rainxch.core.presentation.utils.rememberTapCooldown
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.apps_version_update
import zed.rainxch.githubstore.core.presentation.res.delete_task
import zed.rainxch.githubstore.core.presentation.res.dismiss
import zed.rainxch.githubstore.core.presentation.res.download_failed
import zed.rainxch.githubstore.core.presentation.res.downloading
import zed.rainxch.githubstore.core.presentation.res.error_with_message
import zed.rainxch.githubstore.core.presentation.res.install
import zed.rainxch.githubstore.core.presentation.res.installing
import zed.rainxch.githubstore.core.presentation.res.pause
import zed.rainxch.githubstore.core.presentation.res.paused
import zed.rainxch.githubstore.core.presentation.res.ready_to_install
import zed.rainxch.githubstore.core.presentation.res.resume
import zed.rainxch.githubstore.core.presentation.res.retry

// A download in the Library, whoever started it: the same row as a tracked app, with the
// download's stage as its status, a progress bar, and the stage's actions.
@Composable
fun LibraryDownloadRow(
    download: OrchestratedDownload,
    installedVersion: String?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    onOpenRepo: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val (status, tone) = download.status(installedVersion)
    LibraryRow(
        app =
            LibraryRowApp(
                packageName = download.packageName,
                name = download.displayAppName,
                owner = download.repoOwner,
                avatarUrl = download.repoOwnerAvatarUrl,
                apkFilePath = download.filePath,
                sourceHost = download.sourceHost,
            ),
        status = status,
        statusTone = tone,
        onClick = onOpenRepo,
        modifier = modifier,
        progress = download.progress(),
    ) {
        DownloadActions(
            stage = download.stage,
            onPause = onPause,
            onResume = onResume,
            onRetry = onRetry,
            onDiscard = onDiscard,
            onInstall = onInstall,
            onDismiss = onDismiss,
        )
    }
}

@Composable
private fun OrchestratedDownload.status(installedVersion: String?): Pair<String, LibraryStatusTone> =
    when (stage) {
        DownloadStage.Queued, DownloadStage.Downloading -> {
            "${stringResource(Res.string.downloading)} · ${sizeLabel()}" to LibraryStatusTone.Plain
        }

        DownloadStage.Paused -> {
            "${stringResource(Res.string.paused)} · ${sizeLabel()}" to LibraryStatusTone.Plain
        }

        DownloadStage.Installing -> {
            "${stringResource(Res.string.installing)} · $releaseTag" to LibraryStatusTone.Plain
        }

        DownloadStage.AwaitingInstall -> {
            val version = versionText(installedVersion)
            val tone = if (isVersionDowngrade(installedVersion, releaseTag)) LibraryStatusTone.Downgrade else LibraryStatusTone.Accent
            "${stringResource(Res.string.ready_to_install)} · $version · ${formatFileSize(assetSize)}" to tone
        }

        DownloadStage.Failed -> {
            val message = errorMessage ?: stringResource(Res.string.download_failed)
            stringResource(Res.string.error_with_message, message) to LibraryStatusTone.Error
        }

        DownloadStage.Completed, DownloadStage.Cancelled -> {
            versionText(installedVersion) to LibraryStatusTone.Plain
        }
    }

@Composable
private fun OrchestratedDownload.versionText(installedVersion: String?): String =
    if (installedVersion.isNullOrBlank()) {
        releaseTag
    } else {
        stringResource(Res.string.apps_version_update, installedVersion, releaseTag)
    }

private fun OrchestratedDownload.sizeLabel(): String {
    val total = totalBytes ?: assetSize.takeIf { it > 0L }
    return if (total != null) {
        "${formatFileSize(bytesDownloaded)} / ${formatFileSize(total)}"
    } else {
        formatFileSize(bytesDownloaded)
    }
}

private fun OrchestratedDownload.progress(): LibraryProgress? =
    when (stage) {
        DownloadStage.Queued, DownloadStage.Downloading, DownloadStage.Paused -> {
            LibraryProgress.Download(
                fraction = (progressPercent ?: 0) / 100f,
                paused = stage == DownloadStage.Paused,
            )
        }

        DownloadStage.Installing -> {
            LibraryProgress.Working
        }

        else -> {
            null
        }
    }

@Composable
private fun RowScope.DownloadActions(
    stage: DownloadStage,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cooldown = rememberTapCooldown()
    when (stage) {
        DownloadStage.Queued, DownloadStage.Downloading -> {
            KomiIconButton(
                icon = Icons.Rounded.Pause,
                contentDescription = stringResource(Res.string.pause),
                onClick = onPause,
                size = KomiIconButtonSize.Sm,
            )
        }

        DownloadStage.Paused -> {
            KomiIconButton(
                icon = Icons.Rounded.PlayArrow,
                contentDescription = stringResource(Res.string.resume),
                onClick = { cooldown.run(onResume) },
                size = KomiIconButtonSize.Sm,
                enabled = !cooldown.active,
            )
        }

        DownloadStage.AwaitingInstall -> {
            KomiButton(
                onClick = { cooldown.run(onInstall) },
                label = stringResource(Res.string.install),
                size = KomiButtonSize.Sm,
                enabled = !cooldown.active,
            )
        }

        DownloadStage.Failed -> {
            KomiButton(
                onClick = { cooldown.run(onRetry) },
                label = stringResource(Res.string.retry),
                variant = KomiButtonVariant.Tonal,
                size = KomiButtonSize.Sm,
                enabled = !cooldown.active,
            )
        }

        else -> {}
    }
    when (stage) {
        DownloadStage.Installing -> CloseButton(label = stringResource(Res.string.dismiss), onClick = onDismiss)
        DownloadStage.Completed, DownloadStage.Cancelled -> Unit
        else -> CloseButton(label = stringResource(Res.string.delete_task), onClick = onDiscard)
    }
}

@Composable
private fun CloseButton(
    label: String,
    onClick: () -> Unit,
) {
    KomiIconButton(
        icon = Icons.Rounded.Close,
        contentDescription = label,
        onClick = onClick,
        variant = KomiButtonVariant.Text,
        size = KomiIconButtonSize.Sm,
    )
}
