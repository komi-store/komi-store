package zed.rainxch.apps.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
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
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.OrchestratedDownload
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.progress.KomiLinearProgress
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.formatFileSize
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.cancel
import zed.rainxch.githubstore.core.presentation.res.dismiss
import zed.rainxch.githubstore.core.presentation.res.download_failed
import zed.rainxch.githubstore.core.presentation.res.downloading
import zed.rainxch.githubstore.core.presentation.res.error_with_message
import zed.rainxch.githubstore.core.presentation.res.install
import zed.rainxch.githubstore.core.presentation.res.installing
import zed.rainxch.githubstore.core.presentation.res.ready_to_install

@Composable
fun InProgressAppCard(
    download: OrchestratedDownload,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape

    KomiSurface(modifier = modifier) {
        Column(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(shape.corner))
                    .padding(16.dp),
        ) {
            KomiText(
                text = download.displayAppName,
                role = KomiTextRole.Title,
                color = colors.onSurface,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                uppercase = false,
            )

            KomiText(
                text = "${download.repoOwner}/${download.repoName}",
                role = KomiTextRole.Body,
                fontSize = 13.sp,
                uppercase = false,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            KomiText(
                text = download.assetName,
                role = KomiTextRole.Body,
                fontSize = 13.sp,
                uppercase = false,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(12.dp))

            when (download.stage) {
                DownloadStage.Queued,
                DownloadStage.Downloading,
                -> {
                    val percent = download.progressPercent
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KomiText(
                            text = stringResource(Res.string.downloading),
                            role = KomiTextRole.Body,
                            fontSize = 13.sp,
                            color = colors.onSurface,
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
                        color = colors.primary,
                    )

                    Spacer(Modifier.height(6.dp))

                    KomiText(
                        text = downloadSizeLabel(download),
                        role = KomiTextRole.Body,
                        fontSize = 12.sp,
                        uppercase = false,
                        color = colors.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(12.dp))

                    KomiButton(
                        onClick = onCancel,
                        label = stringResource(Res.string.cancel),
                        variant = KomiButtonVariant.Destructive,
                        leadingIcon = Icons.Default.Cancel,
                        modifier = Modifier.fillMaxWidth(),
                    )
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

                    KomiButton(
                        onClick = onDismiss,
                        label = stringResource(Res.string.dismiss),
                        variant = KomiButtonVariant.Destructive,
                        leadingIcon = Icons.Default.Cancel,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                DownloadStage.AwaitingInstall -> {
                    KomiText(
                        text = stringResource(Res.string.ready_to_install),
                        role = KomiTextRole.Body,
                        fontSize = 13.sp,
                        uppercase = false,
                        color = colors.primary,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Spacer(Modifier.height(12.dp))

                    KomiButton(
                        onClick = onInstall,
                        label = stringResource(Res.string.install),
                        variant = KomiButtonVariant.Primary,
                        leadingIcon = Icons.Default.Update,
                        modifier = Modifier.fillMaxWidth(),
                    )
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

                    KomiButton(
                        onClick = onDismiss,
                        label = stringResource(Res.string.dismiss),
                        variant = KomiButtonVariant.Destructive,
                        leadingIcon = Icons.Default.Cancel,
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

private fun downloadSizeLabel(download: OrchestratedDownload): String {
    val total = download.totalBytes ?: download.assetSize.takeIf { it > 0L }
    return if (total != null) {
        "${formatFileSize(download.bytesDownloaded)} / ${formatFileSize(total)}"
    } else {
        formatFileSize(download.bytesDownloaded)
    }
}
