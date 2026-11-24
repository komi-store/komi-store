package zed.rainxch.details.presentation.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.outlined.Devices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.utils.AssetOwnership
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.inputs.KomiSwitch
import zed.rainxch.core.presentation.components.overlays.KomiDialog
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfaceElevation
import zed.rainxch.core.presentation.components.surfaces.KomiSurfacePaper
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.formatFileSize
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.details.presentation.model.AssetUi
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.assets_section_available
import zed.rainxch.githubstore.core.presentation.res.assets_section_your_app
import zed.rainxch.githubstore.core.presentation.res.assets_selection_label
import zed.rainxch.githubstore.core.presentation.res.assets_title
import zed.rainxch.githubstore.core.presentation.res.icon_content_description_info
import zed.rainxch.githubstore.core.presentation.res.installed
import zed.rainxch.githubstore.core.presentation.res.multiple_assets_info_dialog_text
import zed.rainxch.githubstore.core.presentation.res.multiple_assets_info_dialog_title
import zed.rainxch.githubstore.core.presentation.res.no_assets_in_list
import zed.rainxch.githubstore.core.presentation.res.no_assets_selected
import zed.rainxch.githubstore.core.presentation.res.platform_section_android
import zed.rainxch.githubstore.core.presentation.res.platform_section_linux
import zed.rainxch.githubstore.core.presentation.res.platform_section_macos
import zed.rainxch.githubstore.core.presentation.res.platform_section_windows
import zed.rainxch.githubstore.core.presentation.res.section_chip_for_transfer
import zed.rainxch.githubstore.core.presentation.res.section_chip_your_device
import zed.rainxch.githubstore.core.presentation.res.select_version
import zed.rainxch.githubstore.core.presentation.res.show_all_platforms_label
import zed.rainxch.githubstore.core.presentation.res.update
import zed.rainxch.githubstore.core.presentation.res.variant_picker_pinned
import zed.rainxch.githubstore.core.presentation.res.variant_picker_pinned_badge
import zed.rainxch.githubstore.core.presentation.res.variant_picker_unpin

@Composable
fun ReleaseAssetsPicker(
    onAction: (DetailsAction) -> Unit,
    assetsList: ImmutableList<AssetUi>,
    modifier: Modifier = Modifier,
    selectedAsset: AssetUi? = null,
    isPickerVisible: Boolean = false,
    pinnedVariant: String? = null,
    showAllPlatforms: Boolean = false,
    crossPlatformAssets: ImmutableList<AssetUi> = persistentListOf(),
    variantStatuses: ImmutableMap<Long, AssetOwnership.VariantStatus> = persistentMapOf(),
) {
    val isPickerEnabled = assetsList.isNotEmpty() || crossPlatformAssets.isNotEmpty()
    // The sheet sits beside the field rather than in its column, so opening it can't shift the field's layout.
    Box(modifier = modifier.wrapContentHeight()) {
        if (isPickerVisible) {
            AssetsSheet(
                assets =
                    AssetChoices(
                        installable = assetsList,
                        crossPlatform = crossPlatformAssets,
                        selected = selectedAsset,
                        pinnedVariant = pinnedVariant,
                        variantStatuses = variantStatuses,
                    ),
                showAllPlatforms = showAllPlatforms,
                onAction = onAction,
            )
        }
        AssetsField(
            selectedAsset = selectedAsset,
            enabled = isPickerEnabled,
            onClick = { onAction(DetailsAction.ToggleReleaseAssetsPicker) },
        )
    }
}

