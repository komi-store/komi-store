package zed.rainxch.details.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.core.presentation.components.buttons.KomiSoftIconButton
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiDropdown
import zed.rainxch.core.presentation.components.overlays.KomiMenuEntry
import zed.rainxch.core.presentation.components.overlays.KomiMenuItem
import zed.rainxch.core.presentation.components.overlays.KomiRichTooltip
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.pausedProgressFill
import zed.rainxch.core.presentation.personality.progressFill
import zed.rainxch.core.presentation.personality.secondaryContent
import zed.rainxch.core.presentation.utils.formatFileSize
import zed.rainxch.core.presentation.utils.rememberTapCooldown
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.details.presentation.DetailsState
import zed.rainxch.details.presentation.model.AssetUi
import zed.rainxch.details.presentation.model.AttestationStatus
import zed.rainxch.details.presentation.model.DownloadStage
import zed.rainxch.details.presentation.utils.extractArchitectureFromName
import zed.rainxch.details.presentation.utils.isExactArchitectureMatch
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.appmanager_description
import zed.rainxch.githubstore.core.presentation.res.architecture_compatible
import zed.rainxch.githubstore.core.presentation.res.checking_attestation
import zed.rainxch.githubstore.core.presentation.res.delete_task
import zed.rainxch.githubstore.core.presentation.res.details_attestation_unverified_explainer
import zed.rainxch.githubstore.core.presentation.res.details_attestation_verified_explainer
import zed.rainxch.githubstore.core.presentation.res.downloading
import zed.rainxch.githubstore.core.presentation.res.external_installer_description
import zed.rainxch.githubstore.core.presentation.res.inspect_with_appmanager
import zed.rainxch.githubstore.core.presentation.res.install_latest
import zed.rainxch.githubstore.core.presentation.res.install_ready
import zed.rainxch.githubstore.core.presentation.res.install_version
import zed.rainxch.githubstore.core.presentation.res.installing
import zed.rainxch.githubstore.core.presentation.res.no_build_for_this_device
import zed.rainxch.githubstore.core.presentation.res.not_available
import zed.rainxch.githubstore.core.presentation.res.obtainium_description
import zed.rainxch.githubstore.core.presentation.res.open_app
import zed.rainxch.githubstore.core.presentation.res.open_in_obtainium
import zed.rainxch.githubstore.core.presentation.res.open_with_external_installer
import zed.rainxch.githubstore.core.presentation.res.pause
import zed.rainxch.githubstore.core.presentation.res.paused
import zed.rainxch.githubstore.core.presentation.res.resume
import zed.rainxch.githubstore.core.presentation.res.show_install_options
import zed.rainxch.githubstore.core.presentation.res.unable_to_verify_attestation
import zed.rainxch.githubstore.core.presentation.res.uninstall
import zed.rainxch.githubstore.core.presentation.res.update_to_version
import zed.rainxch.githubstore.core.presentation.res.updating
import zed.rainxch.githubstore.core.presentation.res.verified_build
import zed.rainxch.githubstore.core.presentation.res.verifying

private val ButtonHeight = 56.dp

@Composable
fun SmartInstallButton(
    isDownloading: Boolean,
    isInstalling: Boolean,
    progress: Int?,
    primaryAsset: AssetUi?,
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryCooldown = rememberTapCooldown()
    val target = installTargetOf(state)
    val enabled =
        remember(primaryAsset, isDownloading, isInstalling) {
            primaryAsset != null && !isDownloading && !isInstalling
        } && !primaryCooldown.active
    val isActiveDownload = state.isDownloading || state.downloadStage != DownloadStage.IDLE

    if (target.isSameVersionInstalled && !isActiveDownload) {
        Column(modifier = modifier) {
            InstalledSplitRow(
                onUninstall = { onAction(DetailsAction.OnRequestUninstall) },
                onOpenApp = { onAction(DetailsAction.OpenApp) },
            )
            AttestationBadge(attestationStatus = state.attestationStatus)
        }
        return
    }

    val buttonText = installLabelText(labelOf(target, enabled = enabled, hasAsset = primaryAsset != null, state = state), target)
    Column(modifier = modifier) {
        InstallActionRow(
            enabled = enabled,
            isActiveDownload = isActiveDownload,
            target = target,
            buttonText = buttonText,
            primaryAsset = primaryAsset,
            state = state,
            progress = progress,
            onAction = onAction,
            onPrimaryClick = {
                if (!state.isDownloading && state.downloadStage == DownloadStage.IDLE) {
                    primaryCooldown.run {
                        onAction(if (target.showUpdateAffordance) DetailsAction.UpdateApp else DetailsAction.InstallPrimary)
                    }
                }
            },
        )
        AttestationBadge(attestationStatus = state.attestationStatus)
    }
}

