package zed.rainxch.apps.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownloadOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Update
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.AppsAction
import zed.rainxch.apps.presentation.isVersionDowngrade
import zed.rainxch.apps.presentation.model.AppItem
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.OrchestratedDownload
import zed.rainxch.core.presentation.components.GitHubStoreImage
import zed.rainxch.core.presentation.components.InstalledAppIcon
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.progress.KomiLinearProgress
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.formatFileSize
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

@Composable
fun InProgressAppCard(
    download: OrchestratedDownload,
    installedVersion: String?,
    rowItem: AppItem?,
    onRowAction: (AppsAction) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    // Tapping the card leads back to the repo it stands for, the move the library card makes.
    // Null for a download with no repository behind it: the card stays inert.
    onOpenRepo: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape

    KomiSurface(
        onClick = onOpenRepo,
        modifier = modifier,
    ) {
        Column(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(shape.corner))
                    .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InstalledAppIcon(
                    packageName = download.packageName,
                    appName = download.displayAppName,
                    modifier =
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(shape.corner)),
                    apkFilePath = download.filePath,
                    avatarFallbackUrl = download.repoOwnerAvatarUrl,
                )

                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    KomiText(
                        text = download.displayAppName,
                        role = KomiTextRole.Title,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        uppercase = false,
                    )

                    // The same identity lines as the big library card: the handoff from this card
                    // to that one has to read as the same object changing its status zone, not as
                    // a swap to a different widget.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        GitHubStoreImage(
                            imageModel = { download.repoOwnerAvatarUrl },
                            modifier =
                                Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(shape.cornerSmall)),
                        )

                        Spacer(Modifier.width(6.dp))

                        KomiText(
                            text = download.repoOwner,
                            role = KomiTextRole.Body,
                            fontSize = 13.sp,
                            uppercase = false,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }

                    StageLine(download = download, installedVersion = installedVersion)
                }
            }

            // The same block in the same slot as the big card's; the row's copy covers a download
            // that entered before the spec carried a description.
            val description = download.repoDescription ?: rowItem?.installedApp?.repoDescription
            if (description != null) {
                Spacer(Modifier.height(8.dp))

                KomiText(
                    text = description,
                    role = KomiTextRole.Body,
                    uppercase = false,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // The big card's management row, driven through the row it belongs to. Shown for every
            // stage so the row neither appears nor vanishes depending on where the download is.
            rowItem?.let { item ->
                AppCardToolbar(
                    appItem = item,
                    // The card is busy by definition, so the row's gate would keep every control
                    // dead for the card's whole life; the controls stay live here. The one
                    // risky follow-up — re-running the update after a variant pick — is already
                    // covered: this call passes resumeUpdateAfterPick = false.
                    controlsEnabled = true,
                    onAdvancedSettingsClick = {
                        onRowAction(AppsAction.OnOpenAdvancedSettings(item.installedApp))
                    },
                    onPickVariantClick = {
                        onRowAction(
                            AppsAction.OnOpenVariantPicker(
                                app = item.installedApp,
                                resumeUpdateAfterPick = false,
                            ),
                        )
                    },
                    onTogglePreReleases = { enabled ->
                        onRowAction(
                            AppsAction.OnTogglePreReleases(item.installedApp.packageName, enabled),
                        )
                    },
                    onToggleUpdateCheck = { enabled ->
                        onRowAction(
                            AppsAction.OnToggleUpdateCheck(item.installedApp.packageName, enabled),
                        )
                    },
                    onSkipVersionClick = {
                        val tag =
                            item.installedApp.latestVersion
                                ?: item.installedApp.latestVersionName
                        if (!tag.isNullOrBlank()) {
                            onRowAction(AppsAction.OnSkipReleaseTag(item.installedApp.packageName, tag))
                        }
                    },
                    onUnskipVersionClick = {
                        onRowAction(AppsAction.OnUnskipReleaseTag(item.installedApp.packageName))
                    },
                )
            }

            Spacer(Modifier.height(12.dp))

            when (download.stage) {
                DownloadStage.Queued,
                DownloadStage.Downloading,
                -> {
                    ProgressBody(download = download, paused = false)

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KomiButton(
                            onClick = onPause,
                            label = stringResource(Res.string.pause),
                            variant = KomiButtonVariant.Primary,
                            leadingIcon = Icons.Default.Pause,
                            modifier = Modifier.weight(1f),
                        )

                        DeleteTaskIcon(onClick = onDiscard)
                    }
                }

                // The bar holds the value it stopped at rather than clearing: resuming picks up from
                // exactly there.
                DownloadStage.Paused -> {
                    ProgressBody(download = download, paused = true)

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KomiButton(
                            onClick = onResume,
                            label = stringResource(Res.string.resume),
                            variant = KomiButtonVariant.Primary,
                            leadingIcon = Icons.Default.PlayArrow,
                            modifier = Modifier.weight(1f),
                        )

                        DeleteTaskIcon(onClick = onDiscard)
                    }
                }

                DownloadStage.Installing -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KomiCircularProgress(
                            modifier = Modifier.size(16.dp),
                        )

                        KomiText(
                            text = stringResource(Res.string.installing),
                            role = KomiTextRole.Body,
                            fontSize = 13.sp,
                            color = colors.onSurface,
                        )
                    }

                    // The card needs a way out of this state. An install handed to the system
                    // installer never advances the stage: the installer reports DELEGATED_TO_SYSTEM
                    // and only a COMPLETED outcome settles the entry, so this card would otherwise
                    // sit here spinning for good once the transfer has already succeeded.
                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KomiButton(
                            onClick = onDismiss,
                            label = stringResource(Res.string.dismiss),
                            variant = KomiButtonVariant.Outline,
                            modifier = Modifier.weight(1f),
                        )

                        // Disabled: the installer is holding the file, so deleting the task now
                        // would pull it out from under a live install.
                        DeleteTaskIcon(onClick = onDiscard, enabled = false)
                    }
                }

                DownloadStage.AwaitingInstall -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KomiButton(
                            onClick = onInstall,
                            label = stringResource(Res.string.install),
                            variant = KomiButtonVariant.Primary,
                            leadingIcon = Icons.Default.Update,
                            modifier = Modifier.weight(1f),
                        )

                        DeleteTaskIcon(onClick = onDiscard)
                    }
                }

                DownloadStage.Failed -> {
                    KomiText(
                        text =
                            stringResource(
                                Res.string.error_with_message,
                                download.errorMessage ?: stringResource(Res.string.download_failed),
                            ),
                        role = KomiTextRole.Body,
                        fontSize = 13.sp,
                        uppercase = false,
                        color = colors.error,
                    )

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KomiButton(
                            onClick = onRetry,
                            label = stringResource(Res.string.retry),
                            variant = KomiButtonVariant.Primary,
                            leadingIcon = Icons.Default.Refresh,
                            modifier = Modifier.weight(1f),
                        )

                        DeleteTaskIcon(onClick = onDiscard)
                    }

                    Spacer(Modifier.height(8.dp))

                    // Dismissing only clears the card from the list; it deletes nothing. Weak
                    // on purpose next to retry and delete.
                    KomiButton(
                        onClick = onDismiss,
                        label = stringResource(Res.string.dismiss),
                        variant = KomiButtonVariant.Text,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // Terminal: the VM keeps these out of the transient list, so this is reachable
                // only if that filter changes. Draw nothing rather than invent a fifth state.
                DownloadStage.Completed,
                DownloadStage.Cancelled,
                -> Unit
            }
        }
    }
}

