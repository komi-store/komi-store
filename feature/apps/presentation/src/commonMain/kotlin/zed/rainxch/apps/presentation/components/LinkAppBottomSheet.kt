package zed.rainxch.apps.presentation.components

import zed.rainxch.core.presentation.utils.formatFileSize
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.inputs.KomiSwitch
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.AppsAction
import zed.rainxch.apps.presentation.AppsState
import zed.rainxch.apps.presentation.model.DeviceAppUi
import zed.rainxch.apps.presentation.model.LinkStep
import zed.rainxch.apps.presentation.model.GithubAssetUi
import zed.rainxch.core.domain.model.installation.InstallerCategory
import zed.rainxch.core.domain.system.RepoMatchSource
import zed.rainxch.core.domain.system.RepoMatchSuggestion
import zed.rainxch.githubstore.core.presentation.res.*

@Composable
fun LinkAppBottomSheet(
    state: AppsState,
    onAction: (AppsAction) -> Unit,
) {
    KomiSheet(
        onDismiss = { onAction(AppsAction.OnDismissLinkSheet) },
        placement = KomiSheetPlacement.Bottom,
    ) {
        AnimatedContent(
            targetState = state.linkStep,
            transitionSpec = {
                if (targetState.ordinal > initialState.ordinal) {
                    (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
                } else {
                    (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
                }
            },
            label = "link_step",
        ) { step ->
            when (step) {
                LinkStep.PickApp -> PickAppStep(
                    deviceApps = state.filteredDeviceApps,
                    searchQuery = state.deviceAppSearchQuery,
                    onSearchChange = { onAction(AppsAction.OnDeviceAppSearchChange(it)) },
                    onAppSelected = { onAction(AppsAction.OnDeviceAppSelected(it)) },
                )

                LinkStep.SmartMatch -> SmartMatchStep(
                    selectedApp = state.selectedDeviceApp,
                    loading = state.linkSearchLoading,
                    suggestions = state.linkSuggestions,
                    error = state.linkSearchError,
                    isValidating = state.isValidatingRepo,
                    validationStatus = state.linkValidationStatus,
                    onSuggestionSelected = { owner, repo, sourceHost ->
                        onAction(AppsAction.OnLinkSuggestionSelected(owner, repo, sourceHost))
                    },
                    onEnterUrlManually = { onAction(AppsAction.OnLinkEnterUrlManually) },
                    onRetry = { onAction(AppsAction.OnRetryLinkSearch) },
                    onBack = { onAction(AppsAction.OnBackToAppPicker) },
                )

                LinkStep.EnterUrl -> EnterUrlStep(
                    selectedApp = state.selectedDeviceApp,
                    repoUrl = state.repoUrl,
                    isValidating = state.isValidatingRepo,
                    validationError = state.repoValidationError,
                    validationStatus = state.linkValidationStatus,
                    onUrlChanged = { onAction(AppsAction.OnRepoUrlChanged(it)) },
                    onConfirm = { onAction(AppsAction.OnValidateAndLinkRepo) },
                    onBack = { onAction(AppsAction.OnBackToSmartMatch) },
                )

                LinkStep.PickAsset -> PickAssetStep(
                    allAssets = state.linkInstallableAssets,
                    visibleAssets = state.filteredLinkAssets,
                    selectedAsset = state.linkSelectedAsset,
                    downloadProgress = state.linkDownloadProgress,
                    validationStatus = state.linkValidationStatus,
                    validationError = state.repoValidationError,
                    filterValue = state.linkAssetFilter,
                    filterError = state.linkAssetFilterError,
                    fallbackEnabled = state.linkFallbackToOlder,
                    onFilterChanged = { onAction(AppsAction.OnLinkAssetFilterChanged(it)) },
                    onFallbackToggled = { onAction(AppsAction.OnLinkFallbackToggled(it)) },
                    onAssetSelected = { onAction(AppsAction.OnLinkAssetSelected(it)) },
                    onBack = { onAction(AppsAction.OnBackToEnterUrl) },
                )
            }
        }
    }
}

@Composable
private fun PickAppStep(
    deviceApps: ImmutableList<DeviceAppUi>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onAppSelected: (DeviceAppUi) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        KomiText(
            text = stringResource(Res.string.link_app_title),
            role = KomiTextRole.CardTitle,
            color = colors.onSurface,
        )

        Spacer(Modifier.height(4.dp))

        KomiText(
            text = stringResource(Res.string.pick_installed_app),
            role = KomiTextRole.Body,
            color = colors.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        KomiTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(Res.string.search_apps_hint),
            leadingIcon = Icons.Rounded.Search,
            clearable = true,
        )

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp),
        ) {
            items(
                items = deviceApps,
                key = { it.packageName },
            ) { app ->
                DeviceAppItem(
                    app = app,
                    onClick = { onAppSelected(app) },
                )

                KomiHorizontalDivider(
                    color = colors.divider,
                )
            }

            if (deviceApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        KomiText(
                            text = stringResource(Res.string.no_apps_found),
                            role = KomiTextRole.Body,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun DeviceAppItem(
    app: DeviceAppUi,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            KomiText(
                text = app.appName,
                role = KomiTextRole.Label,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(shape.cornerSmall))
                        .background(
                            when (app.installerCategory) {
                                InstallerCategory.SIDE_STORE -> colors.primaryContainer
                                InstallerCategory.SIDELOADED -> colors.primaryContainer
                                InstallerCategory.VENDOR_STORE -> colors.primaryContainer
                                InstallerCategory.PLAY_STORE -> colors.surfaceVariant
                                InstallerCategory.SYSTEM_UPDATE -> colors.surfaceVariant
                            },
                        ),
                ) {
                    KomiText(
                        text = when (app.installerCategory) {
                            InstallerCategory.SIDE_STORE -> stringResource(Res.string.installer_category_side_store)
                            InstallerCategory.SIDELOADED -> stringResource(Res.string.installer_category_sideloaded)
                            InstallerCategory.VENDOR_STORE -> stringResource(Res.string.installer_category_vendor_store)
                            InstallerCategory.PLAY_STORE -> stringResource(Res.string.installer_category_play_store)
                            InstallerCategory.SYSTEM_UPDATE -> stringResource(Res.string.installer_category_system_update)
                        },
                        role = KomiTextRole.Caption,
                        color = when (app.installerCategory) {
                            InstallerCategory.SIDE_STORE -> colors.onPrimaryContainer
                            InstallerCategory.SIDELOADED -> colors.onPrimaryContainer
                            InstallerCategory.VENDOR_STORE -> colors.onPrimaryContainer
                            InstallerCategory.PLAY_STORE -> colors.onSurfaceVariant
                            InstallerCategory.SYSTEM_UPDATE -> colors.onSurfaceVariant
                        },
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Spacer(Modifier.width(6.dp))

                KomiText(
                    text = app.packageName,
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        app.versionName?.let { version ->
            KomiText(
                text = version,
                role = KomiTextRole.Caption,
                color = colors.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 96.dp),
            )
        }
    }
}

@Composable
private fun SmartMatchStep(
    selectedApp: DeviceAppUi?,
    loading: Boolean,
    suggestions: ImmutableList<RepoMatchSuggestion>,
    error: String?,
    isValidating: Boolean,
    validationStatus: String?,
    onSuggestionSelected: (owner: String, repo: String, sourceHost: String?) -> Unit,
    onEnterUrlManually: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KomiIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.cd_back),
                onClick = onBack,
                enabled = !isValidating,
            )

            KomiText(
                text = stringResource(Res.string.link_smart_search_title),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        if (selectedApp != null) {
            KomiText(
                text = selectedApp.appName,
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            KomiText(
                text = selectedApp.packageName,
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))

        when {
            loading -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KomiCircularProgress(
                        modifier = Modifier.size(20.dp),
                    )

                    Spacer(Modifier.width(12.dp))

                    KomiText(
                        text = stringResource(Res.string.link_smart_search_searching),
                        role = KomiTextRole.Body,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            isValidating -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KomiCircularProgress(
                        modifier = Modifier.size(20.dp),
                    )

                    Spacer(Modifier.width(12.dp))

                    KomiText(
                        text = validationStatus ?: stringResource(Res.string.validating_repo),
                        role = KomiTextRole.Body,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            error != null -> {
                KomiText(
                    text = stringResource(Res.string.link_smart_search_failed),
                    role = KomiTextRole.Body,
                    color = colors.error,
                )

                Spacer(Modifier.height(8.dp))

                KomiButton(
                    onClick = onRetry,
                    label = stringResource(Res.string.retry),
                    variant = KomiButtonVariant.Tonal,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            suggestions.isEmpty() -> {
                KomiText(
                    text = stringResource(Res.string.link_smart_search_no_matches),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                ) {
                    items(
                        items = suggestions,
                        key = { "${it.sourceHost ?: "github"}|${it.owner}/${it.repo}" },
                    ) { suggestion ->
                        SuggestionRow(
                            suggestion = suggestion,
                            onClick = {
                                onSuggestionSelected(
                                    suggestion.owner,
                                    suggestion.repo,
                                    suggestion.sourceHost,
                                )
                            },
                        )

                        KomiHorizontalDivider(
                            color = colors.divider,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        KomiButton(
            onClick = onEnterUrlManually,
            label = stringResource(Res.string.link_smart_search_enter_manually),
            variant = KomiButtonVariant.Tonal,
            enabled = !isValidating,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SuggestionRow(
    suggestion: RepoMatchSuggestion,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            KomiText(
                text = "${suggestion.owner}/${suggestion.repo}",
                role = KomiTextRole.Label,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            val description = suggestion.description?.takeIf { it.isNotBlank() }
            if (description != null) {
                KomiText(
                    text = description,
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val sourceHost = suggestion.sourceHost
                val (hostLabel, hostBg, hostFg) = when {
                    sourceHost == null ->
                        Triple("GitHub", colors.surfaceVariant, colors.onSurfaceVariant)
                    sourceHost.equals("codeberg.org", ignoreCase = true) ->
                        Triple("Codeberg", colors.primaryContainer, colors.onPrimaryContainer)
                    else ->
                        Triple(sourceHost, colors.primaryContainer, colors.onPrimaryContainer)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(shape.cornerSmall))
                        .background(hostBg),
                ) {
                    KomiText(
                        text = hostLabel,
                        role = KomiTextRole.Caption,
                        color = hostFg,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Spacer(Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(shape.cornerSmall))
                        .background(colors.primaryContainer),
                ) {
                    KomiText(
                        text = when (suggestion.source) {
                            RepoMatchSource.MANIFEST -> stringResource(Res.string.match_source_manifest)
                            RepoMatchSource.FINGERPRINT -> stringResource(Res.string.match_source_fingerprint)
                            RepoMatchSource.SEARCH -> stringResource(Res.string.match_source_search)
                            RepoMatchSource.MANUAL -> stringResource(Res.string.match_source_manual)
                            RepoMatchSource.FORGEJO_SEARCH -> stringResource(Res.string.match_source_search)
                            RepoMatchSource.STARRED -> stringResource(Res.string.match_source_starred)
                        },
                        role = KomiTextRole.Caption,
                        color = colors.onPrimaryContainer,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Spacer(Modifier.width(6.dp))

                KomiText(
                    text = "${(suggestion.confidence * 100).toInt()}%",
                    role = KomiTextRole.Caption,
                    color = colors.onSurfaceVariant,
                )

                suggestion.stars?.let { stars ->
                    Spacer(Modifier.width(8.dp))

                    KomiIcon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(12.dp),
                    )

                    Spacer(Modifier.width(2.dp))

                    KomiText(
                        text = "$stars",
                        role = KomiTextRole.Caption,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}


@Composable
private fun EnterUrlStep(
    selectedApp: DeviceAppUi?,
    repoUrl: String,
    isValidating: Boolean,
    validationError: String?,
    validationStatus: String?,
    onUrlChanged: (String) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KomiIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.cd_back),
                onClick = onBack,
            )

            KomiText(
                text = stringResource(Res.string.link_app_title),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
            )
        }

        Spacer(Modifier.height(16.dp))

        if (selectedApp != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(shape.corner))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    KomiText(
                        text = selectedApp.appName,
                        role = KomiTextRole.CardTitle,
                        color = colors.onSurface,
                    )

                    KomiText(
                        text = selectedApp.packageName,
                        role = KomiTextRole.Body,
                        color = colors.onSurfaceVariant,
                    )
                }

                selectedApp.versionName?.let {
                    KomiText(
                        text = it,
                        role = KomiTextRole.Caption,
                        color = colors.primary,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        KomiTextField(
            value = repoUrl,
            onValueChange = onUrlChanged,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.enter_repo_url),
            placeholder = stringResource(Res.string.repo_url_hint),
            error = validationError,
        )

        Spacer(Modifier.height(20.dp))

        KomiButton(
            onClick = onConfirm,
            label = if (isValidating) {
                stringResource(Res.string.validating_repo)
            } else {
                stringResource(Res.string.link_and_track)
            },
            variant = KomiButtonVariant.Tonal,
            enabled = repoUrl.isNotBlank() && !isValidating,
            loading = isValidating,
            modifier = Modifier.fillMaxWidth(),
        )

        if (isValidating && validationStatus != null) {
            Spacer(Modifier.height(8.dp))

            KomiText(
                text = validationStatus,
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PickAssetStep(
    allAssets: ImmutableList<GithubAssetUi>,
    visibleAssets: ImmutableList<GithubAssetUi>,
    selectedAsset: GithubAssetUi?,
    downloadProgress: Int?,
    validationStatus: String?,
    validationError: String?,
    filterValue: String,
    filterError: String?,
    fallbackEnabled: Boolean,
    onFilterChanged: (String) -> Unit,
    onFallbackToggled: (Boolean) -> Unit,
    onAssetSelected: (GithubAssetUi) -> Unit,
    onBack: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val isProcessing = selectedAsset != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KomiIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(Res.string.cd_back),
                onClick = onBack,
                enabled = !isProcessing,
            )

            KomiText(
                text = stringResource(Res.string.select_asset_title),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
            )
        }

        Spacer(Modifier.height(4.dp))

        KomiText(
            text = stringResource(Res.string.select_asset_description),
            role = KomiTextRole.Body,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )

        Spacer(Modifier.height(12.dp))

        val filterSupporting = when {
            filterError != null -> stringResource(Res.string.asset_filter_invalid)
            visibleAssets.isEmpty() && filterValue.isNotBlank() ->
                stringResource(Res.string.asset_filter_no_match)
            filterValue.isNotBlank() ->
                pluralStringResource(
                    Res.plurals.asset_filter_visible_count,
                    allAssets.size,
                    visibleAssets.size,
                    allAssets.size,
                )
            else -> stringResource(Res.string.asset_filter_help)
        }
        KomiTextField(
            value = filterValue,
            onValueChange = onFilterChanged,
            modifier = Modifier.fillMaxWidth(),
            label = stringResource(Res.string.asset_filter_label),
            placeholder = stringResource(Res.string.asset_filter_placeholder),
            leadingIcon = Icons.Rounded.FilterAlt,
            clearable = true,
            helper = if (filterError == null) filterSupporting else null,
            error = if (filterError != null) filterSupporting else null,
            enabled = !isProcessing,
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isProcessing) { onFallbackToggled(!fallbackEnabled) }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                KomiText(
                    text = stringResource(Res.string.fallback_older_releases_title),
                    role = KomiTextRole.Label,
                    color = colors.onSurface,
                )

                KomiText(
                    text = stringResource(Res.string.fallback_older_releases_description),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                )
            }

            KomiSwitch(
                checked = fallbackEnabled,
                onCheckedChange = onFallbackToggled,
                enabled = !isProcessing,
            )
        }

        Spacer(Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
        ) {
            items(
                items = visibleAssets,
                key = { it.id },
            ) { asset ->
                val isSelected = selectedAsset?.id == asset.id

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isSelected) {
                                Modifier.background(
                                    colors.primaryTint,
                                    RoundedCornerShape(shape.cornerSmall),
                                )
                            } else {
                                Modifier
                            },
                        )
                        .clickable(enabled = !isProcessing) { onAssetSelected(asset) }
                        .padding(vertical = 12.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        KomiText(
                            text = asset.name,
                            role = if (isSelected) KomiTextRole.Label else KomiTextRole.Body,
                            color = colors.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        KomiText(
                            text = formatFileSize(asset.size),
                            role = KomiTextRole.Body,
                            color = colors.onSurfaceVariant,
                        )
                    }

                    if (isSelected && downloadProgress != null) {
                        Spacer(Modifier.width(8.dp))

                        KomiCircularProgress(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.size(24.dp),
                        )

                        Spacer(Modifier.width(4.dp))

                        KomiText(
                            text = "$downloadProgress%",
                            role = KomiTextRole.Caption,
                            color = colors.primary,
                        )
                    }
                }

                KomiHorizontalDivider(
                    color = colors.divider,
                )
            }

            if (visibleAssets.isEmpty()) {
                item {
                    val (message, isError) = when {
                        allAssets.isEmpty() ->
                            stringResource(Res.string.asset_none_available) to false
                        filterError != null ->
                            stringResource(Res.string.asset_filter_invalid) to true
                        else ->
                            stringResource(Res.string.asset_filter_no_match) to false
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        KomiText(
                            text = message,
                            role = KomiTextRole.Body,
                            color =
                                if (isError) {
                                    colors.error
                                } else {
                                    colors.onSurfaceVariant
                                },
                        )
                    }
                }
            }
        }

        if (validationStatus != null) {
            Spacer(Modifier.height(8.dp))
            KomiText(
                text = validationStatus,
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
            )
        }

        if (validationError != null) {
            Spacer(Modifier.height(8.dp))
            KomiText(
                text = validationError,
                role = KomiTextRole.Body,
                color = colors.error,
            )
        }
    }
}

