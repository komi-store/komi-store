package zed.rainxch.apps.presentation.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.AppsAction
import zed.rainxch.apps.presentation.model.AppItem
import zed.rainxch.apps.presentation.model.InstalledAppUi
import zed.rainxch.apps.presentation.model.UpdateState
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.buttons.KomiIconButtonSize
import zed.rainxch.core.presentation.components.overlays.KomiDropdown
import zed.rainxch.core.presentation.components.overlays.KomiMenuItem
import zed.rainxch.core.presentation.components.overlays.KomiMenuTone
import zed.rainxch.core.presentation.utils.rememberTapCooldown
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.advanced_settings_open
import zed.rainxch.githubstore.core.presentation.res.apps_compact_more_actions
import zed.rainxch.githubstore.core.presentation.res.apps_ignore_updates
import zed.rainxch.githubstore.core.presentation.res.apps_menu_item_active
import zed.rainxch.githubstore.core.presentation.res.apps_skip_version
import zed.rainxch.githubstore.core.presentation.res.apps_skip_version_unskip
import zed.rainxch.githubstore.core.presentation.res.apps_tag_asset_filter
import zed.rainxch.githubstore.core.presentation.res.apps_tag_skipped
import zed.rainxch.githubstore.core.presentation.res.apps_tag_updates_ignored
import zed.rainxch.githubstore.core.presentation.res.apps_variant_stale_pick
import zed.rainxch.githubstore.core.presentation.res.checking
import zed.rainxch.githubstore.core.presentation.res.delete_task
import zed.rainxch.githubstore.core.presentation.res.downloading
import zed.rainxch.githubstore.core.presentation.res.error_with_message
import zed.rainxch.githubstore.core.presentation.res.install
import zed.rainxch.githubstore.core.presentation.res.installing
import zed.rainxch.githubstore.core.presentation.res.open
import zed.rainxch.githubstore.core.presentation.res.pre_release_badge
import zed.rainxch.githubstore.core.presentation.res.ready_to_install
import zed.rainxch.githubstore.core.presentation.res.uninstall
import zed.rainxch.githubstore.core.presentation.res.update
import zed.rainxch.githubstore.core.presentation.res.variant_label_inline
import zed.rainxch.githubstore.core.presentation.res.variant_picker_open
import zed.rainxch.githubstore.core.presentation.res.variant_stale_hint

// A tracked app in any state but a running download: what it is, where it stands, one action
// (Install, Update or Open) and a menu with everything else.
@Composable
fun LibraryAppRow(
    appItem: AppItem,
    onAction: (AppsAction) -> Unit,
    onOpenRepo: () -> Unit,
    modifier: Modifier = Modifier,
    framed: Boolean = true,
) {
    val app = appItem.installedApp
    val (status, tone) = appItem.status()
    LibraryRow(
        app =
            LibraryRowApp(
                packageName = app.packageName,
                name = app.appName,
                owner = app.repoOwner,
                avatarUrl = app.repoOwnerAvatarUrl,
                apkFilePath = app.pendingInstallFilePath,
                sourceHost = app.sourceHost,
                ratingAverage = appItem.ratingAverage,
                ratingCount = appItem.ratingCount,
            ),
        status = status,
        statusTone = tone,
        onClick = onOpenRepo,
        modifier = modifier,
        tags = appItem.tags(),
        progress = LibraryProgress.Working.takeIf { appItem.isWorking },
        framed = framed,
    ) {
        MainAction(appItem = appItem, onAction = onAction)
        AppMenu(appItem = appItem, onAction = onAction)
    }
}

// Checking, downloading or installing: the row waits.
private val AppItem.isWorking: Boolean
    get() =
        updateState is UpdateState.CheckingUpdate ||
            updateState is UpdateState.Downloading ||
            updateState is UpdateState.Installing

