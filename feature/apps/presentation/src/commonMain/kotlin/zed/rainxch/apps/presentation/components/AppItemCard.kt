package zed.rainxch.apps.presentation.components

import zed.rainxch.core.presentation.components.InstalledAppIcon
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FileDownloadOff
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.model.AppItem
import zed.rainxch.apps.presentation.model.UpdateState
import zed.rainxch.core.presentation.components.GitHubStoreImage
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.checking
import zed.rainxch.githubstore.core.presentation.res.delete_task
import zed.rainxch.githubstore.core.presentation.res.error_with_message
import zed.rainxch.githubstore.core.presentation.res.install
import zed.rainxch.githubstore.core.presentation.res.installing
import zed.rainxch.githubstore.core.presentation.res.open
import zed.rainxch.githubstore.core.presentation.res.pending_install
import zed.rainxch.githubstore.core.presentation.res.ready_to_install
import zed.rainxch.githubstore.core.presentation.res.uninstall
import zed.rainxch.githubstore.core.presentation.res.update
import zed.rainxch.githubstore.core.presentation.res.updated_successfully
import zed.rainxch.githubstore.core.presentation.res.variant_label_inline
import zed.rainxch.githubstore.core.presentation.res.variant_stale_hint

@Composable
fun AppItemCard(
    appItem: AppItem,
    onOpenClick: () -> Unit,
    onUpdateClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onRepoClick: () -> Unit,
    onTogglePreReleases: (Boolean) -> Unit,
    onToggleUpdateCheck: (Boolean) -> Unit,
    onAdvancedSettingsClick: () -> Unit,
    onPickVariantClick: () -> Unit,
    onInstallPendingClick: () -> Unit,
    onDiscardPendingClick: () -> Unit,
    onSkipVersionClick: () -> Unit,
    onUnskipVersionClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val app = appItem.installedApp

    KomiSurface(
        onClick = onRepoClick,
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
                    packageName = app.packageName,
                    appName = app.appName,
                    modifier =
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(shape.corner)),
                    apkFilePath = app.pendingInstallFilePath,
                    avatarFallbackUrl = app.repoOwnerAvatarUrl,
                )

                Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    KomiText(
                        text = app.appName,
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
                            imageModel = { app.repoOwnerAvatarUrl },
                            modifier =
                                Modifier
                                    .size(18.dp)
                                    .clip(RoundedCornerShape(shape.cornerSmall)),
                        )

                        Spacer(Modifier.width(6.dp))

                        KomiText(
                            text = app.repoOwner,
                            role = KomiTextRole.Body,
                            fontSize = 13.sp,
                            uppercase = false,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )

                        app.sourceHost?.let {
                            Spacer(Modifier.width(6.dp))

                            SourceChip(host = it)
                        }
                    }

                    when {
                        app.pendingInstallFilePath != null -> {
                            KomiText(
                                text = stringResource(Res.string.ready_to_install),
                                role = KomiTextRole.Body,
                                fontSize = 13.sp,
                                uppercase = false,
                                color = colors.primary,
                                fontWeight = FontWeight.SemiBold,
                            )

                            // The parked file's version, not the store's latest: a pending install
                            // can be a rollback, and the line's direction has to be visible.
                            if (appItem.versionTargetVersion != null) {
                                Spacer(Modifier.height(2.dp))

                                KomiText(
                                    text = appItem.versionLabel,
                                    role = KomiTextRole.Body,
                                    fontSize = 13.sp,
                                    uppercase = false,
                                    color =
                                        if (appItem.isVersionDowngrade) {
                                            DowngradeWarningColor
                                        } else {
                                            colors.primary
                                        },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        app.isPendingInstall -> {
                            KomiText(
                                text = stringResource(Res.string.pending_install),
                                role = KomiTextRole.Body,
                                fontSize = 13.sp,
                                uppercase = false,
                                color = colors.primary,
                            )
                        }

                        app.preferredVariantStale -> {
                            KomiText(
                                text = stringResource(Res.string.variant_stale_hint),
                                role = KomiTextRole.Body,
                                fontSize = 13.sp,
                                uppercase = false,
                                color = colors.error,
                                modifier =
                                    Modifier.clickable(
                                        enabled = !appItem.isBusy,
                                        onClick = onPickVariantClick,
                                    ),
                            )
                        }

                        app.isUpdateAvailable -> {
                            KomiText(
                                text = appItem.versionLabel,
                                role = KomiTextRole.Body,
                                fontSize = 13.sp,
                                uppercase = false,
                                color = colors.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            if (!app.preferredAssetVariant.isNullOrBlank()) {
                                KomiText(
                                    text =
                                        stringResource(
                                            Res.string.variant_label_inline,
                                            app.preferredAssetVariant,
                                        ),
                                    role = KomiTextRole.Label,
                                    fontSize = 11.sp,
                                    uppercase = false,
                                    color = colors.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        else -> {
                            KomiText(
                                text = appItem.idleVersionLabel,
                                role = KomiTextRole.Body,
                                fontSize = 13.sp,
                                uppercase = false,
                                color = colors.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (app.repoDescription != null) {
                Spacer(Modifier.height(8.dp))

                KomiText(
                    text = app.repoDescription,
                    role = KomiTextRole.Body,
                    uppercase = false,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            AppCardToolbar(
                appItem = appItem,
                onAdvancedSettingsClick = onAdvancedSettingsClick,
                onPickVariantClick = onPickVariantClick,
                onTogglePreReleases = onTogglePreReleases,
                onToggleUpdateCheck = onToggleUpdateCheck,
                onSkipVersionClick = onSkipVersionClick,
                onUnskipVersionClick = onUnskipVersionClick,
            )

            Spacer(Modifier.height(12.dp))

            when (val state = appItem.updateState) {
                // A running download lives on the "in progress" card, and this row is hidden while
                // it does, so there is no progress to draw here any more.
                is UpdateState.Downloading -> {}

                is UpdateState.Installing -> {
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
                }

                is UpdateState.CheckingUpdate -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KomiCircularProgress(
                            modifier = Modifier.size(16.dp),
                        )

                        KomiText(
                            text = stringResource(Res.string.checking),
                            role = KomiTextRole.Body,
                            fontSize = 13.sp,
                            color = colors.onSurface,
                        )
                    }
                }

                is UpdateState.Success -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        KomiIcon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(16.dp),
                        )

                        KomiText(
                            text = stringResource(Res.string.updated_successfully),
                            role = KomiTextRole.Body,
                            fontSize = 13.sp,
                            color = colors.primary,
                        )
                    }
                }

                is UpdateState.Error -> {
                    KomiText(
                        text = stringResource(Res.string.error_with_message, state.message),
                        role = KomiTextRole.Body,
                        fontSize = 13.sp,
                        uppercase = false,
                        color = colors.error,
                    )
                }

                UpdateState.Idle -> {}
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!app.isPendingInstall) {
                    val uninstallDescription = stringResource(Res.string.uninstall)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(enabled = !appItem.isBusy, onClick = onUninstallClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        KomiIcon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = uninstallDescription,
                            tint = colors.error,
                        )
                    }
                }

                if (app.pendingInstallFilePath != null) {
                    KomiButton(
                        onClick = onInstallPendingClick,
                        label = stringResource(Res.string.install),
                        variant = KomiButtonVariant.Primary,
                        leadingIcon = Icons.Default.Update,
                        modifier = Modifier.weight(1f),
                    )

                    val deleteTaskDescription =
                        stringResource(Res.string.delete_task)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(onClick = onDiscardPendingClick),
                        contentAlignment = Alignment.Center,
                    ) {
                        KomiIcon(
                            imageVector = Icons.Default.FileDownloadOff,
                            contentDescription = deleteTaskDescription,
                            tint = colors.onSurfaceVariant,
                        )
                    }
                } else if (app.isUpdateAvailable && !app.isPendingInstall) {
                    KomiButton(
                        onClick = onUpdateClick,
                        label = stringResource(Res.string.update),
                        variant = KomiButtonVariant.Primary,
                        leadingIcon = Icons.Default.Update,
                        modifier = Modifier.weight(1f),
                    )
                } else if (app.isPendingInstall) {
                    KomiButton(
                        onClick = onDiscardPendingClick,
                        label = stringResource(Res.string.delete_task),
                        variant = KomiButtonVariant.Destructive,
                        leadingIcon = Icons.Default.FileDownloadOff,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    KomiButton(
                        onClick = onOpenClick,
                        label = stringResource(Res.string.open),
                        variant = KomiButtonVariant.Primary,
                        leadingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                        enabled = !appItem.isBusy,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