@Composable
private fun AssetsField(
    selectedAsset: AssetUi?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val rowShape = RoundedCornerShape(LocalPersonality.current.shape.corner)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KomiText(
            text = stringResource(Res.string.assets_title),
            role = KomiTextRole.Overline,
            color = colors.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(rowShape)
                    .border(width = 1.dp, color = colors.outline, shape = rowShape)
                    .background(colors.surface)
                    .clickable(enabled = enabled, onClick = onClick)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .heightIn(min = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KomiText(
                text = selectedAsset?.name ?: stringResource(Res.string.no_assets_selected),
                role = KomiTextRole.Label,
                color = colors.onSurface,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            KomiIcon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = stringResource(Res.string.select_version),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// The assets the sheet lists and how each relates to the installed apps and the pinned variant.
private class AssetChoices(
    val installable: ImmutableList<AssetUi>,
    val crossPlatform: ImmutableList<AssetUi>,
    val selected: AssetUi?,
    val pinnedVariant: String?,
    val variantStatuses: ImmutableMap<Long, AssetOwnership.VariantStatus>,
) {
    val installableIds: ImmutableSet<Long> = installable.map { it.id }.toImmutableSet()

    fun isPinned(asset: AssetUi): Boolean =
        !pinnedVariant.isNullOrBlank() && asset.variantTag?.equals(pinnedVariant, ignoreCase = true) == true
}

@Composable
private fun AssetsSheet(
    assets: AssetChoices,
    showAllPlatforms: Boolean,
    onAction: (DetailsAction) -> Unit,
) {
    var showInfoDialog by rememberSaveable { mutableStateOf(false) }
    ReleaseAssetsAboutDialog(showDialog = showInfoDialog, onDismiss = { showInfoDialog = false })
    KomiSheet(onDismiss = { onAction(DetailsAction.ToggleReleaseAssetsPicker) }, placement = KomiSheetPlacement.Bottom) {
        Column(modifier = Modifier.fillMaxWidth()) {
            AssetsSheetHeader(onInfo = { showInfoDialog = true })
            val pinnedVariant = assets.pinnedVariant
            if (!pinnedVariant.isNullOrBlank()) {
                PinnedVariantRow(pinnedVariant = pinnedVariant, onUnpin = { onAction(DetailsAction.UnpinPreferredVariant) })
            }
            ShowAllPlatformsRow(
                showAllPlatforms = showAllPlatforms,
                onToggle = { enabled -> onAction(DetailsAction.OnToggleShowAllPlatforms(enabled)) },
            )
            AssetsList(assets = assets, showAllPlatforms = showAllPlatforms, onAction = onAction)
        }
    }
}

@Composable
private fun AssetsSheetHeader(onInfo: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(vertical = 6.dp)) {
            KomiText(
                text = stringResource(Res.string.assets_title),
                role = KomiTextRole.CardTitle,
                color = LocalPersonality.current.colors.onSurface,
            )
        }
        KomiIconButton(
            icon = Icons.Outlined.Info,
            contentDescription = stringResource(Res.string.icon_content_description_info),
            onClick = onInfo,
            variant = KomiButtonVariant.Text,
        )
    }
}

@Composable
private fun PinnedVariantRow(
    pinnedVariant: String,
    onUnpin: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KomiText(
            text = stringResource(Res.string.variant_picker_pinned, pinnedVariant),
            role = KomiTextRole.Body,
            color = LocalPersonality.current.colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        KomiButton(
            onClick = onUnpin,
            label = stringResource(Res.string.variant_picker_unpin),
            variant = KomiButtonVariant.Text,
            size = KomiButtonSize.Sm,
        )
    }
}

@Composable
private fun ShowAllPlatformsRow(
    showAllPlatforms: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val rowShape = RoundedCornerShape(LocalPersonality.current.shape.corner)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(rowShape)
                .border(width = 1.dp, color = colors.outline, shape = rowShape)
                .background(colors.surface)
                .clickable(onClick = { onToggle(!showAllPlatforms) })
                .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        KomiIcon(imageVector = Icons.Outlined.Devices, contentDescription = null, tint = colors.onSurface)
        Spacer(Modifier.size(12.dp))
        KomiText(
            text = stringResource(Res.string.show_all_platforms_label),
            role = KomiTextRole.Body,
            color = colors.onSurface,
            modifier = Modifier.weight(1f),
        )
        KomiSwitch(checked = showAllPlatforms, onCheckedChange = onToggle)
    }
}

@Composable
private fun AssetsList(
    assets: AssetChoices,
    showAllPlatforms: Boolean,
    onAction: (DetailsAction) -> Unit,
) {
    val groups =
        remember(assets.crossPlatform) {
            assets.crossPlatform
                .mapNotNull { asset -> asset.platform?.let { it to asset } }
                .groupBy({ it.first }, { it.second })
        }
    val installedAssets = assets.installable.filter { it.id in assets.variantStatuses }.toImmutableList()
    val onSelect = { asset: AssetUi -> onAction(DetailsAction.SelectDownloadAsset(asset.id)) }
    val emptyText = stringResource(Res.string.no_assets_in_list)
    LazyColumn(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = LocalPersonality.current.motion.defaultSpatial())
                .heightIn(max = 420.dp),
        contentPadding = PaddingValues(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            showAllPlatforms && groups.isNotEmpty() -> platformSections(assets, groups, onAction)
            assets.installable.isEmpty() -> item { EmptyAssetsText(text = emptyText) }
            installedAssets.isEmpty() -> assetItems(assets.installable, assets, keyPrefix = null, onSelect = onSelect)
            else -> installedFirst(assets, installedAssets, onSelect)
        }
    }
}