@Composable
private fun AppItem.status(): Pair<String, LibraryStatusTone> {
    val app = installedApp
    return when {
        updateState is UpdateState.Error -> {
            stringResource(Res.string.error_with_message, updateState.message) to LibraryStatusTone.Error
        }

        updateState is UpdateState.CheckingUpdate -> {
            stringResource(Res.string.checking) to LibraryStatusTone.Plain
        }

        updateState is UpdateState.Downloading -> {
            stringResource(Res.string.downloading) to LibraryStatusTone.Plain
        }

        updateState is UpdateState.Installing -> {
            stringResource(Res.string.installing) to LibraryStatusTone.Plain
        }

        app.isParked -> {
            readyStatus()
        }

        app.preferredVariantStale -> {
            staleStatus(app) to LibraryStatusTone.Error
        }

        app.isUpdateAvailable && app.updateCheckEnabled -> {
            versionLabel to LibraryStatusTone.Accent
        }

        else -> {
            idleVersionLabel to LibraryStatusTone.Plain
        }
    }
}

// With an update, Update picks the variant again; without one, the row's main action does.
@Composable
private fun staleStatus(app: InstalledAppUi): String =
    stringResource(if (app.hasUpdate) Res.string.variant_stale_hint else Res.string.apps_variant_stale_pick)

private val InstalledAppUi.hasUpdate: Boolean
    get() = isUpdateAvailable && updateCheckEnabled && !isPendingInstall

// The sync keeps a file whose park was cleared until it can prove the install; that file is not
// something to offer, so only a live park counts.
private val InstalledAppUi.isParked: Boolean
    get() = isPendingInstall && pendingInstallFilePath != null

@Composable
private fun AppItem.readyStatus(): Pair<String, LibraryStatusTone> {
    val ready = stringResource(Res.string.ready_to_install)
    if (versionTargetVersion == null) return ready to LibraryStatusTone.Accent
    return "$ready · $versionLabel" to if (isVersionDowngrade) LibraryStatusTone.Downgrade else LibraryStatusTone.Accent
}

// The app's own settings, shown only when they differ from the default.
@Composable
private fun AppItem.tags(): ImmutableList<String> {
    val app = installedApp
    val preRelease = stringResource(Res.string.pre_release_badge)
    val ignored = stringResource(Res.string.apps_tag_updates_ignored)
    val filter = stringResource(Res.string.apps_tag_asset_filter)
    val skipped = app.skippedReleaseTag?.let { stringResource(Res.string.apps_tag_skipped, it) }
    val variant = app.preferredAssetVariant?.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.variant_label_inline, it) }
    return listOfNotNull(
        preRelease.takeIf { app.includePreReleases },
        ignored.takeIf { !app.updateCheckEnabled },
        skipped,
        variant,
        filter.takeIf { hasFilter },
    ).toImmutableList()
}

@Composable
private fun MainAction(
    appItem: AppItem,
    onAction: (AppsAction) -> Unit,
) {
    val app = appItem.installedApp
    val cooldown = rememberTapCooldown()
    when {
        app.isParked -> {
            KomiButton(
                onClick = { cooldown.run { onAction(AppsAction.OnInstallPendingApp(app)) } },
                label = stringResource(Res.string.install),
                size = KomiButtonSize.Sm,
                enabled = !appItem.isWorking && !cooldown.active,
            )
        }

        app.hasUpdate -> {
            KomiButton(
                onClick = { cooldown.run { onAction(AppsAction.OnUpdateApp(app)) } },
                label = stringResource(Res.string.update),
                size = KomiButtonSize.Sm,
                enabled = !appItem.isWorking && !cooldown.active,
            )
        }

        app.preferredVariantStale && !app.isPendingInstall -> {
            KomiButton(
                onClick = { onAction(AppsAction.OnOpenVariantPicker(app = app, resumeUpdateAfterPick = false)) },
                label = stringResource(Res.string.variant_picker_open),
                variant = KomiButtonVariant.Tonal,
                size = KomiButtonSize.Sm,
                enabled = !appItem.isWorking,
            )
        }

        !app.isPendingInstall -> {
            KomiIconButton(
                icon = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = stringResource(Res.string.open),
                onClick = { onAction(AppsAction.OnOpenApp(app)) },
                variant = KomiButtonVariant.Text,
                size = KomiIconButtonSize.Sm,
                enabled = !appItem.isWorking,
            )
        }
    }
}