// What the selected release means for the installed app, worked out once for the label and the layout.
private class InstallTarget(
    val isInstalled: Boolean,
    val normSelected: String?,
    val displaySelected: String?,
    val isSameVersionInstalled: Boolean,
    val showUpdateAffordance: Boolean,
    val offersOtherVersion: Boolean,
)

private fun installTargetOf(state: DetailsState): InstallTarget {
    val installedApp = state.installedApp
    val isInstalled = installedApp != null && !installedApp.isPendingInstall
    val normInstalled = installedApp?.installedVersion?.trim()?.takeIf { it.isNotBlank() }
    val normSelected =
        state.selectedRelease
            ?.tagName
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    val selectedIsLatestRelease = VersionMath.isExactSameVersion(normSelected, installedApp?.latestVersion)
    val tagsMatch = sameTag(normInstalled, normSelected)
    val updateAvailable = installedApp?.isUpdateAvailable == true && isInstalled
    // An equal tag means the phone runs this build, unless the tag is judged by publish time or the offered file differs.
    val updateFlagWins =
        updateAvailable && selectedIsLatestRelease && (isTimestampTracked(normSelected) || installedApp.latestBuildDiffers)
    val differsFromInstalled = isInstalled && normInstalled != null && !tagsMatch
    return InstallTarget(
        isInstalled = isInstalled,
        normSelected = normSelected,
        displaySelected = displayVersion(normSelected),
        isSameVersionInstalled = isInstalled && tagsMatch && !updateFlagWins,
        showUpdateAffordance = updateAvailable && selectedIsLatestRelease,
        offersOtherVersion = normSelected != null && (differsFromInstalled || differsFromNewest(state, normSelected)),
    )
}

private fun sameTag(
    installed: String?,
    selected: String?,
): Boolean = installed != null && selected != null && VersionMath.isExactSameVersion(installed, selected)

private fun isTimestampTracked(tag: String?): Boolean =
    tag != null && (VersionMath.isTimestampTrackedTag(tag) || VersionMath.detectScheme(tag) == VersionMath.Scheme.Unknown)

private fun displayVersion(tag: String?): String? =
    tag?.let { VersionMath.normalizeVersion(it).takeIf { normalized -> normalized.isNotBlank() } ?: it }

private fun differsFromNewest(
    state: DetailsState,
    tag: String,
): Boolean =
    state.allReleases
        .firstOrNull()
        ?.tagName
        ?.let { !VersionMath.isExactSameVersion(it, tag) } == true

private enum class InstallLabel {
    NoDeviceBuild,
    NotAvailable,
    Ready,
    Update,
    InstallVersion,
    InstallLatest,
}

private fun labelOf(
    target: InstallTarget,
    enabled: Boolean,
    hasAsset: Boolean,
    state: DetailsState,
): InstallLabel {
    val unavailable = !enabled && !hasAsset
    return when {
        unavailable && !state.selectedHasDeviceBuild -> InstallLabel.NoDeviceBuild
        unavailable -> InstallLabel.NotAvailable
        state.isPendingInstallReady -> InstallLabel.Ready
        target.showUpdateAffordance -> InstallLabel.Update
        target.offersOtherVersion -> InstallLabel.InstallVersion
        else -> InstallLabel.InstallLatest
    }
}