// The identity column's third line, kept in the same slot and shape as the big library card's:
// an awaiting download shows "ready to install" exactly where the pending card shows it, and
// every other stage shows the version line the same card keeps underneath.
@Composable
private fun StageLine(
    download: OrchestratedDownload,
    installedVersion: String?,
) {
    val colors = LocalPersonality.current.colors

    if (download.stage == DownloadStage.AwaitingInstall) {
        KomiText(
            text = stringResource(Res.string.ready_to_install),
            role = KomiTextRole.Body,
            fontSize = 13.sp,
            uppercase = false,
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
        )

        Spacer(Modifier.height(2.dp))
    }

    VersionLine(installedVersion = installedVersion, targetVersion = download.releaseTag)
}

@Composable
private fun VersionLine(installedVersion: String?, targetVersion: String) {
    val colors = LocalPersonality.current.colors
    val isDowngrade = isVersionDowngrade(installedVersion, targetVersion)
    val text =
        if (installedVersion.isNullOrBlank()) {
            targetVersion
        } else {
            stringResource(Res.string.apps_version_update, installedVersion, targetVersion)
        }

    Spacer(Modifier.height(2.dp))

    KomiText(
        text = text,
        role = KomiTextRole.Body,
        fontSize = 13.sp,
        uppercase = false,
        color = if (isDowngrade) DowngradeWarningColor else colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ProgressBody(
    download: OrchestratedDownload,
    paused: Boolean,
) {
    val colors = LocalPersonality.current.colors
    val percent = download.progressPercent

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KomiText(
            text = stringResource(if (paused) Res.string.paused else Res.string.downloading),
            role = KomiTextRole.Body,
            fontSize = 13.sp,
            color = if (paused) colors.onSurfaceVariant else colors.onSurface,
        )

        if (percent != null) {
            KomiText(
                text = "$percent%",
                role = KomiTextRole.Body,
                fontSize = 13.sp,
                uppercase = false,
                color = colors.onSurface,
            )
        }
    }

    Spacer(Modifier.height(4.dp))

    KomiLinearProgress(
        progress = { (percent ?: 0) / 100f },
        modifier = Modifier.fillMaxWidth(),
        color = if (paused) colors.onSurfaceVariant else colors.primary,
    )

    Spacer(Modifier.height(6.dp))

    KomiText(
        text = downloadSizeLabel(download),
        role = KomiTextRole.Body,
        fontSize = 12.sp,
        uppercase = false,
        color = colors.onSurfaceVariant,
    )

    Spacer(Modifier.height(2.dp))

    // The asset belongs to the transfer, not the identity: the big card this hands off to has no
    // such line, so it moves down here with the numbers instead of changing the top block.
    KomiText(
        text = download.assetName,
        role = KomiTextRole.Body,
        fontSize = 12.sp,
        uppercase = false,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// "Delete task" is not the trash glyph: that one belongs to uninstall. This drops the download
// and its partial, never the installed app. The icon form and its slot match the pending card's
// delete, so the control does not change shape when the file lands and this card becomes that one.
@Composable
private fun DeleteTaskIcon(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = LocalPersonality.current.colors
    val deleteTaskDescription = stringResource(Res.string.delete_task)

    Box(
        modifier =
            modifier
                .size(48.dp)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        KomiIcon(
            imageVector = Icons.Default.FileDownloadOff,
            contentDescription = deleteTaskDescription,
            tint = if (enabled) colors.onSurfaceVariant else colors.onSurfaceVariant.copy(alpha = 0.38f),
        )
    }
}

private fun downloadSizeLabel(download: OrchestratedDownload): String {
    val total = download.totalBytes ?: download.assetSize.takeIf { it > 0L }
    return if (total != null) {
        "${formatFileSize(download.bytesDownloaded)} / ${formatFileSize(total)}"
    } else {
        formatFileSize(download.bytesDownloaded)
    }
}
