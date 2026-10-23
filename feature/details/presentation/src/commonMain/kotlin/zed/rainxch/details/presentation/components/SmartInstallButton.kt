package zed.rainxch.details.presentation.components

import zed.rainxch.core.presentation.utils.formatFileSize
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.core.presentation.components.buttons.KomiSoftIconButton
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.rememberTapCooldown
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.details.presentation.DetailsState
import zed.rainxch.details.presentation.model.AttestationStatus
import zed.rainxch.details.presentation.model.DownloadStage
import zed.rainxch.details.presentation.utils.extractArchitectureFromName
import zed.rainxch.details.presentation.utils.isExactArchitectureMatch
import zed.rainxch.githubstore.core.presentation.res.Res
import androidx.compose.material.icons.filled.Security
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.core.presentation.components.overlays.KomiDropdown
import zed.rainxch.core.presentation.components.overlays.KomiMenuEntry
import zed.rainxch.core.presentation.components.overlays.KomiMenuItem
import zed.rainxch.core.presentation.components.overlays.KomiRichTooltip
import zed.rainxch.githubstore.core.presentation.res.appmanager_description
import zed.rainxch.githubstore.core.presentation.res.external_installer_description
import zed.rainxch.githubstore.core.presentation.res.inspect_with_appmanager
import zed.rainxch.githubstore.core.presentation.res.obtainium_description
import zed.rainxch.githubstore.core.presentation.res.open_in_obtainium
import zed.rainxch.githubstore.core.presentation.res.open_with_external_installer
import zed.rainxch.githubstore.core.presentation.res.architecture_compatible
import zed.rainxch.githubstore.core.presentation.res.details_attestation_unverified_explainer
import zed.rainxch.githubstore.core.presentation.res.details_attestation_verified_explainer
import zed.rainxch.githubstore.core.presentation.res.checking_attestation
import zed.rainxch.githubstore.core.presentation.res.delete_task
import zed.rainxch.githubstore.core.presentation.res.downloading
import zed.rainxch.githubstore.core.presentation.res.install_latest
import zed.rainxch.githubstore.core.presentation.res.install_ready
import zed.rainxch.githubstore.core.presentation.res.install_version
import zed.rainxch.githubstore.core.presentation.res.installing
import zed.rainxch.githubstore.core.presentation.res.no_build_for_this_device
import zed.rainxch.githubstore.core.presentation.res.not_available
import zed.rainxch.githubstore.core.presentation.res.open_app
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
    primaryAsset: GithubAsset?,
    onAction: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
    state: DetailsState,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val primaryCooldown = rememberTapCooldown()
    val installedApp = state.installedApp
    val isInstalled = installedApp != null && !installedApp.isPendingInstall
    val isUpdateAvailable =
        installedApp?.isUpdateAvailable == true && !installedApp.isPendingInstall

    val normInstalled = installedApp?.installedVersion?.trim()?.takeIf { it.isNotBlank() }
    val normSelected = state.selectedRelease?.tagName?.trim()?.takeIf { it.isNotBlank() }
    val displaySelected = normSelected?.let { tag ->
        VersionMath.normalizeVersion(tag).takeIf { it.isNotBlank() } ?: tag
    }
    val selectedIsTimestampTracked =
        normSelected != null &&
            (VersionMath.isTimestampTrackedTag(normSelected) ||
                VersionMath.detectScheme(normSelected) == VersionMath.Scheme.Unknown)
    val latestBuildDiffers =
        installedApp != null &&
            VersionMath.assetBuildChanged(
                matchedReleaseId = null,
                matchedAssetId = installedApp.latestAssetId,
                matchedDigest = installedApp.latestAssetDigest,
                matchedSize = null,
                storedReleaseId = null,
                storedAssetId = installedApp.installedAssetId,
                storedDigest = installedApp.installedAssetDigest,
                storedSize = null,
            )
    val selectedIsLatestRelease = VersionMath.isExactSameVersion(normSelected, installedApp?.latestVersion)
    val isSameVersionInstalled =
        isInstalled &&
            normInstalled != null &&
            normSelected != null &&
            VersionMath.isExactSameVersion(normInstalled, normSelected) &&
            // An equal tag means the phone runs this build even when a stale update flag says
            // otherwise, unless the tag is judged by publish time or the offered file differs.
            !(
                (selectedIsTimestampTracked || latestBuildDiffers) &&
                    installedApp.isUpdateAvailable &&
                    selectedIsLatestRelease
            )
    val showUpdateAffordance = isUpdateAvailable && selectedIsLatestRelease

    val enabled = remember(primaryAsset, isDownloading, isInstalling) {
        primaryAsset != null && !isDownloading && !isInstalling
    } && !primaryCooldown.active
    val isActiveDownload = state.isDownloading || state.downloadStage != DownloadStage.IDLE

    if (isSameVersionInstalled && !isActiveDownload) {
        Column(modifier = modifier) {
            InstalledSplitRow(
                onUninstall = { onAction(DetailsAction.OnRequestUninstall) },
                onOpenApp = { onAction(DetailsAction.OpenApp) },
            )
            AttestationBadge(attestationStatus = state.attestationStatus)
        }
        return
    }

    val accent = when {
        !enabled && !isActiveDownload -> colors.surfaceContainerHigh
        else -> colors.primary
    }
    val onAccent = when {
        !enabled && !isActiveDownload -> colors.disabledContent
        else -> colors.onPrimary
    }

    val buttonText = when {
        !enabled && primaryAsset == null && !state.selectedHasDeviceBuild ->
            stringResource(Res.string.no_build_for_this_device)
        !enabled && primaryAsset == null -> stringResource(Res.string.not_available)
        state.isPendingInstallReady -> stringResource(Res.string.install_ready)
        showUpdateAffordance -> stringResource(
            Res.string.update_to_version,
            normSelected.orEmpty(),
        )
        isInstalled &&
            normInstalled != null &&
            normSelected != null &&
            !VersionMath.isExactSameVersion(normInstalled, normSelected) -> {
            stringResource(Res.string.install_version, displaySelected ?: normSelected)
        }
        normSelected != null &&
            state.allReleases.firstOrNull()?.tagName?.let { latestTag ->
                !VersionMath.isExactSameVersion(latestTag, normSelected)
            } == true -> {
            stringResource(Res.string.install_version, displaySelected ?: normSelected)
        }
        else -> stringResource(Res.string.install_latest)
    }

    val showOptionsPill = state.isObtainiumEnabled && !isActiveDownload

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val primaryShape = if (showOptionsPill) {
                RoundedCornerShape(
                    topStart = shape.corner,
                    bottomStart = shape.corner,
                    topEnd = shape.cornerSmall,
                    bottomEnd = shape.cornerSmall,
                )
            } else {
                RoundedCornerShape(shape.corner)
            }
            PrimaryAction(
                modifier = Modifier.weight(1f),
                shape = primaryShape,
                accent = accent,
                onAccent = onAccent,
                enabled = enabled,
                isActiveDownload = isActiveDownload,
                isUpdateAvailable = showUpdateAffordance,
                isInstalled = isInstalled,
                buttonText = buttonText,
                primaryAsset = primaryAsset,
                state = state,
                progress = progress,
                onClick = {
                    if (!state.isDownloading && state.downloadStage == DownloadStage.IDLE) {
                        primaryCooldown.run {
                            if (showUpdateAffordance) {
                                onAction(DetailsAction.UpdateApp)
                            } else {
                                onAction(DetailsAction.InstallPrimary)
                            }
                        }
                    }
                },
            )

            if (isActiveDownload) {
                when (state.downloadStage) {
                    DownloadStage.DOWNLOADING -> KomiSoftIconButton(
                        icon = Icons.Default.Pause,
                        contentDescription = stringResource(Res.string.pause),
                        onClick = { onAction(DetailsAction.PauseDownload) },
                        accent = true,
                        size = ButtonHeight,
                    )

                    DownloadStage.PAUSED -> KomiSoftIconButton(
                        icon = Icons.Default.PlayArrow,
                        contentDescription = stringResource(Res.string.resume),
                        onClick = { onAction(DetailsAction.ResumeDownload) },
                        accent = true,
                        size = ButtonHeight,
                    )

                    DownloadStage.IDLE,
                    DownloadStage.VERIFYING,
                    DownloadStage.INSTALLING,
                    -> Unit
                }

                KomiSoftIconButton(
                    icon = Icons.Default.Close,
                    contentDescription = stringResource(Res.string.delete_task),
                    onClick = { onAction(DetailsAction.DiscardDownload) },
                    enabled = state.downloadStage != DownloadStage.INSTALLING,
                    size = ButtonHeight,
                )
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

        AttestationBadge(attestationStatus = state.attestationStatus)
    }
}

