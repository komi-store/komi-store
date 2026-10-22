package zed.rainxch.details.presentation.components.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.isReallyInstalled
import zed.rainxch.core.domain.utils.AssetVariant
import zed.rainxch.core.domain.utils.assetPlatformOf
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.details.presentation.DetailsState
import zed.rainxch.details.presentation.components.AppHeader
import zed.rainxch.details.presentation.components.InspectApkButton
import zed.rainxch.details.presentation.components.LinkedRepoBanner
import zed.rainxch.details.presentation.components.ReleaseAssetsPicker
import zed.rainxch.details.presentation.components.ReleasesStatus
import zed.rainxch.details.presentation.components.ReleasesStatusCard
import zed.rainxch.details.presentation.components.SmartInstallButton
import zed.rainxch.details.presentation.components.VersionPicker
import zed.rainxch.details.presentation.components.VersionTypePicker
import zed.rainxch.details.presentation.model.DownloadStage
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.go_to_version
import zed.rainxch.githubstore.core.presentation.res.newer_releases_no_build_for_device

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
                    installedApps = state.installedApps,
                    onSelectInstalledApp = { packageName ->
                        onAction(DetailsAction.OnSelectInstalledApp(packageName))
                    },
                    downloadStage = state.downloadStage,
                    downloadProgress = state.downloadProgressPercent,
                    isCurrentUserOwner = state.isCurrentUserOwner,
                    onPlatformClick = { platform ->
                        onAction(DetailsAction.OnPlatformChipClick(platform))
                    },
                    platformReleases = state.platformReleases,
                    devicePlatform = state.devicePlatform,
                    appLabel = state.selectedAppLabel,
                    onAppLabelClick = { onAction(DetailsAction.ToggleVersionPicker) },
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
                    val crossPlatformAssets =
                        remember(state.selectedRelease) {
                            state.selectedRelease
                                ?.assets
                                ?.filter {
                                    assetPlatformOf(it.name) != null
                                }.orEmpty()
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
                        selectedReleaseTag = state.selectedRelease?.tagName,
                        onAction = onAction,
                        modifier = Modifier.weight(.65f),
                    )
                    VersionPicker(
                        selectedRelease = state.selectedRelease,
                        filteredReleases = state.filteredReleases,
                        isPickerVisible = state.isVersionPickerVisible,
                        onAction = onAction,
                        modifier = Modifier.weight(.35f),
                        devicePlatform = state.devicePlatform,
                        releasePlatforms = state.releasePlatforms,
                        deviceBuildReleaseIds = state.deviceBuildReleaseIds,
                        releaseLines = state.releaseLines,
                        selectedAppLabel = state.selectedAppLabel,
                        repoName = state.repository?.name.orEmpty(),
                    )
                }
            }
        }

        if (state.newerReleasesLackDeviceBuild) {
            item {
                ReleaseHint(
                    text = stringResource(Res.string.newer_releases_no_build_for_device),
                    onClick = { onAction(DetailsAction.ToggleVersionPicker) },
                )
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
            }
        }

        val deviceBuildTarget = state.deviceBuildTarget
        if (!state.selectedHasDeviceBuild && deviceBuildTarget != null) {
            item {
                ReleaseHint(
                    text = stringResource(Res.string.go_to_version, deviceBuildTarget.tagName),
                    onClick = { onAction(DetailsAction.OnJumpToDeviceBuild) },
                    emphasized = true,
                )
            }
        }
    }
}

@Composable
private fun ReleaseHint(
    text: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
) {
    val colors = LocalPersonality.current.colors
    val tint = if (emphasized) colors.primary else colors.onSurfaceVariant
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        KomiIcon(
            imageVector = if (emphasized) Icons.AutoMirrored.Filled.ArrowForward else Icons.Outlined.Info,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = tint,
        )
        KomiText(
            text = text,
            role = if (emphasized) KomiTextRole.Label else KomiTextRole.Body,
            color = tint,
        )
    }
}
