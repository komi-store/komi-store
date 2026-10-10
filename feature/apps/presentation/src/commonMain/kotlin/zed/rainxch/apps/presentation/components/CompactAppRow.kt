@file:OptIn(ExperimentalTime::class)

package zed.rainxch.apps.presentation.components

import zed.rainxch.core.presentation.components.InstalledAppIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MoreVert
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.progress.KomiLinearProgress
import zed.rainxch.core.presentation.components.overlays.KomiDropdown
import zed.rainxch.core.presentation.components.overlays.KomiMenuItem
import zed.rainxch.core.presentation.components.overlays.KomiMenuTone
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.model.AppItem
import zed.rainxch.apps.presentation.model.UpdateState
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.advanced_settings_open
import zed.rainxch.githubstore.core.presentation.res.apps_compact_more_actions
import zed.rainxch.githubstore.core.presentation.res.apps_ignore_updates
import zed.rainxch.githubstore.core.presentation.res.apps_menu_item_active
import zed.rainxch.githubstore.core.presentation.res.apps_skip_version_unskip
import zed.rainxch.githubstore.core.presentation.res.cancel
import zed.rainxch.githubstore.core.presentation.res.download_failed
import zed.rainxch.githubstore.core.presentation.res.downloading
import zed.rainxch.githubstore.core.presentation.res.error_with_message
import zed.rainxch.githubstore.core.presentation.res.install
import zed.rainxch.githubstore.core.presentation.res.installing
import zed.rainxch.githubstore.core.presentation.res.open
import zed.rainxch.githubstore.core.presentation.res.pre_release_badge
import zed.rainxch.githubstore.core.presentation.res.uninstall
import zed.rainxch.githubstore.core.presentation.res.discard_pending_install
import zed.rainxch.githubstore.core.presentation.res.variant_picker_open
import kotlin.time.ExperimentalTime

