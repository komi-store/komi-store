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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.installation.InstallSource
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
import zed.rainxch.details.presentation.model.RepositoryUi
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.go_to_version
import zed.rainxch.githubstore.core.presentation.res.newer_releases_no_build_for_device

fun LazyListScope.header(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
) {
    val repository = state.repository
    item {
        if (repository != null) HeaderCard(state = state, repository = repository, onAction = onAction)
    }
    if (state.installedApp?.installSource == InstallSource.MANUAL && repository != null) {
        item {
            LinkedRepoBanner(
                owner = repository.ownerLogin,
                repo = repository.name,
                onUnlink = { onAction(DetailsAction.OnUnlinkExternalApp) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    val releasesStatus = releasesStatusOf(state)
    if (releasesStatus != null) {
        item {
            ReleasesStatusCard(
                status = releasesStatus,
                onRetry = { onAction(DetailsAction.RetryReleases) },
                modifier = Modifier.animateItem(),
            )
        }
    } else {
        releaseControls(state, onAction)
    }
}

private fun releasesStatusOf(state: DetailsState): ReleasesStatus? =
    when {
        state.repository == null -> null
        state.releasesLoadFailed -> ReleasesStatus.FAILED
        state.isRetryingReleases -> ReleasesStatus.RETRYING
        !state.isLoading && state.allReleases.isEmpty() -> ReleasesStatus.EMPTY
        else -> null
    }

private fun LazyListScope.releaseControls(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
) {
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
        item { PickerRow(state = state, onAction = onAction) }
    }
    if (state.newerReleasesLackDeviceBuild) {
        item {
            ReleaseHint(
                text = stringResource(Res.string.newer_releases_no_build_for_device),
                onClick = { onAction(DetailsAction.ToggleVersionPicker) },
            )
        }
    }
    item { InstallRow(state = state, onAction = onAction) }
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

@Composable
private fun HeaderCard(
    state: DetailsState,
    repository: RepositoryUi,
    onAction: (DetailsAction) -> Unit,
) {
    SelectionContainer {
        AppHeader(
            author = state.userProfile,
            repository = repository,
            installedApp = state.installedApp,
            installedApps = state.installedApps,
            onSelectInstalledApp = { packageName -> onAction(DetailsAction.OnSelectInstalledApp(packageName)) },
            downloadStage = state.downloadStage,
            downloadProgress = state.downloadProgressPercent,
            isCurrentUserOwner = state.isCurrentUserOwner,
            onPlatformClick = { platform -> onAction(DetailsAction.OnPlatformChipClick(platform)) },
            platformReleases = state.platformReleases,
            devicePlatform = state.devicePlatform,
            appLabel = state.selectedAppLabel,
            onAppLabelClick = { onAction(DetailsAction.ToggleVersionPicker) },
            onOwnerClick = { onAction(DetailsAction.OpenDeveloperProfile(repository.ownerLogin)) },
        )
    }
}

@Composable
private fun PickerRow(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ReleaseAssetsPicker(
            assetsList = state.installableAssets,
            selectedAsset = state.primaryAsset,
            isPickerVisible = state.isReleaseSelectorVisible,
            pinnedVariant = state.pinnedVariantLabel,
            showAllPlatforms = state.showAllPlatforms,
            crossPlatformAssets = state.crossPlatformAssets,
            variantStatuses = state.assetVariantStatuses,
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

@Composable
private fun InstallRow(
    state: DetailsState,
    onAction: (DetailsAction) -> Unit,
) {
    val canInspectApk = state.installedApp?.isPendingInstall == false
    val coachmarkActive =
        state.isApkInspectCoachmarkPending &&
            canInspectApk &&
            !state.isApkInspectSheetVisible &&
            isIdle(state)
    Box(modifier = Modifier.fillMaxWidth()) {
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
                    onCoachmarkDismiss = { onAction(DetailsAction.OnAcknowledgeApkInspectCoachmark) },
                )
            }
        }
    }
}

private fun isIdle(state: DetailsState): Boolean = !state.isDownloading && !state.isInstalling && state.downloadStage == DownloadStage.IDLE

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