@Composable
private fun installLabelText(
    label: InstallLabel,
    target: InstallTarget,
): String =
    when (label) {
        InstallLabel.NoDeviceBuild -> stringResource(Res.string.no_build_for_this_device)
        InstallLabel.NotAvailable -> stringResource(Res.string.not_available)
        InstallLabel.Ready -> stringResource(Res.string.install_ready)
        InstallLabel.Update -> stringResource(Res.string.update_to_version, target.normSelected.orEmpty())
        InstallLabel.InstallVersion -> stringResource(Res.string.install_version, target.displaySelected ?: target.normSelected.orEmpty())
        InstallLabel.InstallLatest -> stringResource(Res.string.install_latest)
    }

@Composable
private fun InstallActionRow(
    enabled: Boolean,
    isActiveDownload: Boolean,
    target: InstallTarget,
    buttonText: String,
    primaryAsset: AssetUi?,
    state: DetailsState,
    progress: Int?,
    onAction: (DetailsAction) -> Unit,
    onPrimaryClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val idleDisabled = !enabled && !isActiveDownload
    val accent = if (idleDisabled) colors.surfaceContainerHigh else colors.primary
    val onAccent = if (idleDisabled) colors.disabledContent else colors.onPrimary
    val showOptionsPill = state.isObtainiumEnabled && !isActiveDownload
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PrimaryAction(
            modifier = Modifier.weight(1f),
            shape =
                if (showOptionsPill) {
                    RoundedCornerShape(
                        topStart = shape.corner,
                        bottomStart = shape.corner,
                        topEnd = shape.cornerSmall,
                        bottomEnd = shape.cornerSmall,
                    )
                } else {
                    RoundedCornerShape(shape.corner)
                },
            accent = accent,
            onAccent = onAccent,
            enabled = enabled,
            isActiveDownload = isActiveDownload,
            isUpdateAvailable = target.showUpdateAffordance,
            isInstalled = target.isInstalled,
            buttonText = buttonText,
            primaryAsset = primaryAsset,
            state = state,
            progress = progress,
            onClick = onPrimaryClick,
        )
        if (isActiveDownload) {
            DownloadControls(downloadStage = state.downloadStage, onAction = onAction)
        } else if (state.isObtainiumEnabled) {
            KomiDropdown(
                entries = installOptions(),
                onSelect = { item -> installOptionAction(item.id)?.let(onAction) },
            ) { open ->
                TrailingActionPill(
                    container = if (enabled) accent else colors.surfaceContainerHigh,
                    content = onAccent,
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(Res.string.show_install_options),
                    onClick = open,
                )
            }
        }
    }
}

@Composable
private fun DownloadControls(
    downloadStage: DownloadStage,
    onAction: (DetailsAction) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (downloadStage) {
            DownloadStage.DOWNLOADING -> {
                KomiSoftIconButton(
                    icon = Icons.Default.Pause,
                    contentDescription = stringResource(Res.string.pause),
                    onClick = { onAction(DetailsAction.PauseDownload) },
                    accent = true,
                    size = ButtonHeight,
                )
            }

            DownloadStage.PAUSED -> {
                KomiSoftIconButton(
                    icon = Icons.Default.PlayArrow,
                    contentDescription = stringResource(Res.string.resume),
                    onClick = { onAction(DetailsAction.ResumeDownload) },
                    accent = true,
                    size = ButtonHeight,
                )
            }

            DownloadStage.IDLE,
            DownloadStage.VERIFYING,
            DownloadStage.INSTALLING,
            -> {}
        }
        KomiSoftIconButton(
            icon = Icons.Default.Close,
            contentDescription = stringResource(Res.string.delete_task),
            onClick = { onAction(DetailsAction.DiscardDownload) },
            enabled = downloadStage != DownloadStage.INSTALLING,
            size = ButtonHeight,
        )
    }
}

