package zed.rainxch.apps.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.apps.presentation.model.GithubAssetUi
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.inputs.KomiSwitch
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.formatFileSize
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.asset_filter_help
import zed.rainxch.githubstore.core.presentation.res.asset_filter_invalid
import zed.rainxch.githubstore.core.presentation.res.asset_filter_label
import zed.rainxch.githubstore.core.presentation.res.asset_filter_no_match
import zed.rainxch.githubstore.core.presentation.res.asset_filter_placeholder
import zed.rainxch.githubstore.core.presentation.res.asset_filter_visible_count
import zed.rainxch.githubstore.core.presentation.res.asset_none_available
import zed.rainxch.githubstore.core.presentation.res.cd_back
import zed.rainxch.githubstore.core.presentation.res.fallback_older_releases_description
import zed.rainxch.githubstore.core.presentation.res.fallback_older_releases_title
import zed.rainxch.githubstore.core.presentation.res.select_asset_description
import zed.rainxch.githubstore.core.presentation.res.select_asset_title

@Composable
internal fun PickAssetStep(
    allAssets: ImmutableList<GithubAssetUi>,
    visibleAssets: ImmutableList<GithubAssetUi>,
    selectedAsset: GithubAssetUi?,
    downloadProgress: Int?,
    validationStatus: String?,
    validationError: String?,
    filterValue: String,
    filterError: String?,
    fallbackEnabled: Boolean,
    onFilterChange: (String) -> Unit,
    onFallbackToggle: (Boolean) -> Unit,
    onAssetSelect: (GithubAssetUi) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val isProcessing = selectedAsset != null

    Column(
        modifier =
            modifier
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

        AssetFilterField(
            value = filterValue,
            error = filterError,
            allCount = allAssets.size,
            visibleCount = visibleAssets.size,
            enabled = !isProcessing,
            onValueChange = onFilterChange,
        )

        Spacer(Modifier.height(8.dp))

        FallbackToggleRow(
            enabled = fallbackEnabled,
            interactive = !isProcessing,
            onToggle = onFallbackToggle,
        )

        Spacer(Modifier.height(8.dp))

        AssetList(
            allAssets = allAssets,
            visibleAssets = visibleAssets,
            selectedAsset = selectedAsset,
            downloadProgress = downloadProgress,
            filterError = filterError,
            onAssetSelect = onAssetSelect,
        )

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

@Composable
private fun AssetFilterField(
    value: String,
    error: String?,
    allCount: Int,
    visibleCount: Int,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
) {
    val supporting =
        when {
            error != null -> stringResource(Res.string.asset_filter_invalid)
            visibleCount == 0 && value.isNotBlank() -> stringResource(Res.string.asset_filter_no_match)
            value.isNotBlank() -> pluralStringResource(Res.plurals.asset_filter_visible_count, allCount, visibleCount, allCount)
            else -> stringResource(Res.string.asset_filter_help)
        }
    KomiTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = stringResource(Res.string.asset_filter_label),
        placeholder = stringResource(Res.string.asset_filter_placeholder),
        leadingIcon = Icons.Rounded.FilterAlt,
        clearable = true,
        helper = if (error == null) supporting else null,
        error = if (error != null) supporting else null,
        enabled = enabled,
    )
}

@Composable
private fun FallbackToggleRow(
    enabled: Boolean,
    interactive: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = interactive) { onToggle(!enabled) }
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
            checked = enabled,
            onCheckedChange = onToggle,
            enabled = interactive,
        )
    }
}

@Composable
private fun AssetList(
    allAssets: ImmutableList<GithubAssetUi>,
    visibleAssets: ImmutableList<GithubAssetUi>,
    selectedAsset: GithubAssetUi?,
    downloadProgress: Int?,
    filterError: String?,
    onAssetSelect: (GithubAssetUi) -> Unit,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(260.dp),
    ) {
        items(
            items = visibleAssets,
            key = { it.id },
        ) { asset ->
            val isSelected = selectedAsset?.id == asset.id
            AssetRow(
                asset = asset,
                isSelected = isSelected,
                downloadProgress = downloadProgress.takeIf { isSelected },
                enabled = selectedAsset == null,
                onClick = { onAssetSelect(asset) },
            )

            KomiHorizontalDivider(
                color = LocalPersonality.current.colors.divider,
            )
        }

        if (visibleAssets.isEmpty()) {
            item {
                NoVisibleAssets(noneAvailable = allAssets.isEmpty(), filterInvalid = filterError != null)
            }
        }
    }
}

@Composable
private fun AssetRow(
    asset: GithubAssetUi,
    isSelected: Boolean,
    downloadProgress: Int?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(
                    if (isSelected) {
                        Modifier.background(colors.primaryTint, RoundedCornerShape(shape.cornerSmall))
                    } else {
                        Modifier
                    },
                ).clickable(enabled = enabled, onClick = onClick)
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

        if (downloadProgress != null) {
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
}

@Composable
private fun NoVisibleAssets(
    noneAvailable: Boolean,
    filterInvalid: Boolean,
) {
    val colors = LocalPersonality.current.colors
    val message =
        when {
            noneAvailable -> stringResource(Res.string.asset_none_available)
            filterInvalid -> stringResource(Res.string.asset_filter_invalid)
            else -> stringResource(Res.string.asset_filter_no_match)
        }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        KomiText(
            text = message,
            role = KomiTextRole.Body,
            color = if (!noneAvailable && filterInvalid) colors.error else colors.onSurfaceVariant,
        )
    }
}