@Composable
fun CompactAppRow(
    appItem: AppItem,
    onOpenClick: () -> Unit,
    onInstallPendingClick: () -> Unit,
    onDiscardPendingClick: () -> Unit,
    onAdvancedSettingsClick: () -> Unit,
    onPickVariantClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onTogglePreReleases: (Boolean) -> Unit,
    onToggleUpdateCheck: (Boolean) -> Unit,
    onUnskipVersionClick: () -> Unit,
    onCancelClick: () -> Unit,
    onRowClick: () -> Unit,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
) {
    val app = appItem.installedApp
    val isBusy =
        appItem.updateState is UpdateState.Downloading ||
            appItem.updateState is UpdateState.Installing ||
            appItem.updateState is UpdateState.CheckingUpdate

    val flags = rememberCompactStatusFlags(appItem)
    val rowSemanticName = buildCompactRowSemantics(app.appName, app.installedVersion, flags)
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val rowShape = RoundedCornerShape(shape.corner)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 68.dp)
            .then(
                if (framed) {
                    Modifier
                        .clip(rowShape)
                        .border(
                            width = 1.dp,
                            color = colors.outline,
                            shape = rowShape,
                        )
                        .background(colors.surface)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onRowClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = rowSemanticName
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        InstalledAppIcon(
            packageName = app.packageName,
            appName = app.appName,
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(shape.corner)),
            apkFilePath = app.pendingInstallFilePath,
        )

        Column(modifier = Modifier.weight(1f)) {
            KomiText(
                text = app.appName,
                role = KomiTextRole.Title,
                fontSize = 13.sp,
                color = colors.onSurface,
                uppercase = false,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                app.sourceHost?.let { SourceChip(host = it) }

                KomiText(
                    text = app.installedVersion,
                    role = KomiTextRole.Body,
                    fontSize = 13.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                StatusDotCluster(flags = flags)
            }

            if (appItem.updateState is UpdateState.Downloading) {
                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    KomiText(
                        text = stringResource(Res.string.downloading),
                        role = KomiTextRole.Body,
                        fontSize = 13.sp,
                        color = colors.onSurface,
                    )

                    if (appItem.downloadProgress != null) {
                        KomiText(
                            text = "${appItem.downloadProgress}%",
                            role = KomiTextRole.Body,
                            fontSize = 13.sp,
                            uppercase = false,
                            color = colors.onSurface,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                KomiLinearProgress(
                    progress = { (appItem.downloadProgress ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = colors.primary,
                )
            }

            // This row shows nothing for an error otherwise: isBusy is false for it, so the row
            // looked entirely normal and fell through to Open, leaving a failure that started on
            // the details screen invisible everywhere in the library.
            if (appItem.updateState is UpdateState.Error) {
                Spacer(Modifier.height(4.dp))

                KomiText(
                    text =
                        stringResource(
                            Res.string.error_with_message,
                            appItem.error ?: stringResource(Res.string.download_failed),
                        ),
                    role = KomiTextRole.Body,
                    fontSize = 13.sp,
                    uppercase = false,
                    color = colors.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (appItem.updateState is UpdateState.Downloading) {
            KomiButton(
                onClick = onCancelClick,
                label = stringResource(Res.string.cancel),
                variant = KomiButtonVariant.Destructive,
                size = KomiButtonSize.Sm,
                leadingIcon = Icons.Default.Cancel,
            )

            Spacer(Modifier.width(4.dp))
        } else if (appItem.updateState is UpdateState.Installing) {
            // Without this the row falls through to the install button while the installer is
            // already running, which reads as "nothing is happening". The other two cards render
            // this state, and a details-screen install reaches here through the same mirror.
            KomiText(
                text = stringResource(Res.string.installing),
                role = KomiTextRole.Body,
                fontSize = 13.sp,
                color = colors.onSurface,
            )

            Spacer(Modifier.width(4.dp))
        } else if (app.pendingInstallFilePath != null) {
            KomiButton(
                onClick = onInstallPendingClick,
                label = stringResource(Res.string.install),
                variant = KomiButtonVariant.Primary,
                size = KomiButtonSize.Sm,
                enabled = !isBusy,
                leadingIcon = Icons.Default.Update,
            )

            Spacer(Modifier.width(4.dp))
        } else if (!app.isPendingInstall && !isBusy) {
            val openLabel = stringResource(Res.string.open)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = onOpenClick)
                    .semantics {
                        contentDescription = openLabel
                        role = Role.Button
                    },
                contentAlignment = Alignment.Center,
            ) {
                KomiIcon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        CompactRowOverflow(
            appName = app.appName,
            isBusy = isBusy,
            isPending = app.isPendingInstall,
            isPreReleaseEnabled = app.includePreReleases,
            isUpdateCheckEnabled = app.updateCheckEnabled,
            hasSkippedReleaseTag = app.skippedReleaseTag != null,
            onAdvancedSettingsClick = onAdvancedSettingsClick,
            onPickVariantClick = onPickVariantClick,
            onUninstallClick = onUninstallClick,
            onTogglePreReleases = onTogglePreReleases,
            onToggleUpdateCheck = onToggleUpdateCheck,
            onDiscardPendingClick = onDiscardPendingClick,
            onUnskipVersionClick = onUnskipVersionClick,
        )
    }
}

@Composable
private fun CompactRowOverflow(
    appName: String,
    isBusy: Boolean,
    isPending: Boolean,
    isPreReleaseEnabled: Boolean,
    isUpdateCheckEnabled: Boolean,
    hasSkippedReleaseTag: Boolean,
    onAdvancedSettingsClick: () -> Unit,
    onPickVariantClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onTogglePreReleases: (Boolean) -> Unit,
    onToggleUpdateCheck: (Boolean) -> Unit,
    onDiscardPendingClick: () -> Unit,
    onUnskipVersionClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val moreActionsLabel = stringResource(Res.string.apps_compact_more_actions, appName)

    val preReleaseBase = stringResource(Res.string.pre_release_badge)
    val ignoreUpdatesBase = stringResource(Res.string.apps_ignore_updates)
    val advancedSettingsLabel = stringResource(Res.string.advanced_settings_open)
    val variantPickerLabel = stringResource(Res.string.variant_picker_open)
    val unskipLabel = stringResource(Res.string.apps_skip_version_unskip)
    val discardLabel = stringResource(Res.string.discard_pending_install)
    val uninstallLabel = stringResource(Res.string.uninstall)
    val preReleaseActive = stringResource(Res.string.apps_menu_item_active, preReleaseBase)
    val ignoreUpdatesActive = stringResource(Res.string.apps_menu_item_active, ignoreUpdatesBase)

    val entries = buildList {
        add(KomiMenuItem(id = "advanced_settings", label = advancedSettingsLabel))
        add(KomiMenuItem(id = "variant_picker", label = variantPickerLabel))
        add(
            KomiMenuItem(
                id = "toggle_pre_releases",
                label = if (isPreReleaseEnabled) preReleaseActive else preReleaseBase,
            ),
        )
        add(
            KomiMenuItem(
                id = "toggle_update_check",
                label = if (!isUpdateCheckEnabled) ignoreUpdatesActive else ignoreUpdatesBase,
            ),
        )
        if (hasSkippedReleaseTag) {
            add(KomiMenuItem(id = "unskip_version", label = unskipLabel))
        }
        if (isPending) {
            add(
                KomiMenuItem(
                    id = "discard_pending",
                    label = discardLabel,
                    icon = Icons.Outlined.DeleteOutline,
                    tone = KomiMenuTone.Danger,
                ),
            )
        } else {
            add(
                KomiMenuItem(
                    id = "uninstall",
                    label = uninstallLabel,
                    icon = Icons.Outlined.DeleteOutline,
                    tone = KomiMenuTone.Danger,
                ),
            )
        }
    }.toImmutableList()

    KomiDropdown(
        entries = entries,
        onSelect = { item ->
            when (item.id) {
                "advanced_settings" -> onAdvancedSettingsClick()
                "variant_picker" -> onPickVariantClick()
                "toggle_pre_releases" -> onTogglePreReleases(!isPreReleaseEnabled)
                "toggle_update_check" -> onToggleUpdateCheck(!isUpdateCheckEnabled)
                "unskip_version" -> onUnskipVersionClick()
                "discard_pending" -> onDiscardPendingClick()
                "uninstall" -> onUninstallClick()
            }
        },
        trigger = { onClick ->
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .clickable(enabled = !isBusy, onClick = onClick)
                        .semantics {
                            contentDescription = moreActionsLabel
                            role = Role.Button
                        },
                contentAlignment = Alignment.Center,
            ) {
                KomiIcon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = null,
                    tint = if (isBusy) colors.onSurfaceVariant.copy(alpha = 0.38f) else colors.onSurfaceVariant,
                )
            }
        },
    )
}