@Composable
private fun PrimaryAction(
    shape: RoundedCornerShape,
    accent: Color,
    onAccent: Color,
    enabled: Boolean,
    isActiveDownload: Boolean,
    isUpdateAvailable: Boolean,
    isInstalled: Boolean,
    buttonText: String,
    primaryAsset: AssetUi?,
    state: DetailsState,
    progress: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val paused = state.downloadStage == DownloadStage.PAUSED
    val animatedFraction by animateFloatAsState(
        targetValue = progressFraction(isActiveDownload, state.downloadStage, progress),
        animationSpec = LocalPersonality.current.motion.defaultEffects(),
        label = "install-progress",
    )

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(ButtonHeight)
                .clip(shape)
                .then(if (isActiveDownload) Modifier.border(1.dp, colors.outlineVariant, shape) else Modifier)
                .drawBehind {
                    if (isActiveDownload) {
                        drawRect(colors.surfaceContainerHigh)
                        drawRect(
                            color = if (paused) colors.pausedProgressFill else colors.progressFill,
                            size = Size(size.width * animatedFraction, size.height),
                        )
                    } else {
                        drawRect(accent)
                    }
                }.clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (isActiveDownload) {
                DownloadingLabel(state = state, progress = progress, contentColor = colors.onSurface, isUpdateAvailable = isUpdateAvailable)
            } else {
                IdleLabel(
                    text = buttonText,
                    enabled = enabled,
                    contentColor = onAccent,
                    isUpdateAvailable = isUpdateAvailable,
                    isInstalled = isInstalled,
                    primaryAsset = primaryAsset,
                    state = state,
                )
            }
        }
    }
}

// A running or paused download fills to its progress; verifying and installing fill the whole button.
private fun progressFraction(
    isActiveDownload: Boolean,
    downloadStage: DownloadStage,
    progress: Int?,
): Float {
    val showsProgress = downloadStage == DownloadStage.DOWNLOADING || downloadStage == DownloadStage.PAUSED
    return when {
        !isActiveDownload -> 0f
        showsProgress -> (progress?.coerceIn(0, PERCENT_MAX) ?: 0) / PERCENT_MAX.toFloat()
        else -> 1f
    }
}

@Composable
private fun IdleLabel(
    text: String,
    enabled: Boolean,
    contentColor: Color,
    isUpdateAvailable: Boolean,
    isInstalled: Boolean,
    primaryAsset: AssetUi?,
    state: DetailsState,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val leadingIcon =
                when {
                    isUpdateAvailable -> Icons.Default.Update
                    isInstalled -> Icons.Default.CheckCircle
                    enabled -> Icons.Default.Download
                    else -> null
                }
            if (leadingIcon != null) {
                KomiIcon(imageVector = leadingIcon, contentDescription = null, modifier = Modifier.size(18.dp), tint = contentColor)
            }
            KomiText(
                text = text,
                role = KomiTextRole.ItemTitle,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (primaryAsset != null) {
            AssetSubtitle(asset = primaryAsset, state = state, contentColor = contentColor)
        }
    }
}

