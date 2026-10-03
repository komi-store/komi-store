package zed.rainxch.details.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.isEffectivelyPreRelease
import zed.rainxch.core.domain.model.account.github.preReleaseLabel
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.chips.KomiChipSize
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.formatIsoDateOrRaw
import zed.rainxch.core.presentation.utils.toIcon
import zed.rainxch.core.presentation.utils.toLabel
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.details.presentation.utils.releaseLineLabel
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.category_all
import zed.rainxch.githubstore.core.presentation.res.latest_badge
import zed.rainxch.githubstore.core.presentation.res.latest_for_platform
import zed.rainxch.githubstore.core.presentation.res.no_build_for_this_device
import zed.rainxch.githubstore.core.presentation.res.no_version_selected
import zed.rainxch.githubstore.core.presentation.res.not_available
import zed.rainxch.githubstore.core.presentation.res.pre_release_badge
import zed.rainxch.githubstore.core.presentation.res.select_version
import zed.rainxch.githubstore.core.presentation.res.versions_title

@Composable
fun VersionPicker(
    selectedRelease: GithubRelease?,
    filteredReleases: ImmutableList<GithubRelease>,
    isPickerVisible: Boolean,
    onAction: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
    devicePlatform: DiscoveryPlatform? = null,
    releasePlatforms: ImmutableMap<Long, Set<DiscoveryPlatform>> = persistentMapOf(),
    deviceBuildReleaseIds: ImmutableSet<Long> = persistentSetOf(),
    releaseLines: ImmutableMap<Long, String> = persistentMapOf(),
    selectedAppLabel: String? = null,
    repoName: String = "",
) {
    val colors = LocalPersonality.current.colors
    val isPickerEnabled = filteredReleases.isNotEmpty()

    val rowShape = RoundedCornerShape(LocalPersonality.current.shape.corner)
    Column(
        modifier = modifier.wrapContentHeight(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        KomiText(
            text = stringResource(Res.string.versions_title),
            role = KomiTextRole.Label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = colors.primary,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .border(
                    width = 1.dp,
                    color = colors.outline,
                    shape = rowShape,
                )
                .background(colors.surface)
                .clickable(enabled = isPickerEnabled) {
                    onAction(DetailsAction.ToggleVersionPicker)
                }
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .heightIn(min = 36.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                KomiText(
                    text = selectedRelease?.let { release ->
                        if (selectedAppLabel != null) {
                            VersionMath.normalizeVersion(release.tagName).ifBlank { release.tagName }
                        } else {
                            release.tagName
                        }
                    } ?: stringResource(Res.string.no_version_selected),
                    role = KomiTextRole.Stamp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    overflow = TextOverflow.Clip,
                    maxLines = 1,
                    uppercase = false,
                )
                (selectedAppLabel ?: selectedRelease?.name)?.let { name ->
                    if (name != selectedRelease?.tagName) {
                        KomiText(
                            text = name,
                            role = KomiTextRole.Body,
                            fontSize = 13.sp,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            KomiIcon(
                imageVector = Icons.Default.UnfoldMore,
                contentDescription = stringResource(Res.string.select_version),
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }

    if (isPickerVisible) {
        val lines = remember(filteredReleases, releaseLines) {
            filteredReleases.mapNotNull { releaseLines[it.id] }.distinct()
        }
        var lineFilter by remember(selectedRelease?.id, lines) {
            mutableStateOf(selectedRelease?.let { releaseLines[it.id] }?.takeIf { it in lines })
        }
        val visibleReleases = remember(filteredReleases, releaseLines, lineFilter) {
            val line = lineFilter
            if (line == null) filteredReleases else filteredReleases.filter { releaseLines[it.id] == line }
        }
        KomiSheet(
            onDismiss = { onAction(DetailsAction.ToggleVersionPicker) },
            placement = KomiSheetPlacement.Bottom,
        ) {
            KomiText(
                text = stringResource(Res.string.versions_title),
                role = KomiTextRole.Title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = colors.onSurface,
                modifier = Modifier.padding(vertical = 6.dp),
                uppercase = false,
            )
            Spacer(Modifier.size(8.dp))
            if (lines.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    item(key = "all") {
                        KomiChip(
                            label = stringResource(Res.string.category_all),
                            kind = KomiChipKind.Filter,
                            size = KomiChipSize.Sm,
                            selected = lineFilter == null,
                            onClick = { lineFilter = null },
                        )
                    }
                    items(lines, key = { it }) { line ->
                        KomiChip(
                            label = releaseLineLabel(line, repoName),
                            kind = KomiChipKind.Filter,
                            size = KomiChipSize.Sm,
                            selected = lineFilter == line,
                            onClick = { lineFilter = line },
                        )
                    }
                }
            }
            if (filteredReleases.isEmpty()) {
                KomiText(
                    text = stringResource(Res.string.not_available),
                    role = KomiTextRole.Body,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                val latestReleaseId = visibleReleases.firstOrNull()?.id
                val latestDeviceBuildId =
                    visibleReleases.firstOrNull { it.id in deviceBuildReleaseIds }?.id
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(items = visibleReleases, key = { it.id }) { release ->
                        VersionListItem(
                            release = release,
                            isSelected = release.id == selectedRelease?.id,
                            isLatest = release.id == latestReleaseId,
                            onClick = { onAction(DetailsAction.SelectRelease(release)) },
                            latestForPlatform = devicePlatform?.takeIf {
                                release.id == latestDeviceBuildId && latestDeviceBuildId != latestReleaseId
                            },
                            hasDeviceBuild =
                                deviceBuildReleaseIds.isEmpty() || release.id in deviceBuildReleaseIds,
                            platforms = releasePlatforms[release.id].orEmpty(),
                            devicePlatform = devicePlatform,
                        )
                        KomiHorizontalDivider(
                            color = colors.outlineVariant.copy(alpha = 0.5f),
                            thickness = 0.5.dp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VersionListItem(
    release: GithubRelease,
    isSelected: Boolean,
    isLatest: Boolean,
    onClick: () -> Unit,
    latestForPlatform: DiscoveryPlatform? = null,
    hasDeviceBuild: Boolean = true,
    platforms: Set<DiscoveryPlatform> = emptySet(),
    devicePlatform: DiscoveryPlatform? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                onClickLabel = stringResource(Res.string.select_version),
                onClick = onClick,
            )
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val colors = LocalPersonality.current.colors
        val shape = LocalPersonality.current.shape
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KomiText(
                    text = release.tagName,
                    role = KomiTextRole.Stamp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = when {
                        isSelected -> colors.primary
                        hasDeviceBuild -> colors.onSurface
                        else -> colors.onSurfaceVariant
                    },
                    uppercase = false,
                )
                if (isLatest) {
                    KomiText(
                        text = stringResource(Res.string.latest_badge),
                        role = KomiTextRole.Label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(shape.cornerSmall))
                            .border(
                                width = 1.dp,
                                color = colors.primary,
                                shape = RoundedCornerShape(shape.cornerSmall),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                latestForPlatform?.let { platform ->
                    VersionBadge(text = stringResource(Res.string.latest_for_platform, platform.toLabel()))
                }
                if (release.isEffectivelyPreRelease()) {
                    val specificLabel = release.preReleaseLabel()
                    KomiText(
                        text = specificLabel ?: stringResource(Res.string.pre_release_badge),
                        role = KomiTextRole.Label,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        color = colors.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(shape.cornerSmall))
                            .border(
                                width = 1.dp,
                                color = colors.primary,
                                shape = RoundedCornerShape(shape.cornerSmall),
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
            release.name?.let { name ->
                if (name != release.tagName) {
                    KomiText(
                        text = name,
                        role = KomiTextRole.Body,
                        fontSize = 13.sp,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            KomiText(
                text = if (hasDeviceBuild) {
                    formatIsoDateOrRaw(release.publishedAt)
                } else {
                    "${formatIsoDateOrRaw(release.publishedAt)} · ${stringResource(Res.string.no_build_for_this_device)}"
                },
                role = KomiTextRole.Label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant,
                uppercase = false,
            )
        }
        if (platforms.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            ReleasePlatformIcons(platforms = platforms, devicePlatform = devicePlatform)
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

@Composable
private fun VersionBadge(text: String) {
    val colors = LocalPersonality.current.colors
    val shape = RoundedCornerShape(LocalPersonality.current.shape.cornerSmall)
    KomiText(
        text = text,
        role = KomiTextRole.Label,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        color = colors.primary,
        uppercase = false,
        modifier = Modifier
            .clip(shape)
            .border(width = 1.dp, color = colors.primary, shape = shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun ReleasePlatformIcons(
    platforms: Set<DiscoveryPlatform>,
    devicePlatform: DiscoveryPlatform?,
) {
    val colors = LocalPersonality.current.colors
    val ordered = platforms.sortedWith(
        compareBy<DiscoveryPlatform> { it != devicePlatform }
            .thenBy { DiscoveryPlatform.selectablePlatforms.indexOf(it) },
    )
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ordered.forEach { platform ->
            platform.toIcon()?.let { icon ->
                KomiIcon(
                    imageVector = icon,
                    contentDescription = platform.toLabel(),
                    tint = if (platform == devicePlatform) colors.onSurface else colors.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