// A card per platform, the ones this device can install first; other platforms' files open for transfer instead.
private fun LazyListScope.platformSections(
    assets: AssetChoices,
    groups: Map<DiscoveryPlatform, List<AssetUi>>,
    onAction: (DetailsAction) -> Unit,
) {
    PlatformSections
        .sortedByDescending { (platform, _) -> groups[platform]?.any { it.id in assets.installableIds } == true }
        .forEach { (platform, labelRes) ->
            val platformAssets = groups[platform].orEmpty()
            if (platformAssets.isNotEmpty()) {
                item(key = "section-${platform.name}") {
                    PlatformSectionCard(
                        platformLabel = stringResource(labelRes),
                        isCurrentDevice = platformAssets.any { it.id in assets.installableIds },
                        platformAssets = platformAssets.toImmutableList(),
                        assets = assets,
                        onAssetClick = { asset ->
                            onAction(
                                if (asset.id in assets.installableIds) {
                                    DetailsAction.SelectDownloadAsset(asset.id)
                                } else {
                                    DetailsAction.OnDownloadForTransfer(asset.downloadUrl)
                                },
                            )
                        },
                    )
                }
            }
        }
}

private val PlatformSections: List<Pair<DiscoveryPlatform, StringResource>> =
    listOf(
        DiscoveryPlatform.Android to Res.string.platform_section_android,
        DiscoveryPlatform.Windows to Res.string.platform_section_windows,
        DiscoveryPlatform.Macos to Res.string.platform_section_macos,
        DiscoveryPlatform.Linux to Res.string.platform_section_linux,
    )