@Composable
private fun PrimaryAction(
    modifier: Modifier,
    shape: RoundedCornerShape,
    accent: Color,
    onAccent: Color,
    enabled: Boolean,
    isActiveDownload: Boolean,
    isUpdateAvailable: Boolean,
    isInstalled: Boolean,
    buttonText: String,
    primaryAsset: GithubAsset?,
    state: DetailsState,
    progress: Int?,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val pct = progress?.coerceIn(0, 100) ?: 0
    val paused = state.downloadStage == DownloadStage.PAUSED
    val targetFraction = if (isActiveDownload &&
        (state.downloadStage == DownloadStage.DOWNLOADING || paused)
    ) {
        pct / 100f
    } else if (isActiveDownload) {
        1f
    } else {
        0f
    }
    val animatedFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = LocalPersonality.current.motion.defaultEffects(),
        label = "install-progress",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight)
            .clip(shape)
            .then(
                if (isActiveDownload) {
                    Modifier.border(1.dp, colors.outlineVariant, shape)
                } else {
                    Modifier
                },
            )
            .drawBehind {
                if (isActiveDownload) {
                    drawRect(colors.surfaceContainerHigh)
                    drawRect(
                        color = if (paused) {
                            colors.onSurfaceVariant.copy(alpha = 0.16f)
                        } else {
                            colors.primary.copy(alpha = 0.22f)
                        },
                        size = Size(size.width * animatedFraction, size.height),
                    )
                } else {
                    drawRect(accent)
                }
            }
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (isActiveDownload) {
                DownloadingLabel(
                    state = state,
                    progress = progress,
                    contentColor = colors.onSurface,
                    isUpdateAvailable = isUpdateAvailable,
                )
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

@Composable
private fun IdleLabel(
    text: String,
    enabled: Boolean,
    contentColor: Color,
    isUpdateAvailable: Boolean,
    isInstalled: Boolean,
    primaryAsset: GithubAsset?,
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
            val leadingIcon = when {
                isUpdateAvailable -> Icons.Default.Update
                isInstalled -> Icons.Default.CheckCircle
                enabled -> Icons.Default.Download
                else -> null
            }
            if (leadingIcon != null) {
                KomiIcon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = contentColor,
                )
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
            val assetArch = extractArchitectureFromName(primaryAsset.name)
            val systemArch = state.systemArchitecture
            val sizeText = formatFileSize(primaryAsset.size)
            val archLabel = assetArch ?: systemArch.name.lowercase()
            val subtitle = "$archLabel  ·  $sizeText"
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KomiText(
                    text = subtitle,
                    role = KomiTextRole.Caption,
                    color = contentColor.copy(alpha = 0.78f),
                )
                if (assetArch != null && isExactArchitectureMatch(
                        assetName = primaryAsset.name.lowercase(),
                        systemArch = systemArch,
                    )
                ) {
                    Spacer(modifier = Modifier.width(4.dp))
                    KomiIcon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(Res.string.architecture_compatible),
                        tint = contentColor.copy(alpha = 0.78f),
                        modifier = Modifier.size(12.dp),
                    )
                }
            }
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
        val label = when (state.downloadStage) {
            DownloadStage.DOWNLOADING -> if (isUpdateAvailable) {
                stringResource(Res.string.updating)
            } else {
                stringResource(Res.string.downloading)
            }
            DownloadStage.PAUSED -> stringResource(Res.string.paused)
            DownloadStage.VERIFYING -> stringResource(Res.string.verifying)
            DownloadStage.INSTALLING -> if (isUpdateAvailable) {
                stringResource(Res.string.updating)
            } else {
                stringResource(Res.string.installing)
            }
            DownloadStage.IDLE -> ""
        }
        KomiText(
            text = label,
            role = KomiTextRole.ItemTitle,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.downloadStage == DownloadStage.DOWNLOADING ||
            state.downloadStage == DownloadStage.PAUSED
        ) {
            Spacer(Modifier.height(2.dp))
            val progressText = if (state.totalBytes != null && state.totalBytes > 0) {
                "${formatFileSize(state.downloadedBytes)} / ${formatFileSize(state.totalBytes)}"
            } else {
                "${progress ?: 0}%"
            }
            KomiText(
                text = progressText,
                role = KomiTextRole.Caption,
                color = contentColor.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
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

@Composable
private fun TrailingActionPill(
    container: Color,
    content: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val shape = LocalPersonality.current.shape
    val pillShape = RoundedCornerShape(
        topStart = shape.cornerSmall,
        bottomStart = shape.cornerSmall,
        topEnd = shape.corner,
        bottomEnd = shape.corner,
    )
    Box(
        modifier = Modifier
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
        val leftShape = RoundedCornerShape(
            topStart = shape.corner,
            bottomStart = shape.corner,
            topEnd = shape.cornerSmall,
            bottomEnd = shape.cornerSmall,
        )
        val rightShape = RoundedCornerShape(
            topStart = shape.cornerSmall,
            bottomStart = shape.cornerSmall,
            topEnd = shape.corner,
            bottomEnd = shape.corner,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight)
                .clip(leftShape)
                .border(
                    width = 1.dp,
                    color = colors.error,
                    shape = leftShape,
                )
                .clickable(onClick = onUninstall),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KomiIcon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.error,
                )
                KomiText(
                    text = stringResource(Res.string.uninstall),
                    role = KomiTextRole.ItemTitle,
                    color = colors.error,
                )
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(ButtonHeight)
                .clip(rightShape)
                .background(colors.primary)
                .clickable(onClick = onOpenApp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KomiIcon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = colors.onPrimary,
                )
                KomiText(
                    text = stringResource(Res.string.open_app),
                    role = KomiTextRole.ItemTitle,
                    color = colors.onPrimary,
                )
            }
        }
    }
}