@Composable
private fun AppMenu(
    appItem: AppItem,
    onAction: (AppsAction) -> Unit,
) {
    val app = appItem.installedApp
    KomiDropdown(
        entries = menuEntries(appItem),
        onSelect = { item -> menuAction(item.id, appItem)?.let(onAction) },
        trigger = { onClick ->
            KomiIconButton(
                icon = Icons.Rounded.MoreVert,
                contentDescription = stringResource(Res.string.apps_compact_more_actions, app.appName),
                onClick = onClick,
                variant = KomiButtonVariant.Text,
                size = KomiIconButtonSize.Sm,
                enabled = !appItem.isWorking,
            )
        },
    )
}

@Composable
private fun menuEntries(appItem: AppItem): ImmutableList<KomiMenuItem> {
    val app = appItem.installedApp
    val preRelease = stringResource(Res.string.pre_release_badge)
    val ignoreUpdates = stringResource(Res.string.apps_ignore_updates)
    val advanced = stringResource(Res.string.advanced_settings_open)
    val variant = stringResource(Res.string.variant_picker_open)
    val skip = stringResource(Res.string.apps_skip_version)
    val unskip = stringResource(Res.string.apps_skip_version_unskip)
    val deleteTask = stringResource(Res.string.delete_task)
    val uninstall = stringResource(Res.string.uninstall)
    val preReleaseOn = stringResource(Res.string.apps_menu_item_active, preRelease)
    val ignoreUpdatesOn = stringResource(Res.string.apps_menu_item_active, ignoreUpdates)
    return buildList {
        add(KomiMenuItem(id = MENU_ADVANCED, label = advanced))
        add(KomiMenuItem(id = MENU_VARIANT, label = variant))
        add(KomiMenuItem(id = MENU_PRE_RELEASE, label = if (app.includePreReleases) preReleaseOn else preRelease))
        add(KomiMenuItem(id = MENU_IGNORE, label = if (app.updateCheckEnabled) ignoreUpdates else ignoreUpdatesOn))
        when {
            app.skippedReleaseTag != null -> add(KomiMenuItem(id = MENU_UNSKIP, label = unskip))
            appItem.canSkipVersion -> add(KomiMenuItem(id = MENU_SKIP, label = skip))
        }
        if (app.isPendingInstall) {
            add(KomiMenuItem(id = MENU_DISCARD, label = deleteTask, icon = Icons.Rounded.Close, tone = KomiMenuTone.Danger))
        } else {
            add(KomiMenuItem(id = MENU_UNINSTALL, label = uninstall, icon = Icons.Rounded.DeleteOutline, tone = KomiMenuTone.Danger))
        }
    }.toImmutableList()
}

private fun menuAction(
    id: String,
    appItem: AppItem,
): AppsAction? {
    val app = appItem.installedApp
    return when (id) {
        MENU_ADVANCED -> AppsAction.OnOpenAdvancedSettings(app)
        MENU_VARIANT -> AppsAction.OnOpenVariantPicker(app = app, resumeUpdateAfterPick = false)
        MENU_PRE_RELEASE -> AppsAction.OnTogglePreReleases(app.packageName, !app.includePreReleases)
        MENU_IGNORE -> AppsAction.OnToggleUpdateCheck(app.packageName, !app.updateCheckEnabled)
        MENU_SKIP -> app.skipTag()?.let { AppsAction.OnSkipReleaseTag(app.packageName, it) }
        MENU_UNSKIP -> AppsAction.OnUnskipReleaseTag(app.packageName)
        MENU_DISCARD -> AppsAction.OnDiscardPendingInstall(app)
        MENU_UNINSTALL -> AppsAction.OnUninstallApp(app)
        else -> null
    }
}

private fun InstalledAppUi.skipTag(): String? = (latestVersion ?: latestVersionName)?.takeIf { it.isNotBlank() }

private const val MENU_ADVANCED = "advanced_settings"
private const val MENU_VARIANT = "variant_picker"
private const val MENU_PRE_RELEASE = "toggle_pre_releases"
private const val MENU_IGNORE = "toggle_update_check"
private const val MENU_SKIP = "skip_version"
private const val MENU_UNSKIP = "unskip_version"
private const val MENU_DISCARD = "discard_pending"
private const val MENU_UNINSTALL = "uninstall"