// The installed variants under their own heading, then the rest of the release's assets.
private fun LazyListScope.installedFirst(
    assets: AssetChoices,
    installedAssets: ImmutableList<AssetUi>,
    onSelect: (AssetUi) -> Unit,
) {
    item(key = "section-header-installed") {
        val allInstalled = installedAssets.all { assets.variantStatuses[it.id] == AssetOwnership.VariantStatus.INSTALLED }
        SectionHeading(text = stringResource(if (allInstalled) Res.string.installed else Res.string.assets_section_your_app))
    }
    assetItems(installedAssets, assets, keyPrefix = "installed", onSelect = onSelect, showStatus = true)
    val installedIds = installedAssets.map { it.id }.toSet()
    val otherAssets = assets.installable.filter { it.id !in installedIds }
    if (otherAssets.isNotEmpty()) {
        item(key = "section-divider-available") {
            KomiHorizontalDivider(
                color = LocalPersonality.current.colors.divider,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
        }
        item(key = "section-header-available") { SectionHeading(text = stringResource(Res.string.assets_section_available)) }
        assetItems(otherAssets, assets, keyPrefix = "available", onSelect = onSelect)
    }
}

private fun LazyListScope.assetItems(
    list: List<AssetUi>,
    assets: AssetChoices,
    keyPrefix: String?,
    onSelect: (AssetUi) -> Unit,
    showStatus: Boolean = false,
) {
    items(items = list, key = { asset -> keyPrefix?.let { "$it-${asset.id}" } ?: asset.id }) { asset ->
        ReleaseAssetItem(
            asset = asset,
            isSelected = asset.id == assets.selected?.id,
            isPinned = assets.isPinned(asset),
            variantStatus = assets.variantStatuses[asset.id]?.takeIf { showStatus },
            onClick = { onSelect(asset) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SectionHeading(text: String) {
    KomiText(
        text = text,
        role = KomiTextRole.Overline,
        color = LocalPersonality.current.colors.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun EmptyAssetsText(text: String) {
    KomiText(
        text = text,
        role = KomiTextRole.Body,
        color = LocalPersonality.current.colors.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(16.dp),
    )
}

@Composable
private fun ReleaseAssetsAboutDialog(
    showDialog: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
) {
    if (!showDialog) return

    val colors = LocalPersonality.current.colors
    val dialogShape = RoundedCornerShape(LocalPersonality.current.shape.corner)
    KomiDialog(onDismissRequest = onDismiss, modifier = modifier, properties = properties) {
        Column(
            modifier =
                Modifier
                    .clip(dialogShape)
                    .background(colors.surface)
                    .border(width = 1.5.dp, color = colors.outline, shape = dialogShape)
                    .padding(24.dp),
        ) {
            KomiText(
                text = stringResource(Res.string.multiple_assets_info_dialog_title),
                role = KomiTextRole.CardTitle,
                color = colors.onSurface,
            )
            Spacer(Modifier.size(12.dp))
            KomiText(
                text = stringResource(Res.string.multiple_assets_info_dialog_text),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReleaseAssetItem(
    asset: AssetUi,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
    variantStatus: AssetOwnership.VariantStatus? = null,
) {
    val colors = LocalPersonality.current.colors
    Row(
        modifier =
            modifier
                .clickable(
                    onClickLabel = stringResource(Res.string.assets_selection_label),
                    onClick = onClick,
                ).padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KomiText(
                    text = asset.name,
                    role = KomiTextRole.ItemTitle,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 2,
                    color = if (isSelected) colors.primary else colors.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                variantBadgeOf(variantStatus)?.let { badge ->
                    Spacer(Modifier.width(6.dp))
                    AssetBadge(text = stringResource(badge), container = colors.surfaceVariant, content = colors.onSurfaceVariant)
                }
                if (isPinned) {
                    Spacer(Modifier.width(6.dp))
                    AssetBadge(
                        text = stringResource(Res.string.variant_picker_pinned_badge),
                        container = colors.primaryContainer,
                        content = colors.onPrimaryContainer,
                    )
                }
            }
            KomiText(
                text = formatFileSize(asset.size),
                role = KomiTextRole.Body,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isSelected) {
            Spacer(Modifier.width(8.dp))
            KomiIcon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun variantBadgeOf(status: AssetOwnership.VariantStatus?): StringResource? =
    when (status) {
        AssetOwnership.VariantStatus.INSTALLED -> Res.string.installed
        AssetOwnership.VariantStatus.UPDATE -> Res.string.update
        else -> null
    }

@Composable
private fun AssetBadge(
    text: String,
    container: Color,
    content: Color,
) {
    KomiText(
        text = text,
        role = KomiTextRole.Caption,
        color = content,
        modifier =
            Modifier
                .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
                .background(container)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Preview
@Composable
private fun ReleaseAssetsPickerItemPreview() {
    ReleaseAssetItem(
        asset =
            AssetUi(
                id = -1,
                name = "Random",
                size = 20 * 1024,
                downloadUrl = "",
                platform = null,
                variantTag = null,
            ),
        onClick = {},
        isSelected = false,
    )
}

@Composable
private fun PlatformSectionCard(
    platformLabel: String,
    isCurrentDevice: Boolean,
    platformAssets: ImmutableList<AssetUi>,
    assets: AssetChoices,
    onAssetClick: (AssetUi) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    KomiSurface(
        elevation = KomiSurfaceElevation.Flat,
        paper = if (isCurrentDevice) KomiSurfacePaper.Surface else KomiSurfacePaper.Background,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KomiText(
                    text = platformLabel,
                    role = KomiTextRole.CardTitle,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                SectionChip(
                    label =
                        stringResource(
                            if (isCurrentDevice) Res.string.section_chip_your_device else Res.string.section_chip_for_transfer,
                        ),
                )
            }
            KomiHorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(horizontal = 12.dp))
            platformAssets.forEachIndexed { index, asset ->
                val isInstallableHere = asset.id in assets.installableIds
                ReleaseAssetItem(
                    asset = asset,
                    isSelected = isInstallableHere && asset.id == assets.selected?.id,
                    isPinned = isInstallableHere && assets.isPinned(asset),
                    variantStatus = assets.variantStatuses[asset.id]?.takeIf { isInstallableHere },
                    onClick = { onAssetClick(asset) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (index < platformAssets.lastIndex) {
                    KomiHorizontalDivider(color = colors.divider, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionChip(label: String) {
    val colors = LocalPersonality.current.colors
    KomiText(
        text = label,
        role = KomiTextRole.LabelSmall,
        color = colors.onPrimaryContainer,
        modifier =
            Modifier
                .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
                .background(colors.primaryContainer)
                .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