@Composable
private fun AttestationBadge(attestationStatus: AttestationStatus) {
    val colors = LocalPersonality.current.colors
    AnimatedVisibility(
        visible = attestationStatus == AttestationStatus.VERIFIED ||
            attestationStatus == AttestationStatus.CHECKING ||
            attestationStatus == AttestationStatus.UNABLE_TO_VERIFY,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val (container, content, icon, label) = when (attestationStatus) {
            AttestationStatus.VERIFIED -> AttestationVisual(
                container = colors.primaryContainer,
                content = colors.onPrimaryContainer,
                icon = Icons.Filled.VerifiedUser,
                label = stringResource(Res.string.verified_build),
            )
            AttestationStatus.UNABLE_TO_VERIFY -> AttestationVisual(
                container = colors.surfaceContainerHigh,
                content = colors.onSurfaceVariant,
                icon = Icons.Outlined.Warning,
                label = stringResource(Res.string.unable_to_verify_attestation),
            )
            AttestationStatus.CHECKING -> AttestationVisual(
                container = colors.surfaceContainerHigh,
                content = colors.onSurfaceVariant,
                icon = null,
                label = stringResource(Res.string.checking_attestation),
            )
            else -> AttestationVisual(
                container = Color.Transparent,
                content = Color.Transparent,
                icon = null,
                label = "",
            )
        }
        val explainer =
            when (attestationStatus) {
                AttestationStatus.VERIFIED -> stringResource(Res.string.details_attestation_verified_explainer)
                AttestationStatus.UNABLE_TO_VERIFY -> stringResource(Res.string.details_attestation_unverified_explainer)
                else -> null
            }
        val badge: @Composable () -> Unit = {
            AttestationChip(
                container = container,
                content = content,
                icon = icon,
                label = label,
                checking = attestationStatus == AttestationStatus.CHECKING,
            )
        }
        if (explainer != null) {
            KomiRichTooltip(title = label, text = explainer, modifier = Modifier.padding(top = 10.dp), content = badge)
        } else {
            Box(modifier = Modifier.padding(top = 10.dp)) { badge() }
        }
    }
}

@Composable
private fun AttestationChip(
    container: Color,
    content: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    label: String,
    checking: Boolean,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(shape.cornerSmall))
            .background(container)
            .border(
                width = 0.5.dp,
                color = colors.divider,
                shape = RoundedCornerShape(shape.cornerSmall),
            ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (checking) {
                KomiCircularProgress(
                    modifier = Modifier.size(13.dp),
                    color = content,
                )
            } else if (icon != null) {
                KomiIcon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = content,
                )
            }
            KomiText(
                text = label,
                role = KomiTextRole.LabelSmall,
                color = content,
            )
        }
    }
}

private data class AttestationVisual(
    val container: Color,
    val content: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
    val label: String,
)