@Composable
private fun AssetSubtitle(
    asset: AssetUi,
    state: DetailsState,
    contentColor: Color,
) {
    val assetArch = extractArchitectureFromName(asset.name)
    val systemArch = state.systemArchitecture
    val subtitle = "${assetArch ?: systemArch.name.lowercase()}  ·  ${formatFileSize(asset.size)}"
    Row(
        modifier = Modifier.padding(top = 2.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KomiText(text = subtitle, role = KomiTextRole.Caption, color = contentColor.secondaryContent)
        if (assetArch != null && isExactArchitectureMatch(assetName = asset.name.lowercase(), systemArch = systemArch)) {
            Spacer(modifier = Modifier.width(4.dp))
            KomiIcon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = stringResource(Res.string.architecture_compatible),
                tint = contentColor.secondaryContent,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun DownloadingLabel(
    state: DetailsState,
    progress: Int?,
    contentColor: Color,
    isUpdateAvailable: Boolean,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        KomiText(
            text = downloadStageLabel(state.downloadStage, isUpdateAvailable),
            role = KomiTextRole.ItemTitle,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.downloadStage == DownloadStage.DOWNLOADING || state.downloadStage == DownloadStage.PAUSED) {
            Spacer(Modifier.height(2.dp))
            val totalBytes = state.totalBytes
            val progressText =
                if (totalBytes != null && totalBytes > 0) {
                    "${formatFileSize(state.downloadedBytes)} / ${formatFileSize(totalBytes)}"
                } else {
                    "${progress ?: 0}%"
                }
            KomiText(
                text = progressText,
                role = KomiTextRole.Caption,
                color = contentColor.secondaryContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun downloadStageLabel(
    downloadStage: DownloadStage,
    isUpdateAvailable: Boolean,
): String =
    when (downloadStage) {
        DownloadStage.DOWNLOADING -> stringResource(if (isUpdateAvailable) Res.string.updating else Res.string.downloading)
        DownloadStage.PAUSED -> stringResource(Res.string.paused)
        DownloadStage.VERIFYING -> stringResource(Res.string.verifying)
        DownloadStage.INSTALLING -> stringResource(if (isUpdateAvailable) Res.string.updating else Res.string.installing)
        DownloadStage.IDLE -> ""
    }

@Composable
private fun installOptions(): ImmutableList<KomiMenuEntry> =
    persistentListOf(
        KomiMenuItem(
            id = OPTION_OBTAINIUM,
            label = stringResource(Res.string.open_in_obtainium),
            subtitle = stringResource(Res.string.obtainium_description),
            icon = Icons.Default.Update,
        ),
        KomiMenuItem(
            id = OPTION_APP_MANAGER,
            label = stringResource(Res.string.inspect_with_appmanager),
            subtitle = stringResource(Res.string.appmanager_description),
            icon = Icons.Default.Security,
        ),
        KomiMenuItem(
            id = OPTION_EXTERNAL_INSTALLER,
            label = stringResource(Res.string.open_with_external_installer),
            subtitle = stringResource(Res.string.external_installer_description),
            icon = Icons.AutoMirrored.Filled.OpenInNew,
        ),
    )

private fun installOptionAction(id: String): DetailsAction? =
    when (id) {
        OPTION_OBTAINIUM -> DetailsAction.OpenInObtainium
        OPTION_APP_MANAGER -> DetailsAction.OpenInAppManager
        OPTION_EXTERNAL_INSTALLER -> DetailsAction.InstallWithExternalApp
        else -> null
    }

private const val OPTION_OBTAINIUM = "obtainium"
private const val OPTION_APP_MANAGER = "app_manager"
private const val OPTION_EXTERNAL_INSTALLER = "external_installer"
private const val PERCENT_MAX = 100

@Composable
private fun TrailingActionPill(
    container: Color,
    content: Color,
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val shape = LocalPersonality.current.shape
    val pillShape =
        RoundedCornerShape(
            topStart = shape.cornerSmall,
            bottomStart = shape.cornerSmall,
            topEnd = shape.corner,
            bottomEnd = shape.corner,
        )
    Box(
        modifier =
            Modifier
                .size(width = ButtonHeight, height = ButtonHeight)
                .clip(pillShape)
                .background(container)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        KomiIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = content,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun InstalledSplitRow(
    onUninstall: () -> Unit,
    onOpenApp: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val leftShape =
            RoundedCornerShape(
                topStart = shape.corner,
                bottomStart = shape.corner,
                topEnd = shape.cornerSmall,
                bottomEnd = shape.cornerSmall,
            )
        val rightShape =
            RoundedCornerShape(
                topStart = shape.cornerSmall,
                bottomStart = shape.cornerSmall,
                topEnd = shape.corner,
                bottomEnd = shape.corner,
            )
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(ButtonHeight)
                    .clip(leftShape)
                    .border(width = 1.dp, color = colors.error, shape = leftShape)
                    .clickable(onClick = onUninstall),
            contentAlignment = Alignment.Center,
        ) {
            SplitLabel(icon = Icons.Outlined.DeleteOutline, text = stringResource(Res.string.uninstall), color = colors.error)
        }
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .height(ButtonHeight)
                    .clip(rightShape)
                    .background(colors.primary)
                    .clickable(onClick = onOpenApp),
            contentAlignment = Alignment.Center,
        ) {
            SplitLabel(icon = Icons.AutoMirrored.Filled.OpenInNew, text = stringResource(Res.string.open_app), color = colors.onPrimary)
        }
    }
}

@Composable
private fun SplitLabel(
    icon: ImageVector,
    text: String,
    color: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KomiIcon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = color)
        KomiText(text = text, role = KomiTextRole.ItemTitle, color = color)
    }
}

@Composable
private fun AttestationBadge(attestationStatus: AttestationStatus) {
    AnimatedVisibility(
        visible =
            attestationStatus == AttestationStatus.VERIFIED ||
                attestationStatus == AttestationStatus.CHECKING ||
                attestationStatus == AttestationStatus.UNABLE_TO_VERIFY,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val visual = attestationVisual(attestationStatus)
        val explainer =
            when (attestationStatus) {
                AttestationStatus.VERIFIED -> stringResource(Res.string.details_attestation_verified_explainer)
                AttestationStatus.UNABLE_TO_VERIFY -> stringResource(Res.string.details_attestation_unverified_explainer)
                else -> null
            }
        val badge: @Composable () -> Unit = {
            AttestationChip(visual = visual, checking = attestationStatus == AttestationStatus.CHECKING)
        }
        if (explainer != null) {
            KomiRichTooltip(title = visual.label, text = explainer, modifier = Modifier.padding(top = 10.dp), content = badge)
        } else {
            Box(modifier = Modifier.padding(top = 10.dp)) { badge() }
        }
    }
}

@Composable
private fun attestationVisual(attestationStatus: AttestationStatus): AttestationVisual {
    val colors = LocalPersonality.current.colors
    return when (attestationStatus) {
        AttestationStatus.VERIFIED -> {
            AttestationVisual(
                container = colors.primaryContainer,
                content = colors.onPrimaryContainer,
                icon = Icons.Filled.VerifiedUser,
                label = stringResource(Res.string.verified_build),
            )
        }

        AttestationStatus.UNABLE_TO_VERIFY -> {
            AttestationVisual(
                container = colors.surfaceContainerHigh,
                content = colors.onSurfaceVariant,
                icon = Icons.Outlined.Warning,
                label = stringResource(Res.string.unable_to_verify_attestation),
            )
        }

        AttestationStatus.CHECKING -> {
            AttestationVisual(
                container = colors.surfaceContainerHigh,
                content = colors.onSurfaceVariant,
                icon = null,
                label = stringResource(Res.string.checking_attestation),
            )
        }

        else -> {
            AttestationVisual(container = Color.Transparent, content = Color.Transparent, icon = null, label = "")
        }
    }
}

@Composable
private fun AttestationChip(
    visual: AttestationVisual,
    checking: Boolean,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(shape.cornerSmall))
                .background(visual.container)
                .border(width = 0.5.dp, color = colors.divider, shape = RoundedCornerShape(shape.cornerSmall)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (checking) {
                KomiCircularProgress(modifier = Modifier.size(13.dp), color = visual.content)
            } else if (visual.icon != null) {
                KomiIcon(imageVector = visual.icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = visual.content)
            }
            KomiText(text = visual.label, role = KomiTextRole.LabelSmall, color = visual.content)
        }
    }
}

private class AttestationVisual(
    val container: Color,
    val content: Color,
    val icon: ImageVector?,
    val label: String,
)
