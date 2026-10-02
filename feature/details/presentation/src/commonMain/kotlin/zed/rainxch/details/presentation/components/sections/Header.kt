package zed.rainxch.details.presentation.components.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Update
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfaceElevation
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.details.presentation.DetailsState
import zed.rainxch.details.presentation.components.AppHeader
import zed.rainxch.details.presentation.components.LinkedRepoBanner
import zed.rainxch.details.presentation.components.ReleaseAssetsPicker
import zed.rainxch.details.presentation.components.ReleasesStatus
import zed.rainxch.details.presentation.components.ReleasesStatusCard
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.isReallyInstalled
import zed.rainxch.core.domain.utils.AssetVariant
import zed.rainxch.core.domain.utils.assetPlatformOf
import zed.rainxch.details.presentation.components.InspectApkButton
import zed.rainxch.details.presentation.components.SmartInstallButton
import zed.rainxch.details.presentation.components.VersionPicker
import zed.rainxch.details.presentation.components.VersionTypePicker
import zed.rainxch.details.presentation.model.DownloadStage
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.appmanager_description
import zed.rainxch.githubstore.core.presentation.res.external_installer_description
import zed.rainxch.githubstore.core.presentation.res.inspect_with_appmanager
import zed.rainxch.githubstore.core.presentation.res.obtainium_description
import zed.rainxch.githubstore.core.presentation.res.open_in_obtainium
import zed.rainxch.githubstore.core.presentation.res.open_with_external_installer

fun LazyListScope.header(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
) {
    item {
        if (state.repository != null) {
            SelectionContainer {
                AppHeader(
                    author = state.userProfile,
                    release = state.selectedRelease,
                    repository = state.repository,
                    installedApp = state.installedApp,
                    downloadStage = state.downloadStage,
                    downloadProgress = state.downloadProgressPercent,
                    isCurrentUserOwner = state.isCurrentUserOwner,
                    onPlatformClick = { platform ->
                        onAction(DetailsAction.OnPlatformChipClick(platform))
                    },
                    onOwnerClick = {
                        onAction(
                            DetailsAction.OpenDeveloperProfile(
                                state.repository.owner.login,
                            ),
                        )
                    },
                )
            }
        }
    }

    val installedApp = state.installedApp
    val repository = state.repository
    if (installedApp?.installSource == InstallSource.MANUAL && repository != null) {
        item {
            LinkedRepoBanner(
                owner = repository.owner.login,
                repo = repository.name,
                onUnlink = { onAction(DetailsAction.OnUnlinkExternalApp) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    val releasesStatus: ReleasesStatus? =
        when {
            state.repository == null -> null
            state.releasesLoadFailed -> ReleasesStatus.FAILED
            state.isRetryingReleases -> ReleasesStatus.RETRYING
            !state.isLoading && state.allReleases.isEmpty() -> ReleasesStatus.EMPTY
            else -> null
        }

    if (releasesStatus != null) {
        item {
            ReleasesStatusCard(
                status = releasesStatus,
                onRetry = { onAction(DetailsAction.RetryReleases) },
                modifier = Modifier.animateItem(),
            )
        }
    } else {

        if (state.allReleases.isNotEmpty()) {
            item {
                VersionTypePicker(
                    selectedCategory = state.selectedReleaseCategory,
                    onAction = onAction,
                    modifier = Modifier.fillMaxWidth().animateItem(),
                )
            }
        }

        if (state.allReleases.isNotEmpty() || state.installableAssets.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {

                    val crossPlatformAssets = remember(state.selectedRelease) {
                        state.selectedRelease
                            ?.assets
                            ?.filter {
                                assetPlatformOf(it.name) != null
                            }
                            .orEmpty()
                    }.toImmutableList()

                    val pinnedVariantLabel =
                        state.installedApp?.preferredAssetVariant?.let { stored ->
                            state.primaryAsset?.name?.let { name ->
                                AssetVariant.extract(name)?.takeIf { it.isNotBlank() }
                            } ?: stored
                        }
                    ReleaseAssetsPicker(
                        assetsList = state.installableAssets,
                        selectedAsset = state.primaryAsset,
                        isPickerVisible = state.isReleaseSelectorVisible,
                        pinnedVariant = pinnedVariantLabel,
                        showAllPlatforms = state.showAllPlatforms,
                        crossPlatformAssets = crossPlatformAssets,
                        installedApps = state.installedApps,
                        onAction = onAction,
                        modifier = Modifier.weight(.65f),
                    )
                    VersionPicker(
                        selectedRelease = state.selectedRelease,
                        filteredReleases = state.filteredReleases,
                        isPickerVisible = state.isVersionPickerVisible,
                        onAction = onAction,
                        modifier = Modifier.weight(.35f),
                    )
                }
            }
        }

        item {
            val canInspectApk = state.installedApp?.isReallyInstalled() == true

            val coachmarkActive =
                state.isApkInspectCoachmarkPending &&
                        canInspectApk &&
                        !state.isDownloading &&
                        !state.isInstalling &&
                        state.downloadStage == DownloadStage.IDLE &&
                        !state.isApkInspectSheetVisible
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SmartInstallButton(
                        isDownloading = state.isDownloading,
                        isInstalling = state.isInstalling,
                        progress = state.downloadProgressPercent,
                        primaryAsset = state.primaryAsset,
                        state = state,
                        onAction = onAction,
                        modifier = Modifier.weight(1f),
                    )
                    if (canInspectApk) {
                        InspectApkButton(
                            showCoachmark = coachmarkActive,
                            onClick = { onAction(DetailsAction.OnInspectApk) },
                            onCoachmarkDismiss = {
                                onAction(DetailsAction.OnAcknowledgeApkInspectCoachmark)
                            },
                        )
                    }
                }

                if (state.isInstallDropdownExpanded) {
                    val density = LocalDensity.current
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = with(density) { IntOffset(0, 20.dp.roundToPx()) },
                        onDismissRequest = { onAction(DetailsAction.OnToggleInstallDropdown) },
                        properties = PopupProperties(focusable = true),
                    ) {
                        KomiSurface(elevation = KomiSurfaceElevation.Modal) {
                            Column(modifier = Modifier.width(300.dp)) {
                                InstallOptionItem(
                                    title = stringResource(Res.string.open_in_obtainium),
                                    subtitle = stringResource(Res.string.obtainium_description),
                                    icon = Icons.Default.Update,
                                    onClick = { onAction(DetailsAction.OpenInObtainium) },
                                )
                                InstallOptionItem(
                                    title = stringResource(Res.string.inspect_with_appmanager),
                                    subtitle = stringResource(Res.string.appmanager_description),
                                    icon = Icons.Default.Security,
                                    onClick = { onAction(DetailsAction.OpenInAppManager) },
                                )
                                InstallOptionItem(
                                    title = stringResource(Res.string.open_with_external_installer),
                                    subtitle = stringResource(Res.string.external_installer_description),
                                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                                    onClick = { onAction(DetailsAction.InstallWithExternalApp) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InstallOptionItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KomiIcon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = colors.onSurface,
        )
        Column(modifier = Modifier.weight(1f)) {
            KomiText(
                text = title,
                role = KomiTextRole.Body,
                color = colors.onSurface,
                uppercase = false,
                fontSize = 14.sp,
                fontWeight = FontWeight.W600,
            )
            KomiText(
                text = subtitle,
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
                uppercase = false,
                fontSize = 12.sp,
            )
        }
    }
}
