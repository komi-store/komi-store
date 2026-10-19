package zed.rainxch.apps.presentation.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InstallMobile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiSoftIconButton
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.progress.KomiLinearProgress
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
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
    onOpenRepo: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val tapCooldown = rememberTapCooldown()

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

            rowItem?.let { item ->
                AppCardToolbar(
                    appItem = item,
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
                DownloadStage.Paused,
                -> {
                    val paused = download.stage == DownloadStage.Paused
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProgressBody(
                            download = download,
                            paused = paused,
                            modifier = Modifier.weight(1f),
                        )

                        Spacer(Modifier.width(8.dp))

                        if (paused) {
                            KomiSoftIconButton(
                                icon = Icons.Default.PlayArrow,
                                contentDescription = stringResource(Res.string.resume),
                                onClick = { tapCooldown.run(onResume) },
                                accent = true,
                                enabled = !tapCooldown.active,
                            )
                        } else {
                            KomiSoftIconButton(
                                icon = Icons.Default.Pause,
                                contentDescription = stringResource(Res.string.pause),
                                onClick = onPause,
                                accent = true,
                            )
                        }

                        DeleteTaskButton(onClick = onDiscard)
                    }
                }

                DownloadStage.Installing -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
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
                            modifier = Modifier.weight(1f),
                        )

                        KomiButton(
                            onClick = onDismiss,
                            label = stringResource(Res.string.dismiss),
                            variant = KomiButtonVariant.Text,
                            size = KomiButtonSize.Sm,
                        )
                    }
                }

                DownloadStage.AwaitingInstall -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KomiText(
                            text = assetCaption(download, formatFileSize(download.assetSize)),
                            role = KomiTextRole.Body,
                            fontSize = 12.sp,
                            uppercase = false,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        Spacer(Modifier.width(12.dp))

                        KomiButton(
                            onClick = { tapCooldown.run(onInstall) },
                            label = stringResource(Res.string.install),
                            size = KomiButtonSize.Sm,
                            leadingIcon = Icons.Default.InstallMobile,
                            enabled = !tapCooldown.active,
                        )

                        Spacer(Modifier.width(4.dp))

                        DeleteTaskButton(onClick = onDiscard)
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
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        KomiButton(
                            onClick = onDismiss,
                            label = stringResource(Res.string.dismiss),
                            variant = KomiButtonVariant.Text,
                            size = KomiButtonSize.Sm,
                        )

                        KomiButton(
                            onClick = { tapCooldown.run(onRetry) },
                            label = stringResource(Res.string.retry),
                            variant = KomiButtonVariant.Tonal,
                            size = KomiButtonSize.Sm,
                            leadingIcon = Icons.Default.Refresh,
                            enabled = !tapCooldown.active,
                        )

                        DeleteTaskButton(onClick = onDiscard)
                    }
                }

                DownloadStage.Completed,
                DownloadStage.Cancelled,
                -> {
                    Unit
                }
            }
        }
    }
}

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
private fun VersionLine(
    installedVersion: String?,
    targetVersion: String,
) {
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
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val percent = download.progressPercent

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KomiText(
                text = stringResource(if (paused) Res.string.paused else Res.string.downloading),
                role = KomiTextRole.Body,
                fontSize = 13.sp,
                color = if (paused) colors.onSurfaceVariant else colors.onSurface,
                modifier = Modifier.weight(1f),
            )

            if (percent != null) {
                KomiText(
                    text = "$percent%",
                    role = KomiTextRole.Body,
                    fontSize = 13.sp,
                    uppercase = false,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        KomiLinearProgress(
            progress = { (percent ?: 0) / 100f },
            modifier = Modifier.fillMaxWidth(),
            color = if (paused) colors.onSurfaceVariant else colors.primary,
        )

        Spacer(Modifier.height(6.dp))

        KomiText(
            text = assetCaption(download, downloadSizeLabel(download)),
            role = KomiTextRole.Body,
            fontSize = 12.sp,
            uppercase = false,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DeleteTaskButton(onClick: () -> Unit) {
    KomiSoftIconButton(
        icon = Icons.Default.Close,
        contentDescription = stringResource(Res.string.delete_task),
        onClick = onClick,
    )
}

private fun assetCaption(
    download: OrchestratedDownload,
    sizeLabel: String,
): String = "$sizeLabel · ${download.assetName}"

private fun downloadSizeLabel(download: OrchestratedDownload): String {
    val total = download.totalBytes ?: download.assetSize.takeIf { it > 0L }
    return if (total != null) {
        "${formatFileSize(download.bytesDownloaded)} / ${formatFileSize(total)}"
    } else {
        formatFileSize(download.bytesDownloaded)
    }
}
