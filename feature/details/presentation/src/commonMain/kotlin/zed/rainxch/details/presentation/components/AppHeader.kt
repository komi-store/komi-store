package zed.rainxch.details.presentation.components

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.PaddingValues
import kotlinx.coroutines.flow.first
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.History
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.account.github.GithubUserProfile
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.utils.PlatformRelease
import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.core.presentation.components.GitHubStoreImage
import zed.rainxch.core.presentation.components.InstalledAppIcon
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.chips.KomiChipSize
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.utils.daysSinceIso
import zed.rainxch.core.presentation.utils.formatReleasedAgo
import zed.rainxch.core.presentation.utils.toIcon
import zed.rainxch.core.presentation.utils.toLabel
import zed.rainxch.details.presentation.model.DownloadStage
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.details_last_push
import zed.rainxch.githubstore.core.presentation.res.forked_repository
import zed.rainxch.githubstore.core.presentation.res.installed
import zed.rainxch.githubstore.core.presentation.res.no_description
import zed.rainxch.githubstore.core.presentation.res.pending_install
import zed.rainxch.githubstore.core.presentation.res.self_owned_badge
import zed.rainxch.githubstore.core.presentation.res.update_available

private fun Color.normalizedForStripe(isDark: Boolean): Color {
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    val lum = (r + g + b) / 3
    val target = if (isDark) 170 else 140
    val minLum = if (isDark) 90 else 70
    if (lum >= minLum) return this
    val factor = target.toFloat() / lum.coerceAtLeast(1).toFloat()
    return Color(
        red = (r * factor).toInt().coerceIn(0, 255),
        green = (g * factor).toInt().coerceIn(0, 255),
        blue = (b * factor).toInt().coerceIn(0, 255),
        alpha = 255,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppHeader(
    author: GithubUserProfile?,
    repository: GithubRepoSummary,
    release: GithubRelease?,
    installedApp: InstalledApp?,
    modifier: Modifier = Modifier,
    installedApps: List<InstalledApp> = emptyList(),
    onSelectInstalledApp: (String) -> Unit = {},
    downloadStage: DownloadStage = DownloadStage.IDLE,
    downloadProgress: Int? = null,
    isCurrentUserOwner: Boolean = false,
    onPlatformClick: ((DiscoveryPlatform) -> Unit)? = null,
    platformReleases: List<PlatformRelease> = emptyList(),
    devicePlatform: DiscoveryPlatform? = null,
    appLabel: String? = null,
    onAppLabelClick: () -> Unit = {},
    onOwnerClick: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val colors = LocalPersonality.current.colors
    val surface = colors.surface
    val avatarUrl = author?.avatarUrl ?: repository.owner.avatarUrl
    val rawAccent = colors.primary
    val shape = LocalPersonality.current.shape
    val headerShape = RoundedCornerShape(shape.corner)
    val normalizedAccent = remember(rawAccent, isDark) { rawAccent.normalizedForStripe(isDark) }
    val tintFraction = if (isDark) 0.10f else 0.06f
    val animatedAccent by animateColorAsState(
        targetValue = normalizedAccent,
        animationSpec = tween(durationMillis = 1800, easing = LinearOutSlowInEasing),
        label = "details-hero-accent",
    )
    val animatedSurface by animateColorAsState(
        targetValue = lerp(surface, normalizedAccent, tintFraction),
        animationSpec = tween(durationMillis = 1800, easing = LinearOutSlowInEasing),
        label = "details-hero-surface",
    )
    val borderColor = colors.outline

    val animatedProgress by animateFloatAsState(
        targetValue = (downloadProgress ?: 0) / 100f,
        animationSpec = tween(durationMillis = 500),
        label = "avatar-progress",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(headerShape)
            .drawBehind { drawRect(animatedSurface) }
            .border(1.5.dp, borderColor, headerShape),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .clipToBounds()
                    .drawBehind {
                        val accent = animatedAccent
                        val stripeBase =
                            if (isDark) accent.copy(alpha = 0.18f) else accent.copy(alpha = 0.12f)
                        val stripeLineThick =
                            if (isDark) accent.copy(alpha = 0.45f) else accent.copy(alpha = 0.55f)
                        val stripeLineThin =
                            if (isDark) accent.copy(alpha = 0.22f) else accent.copy(alpha = 0.30f)
                        drawRect(color = stripeBase)
                        val thick = 9.dp.toPx()
                        val thin = 2.5.dp.toPx()
                        val gapAfterThick = 10.dp.toPx()
                        val gapBetweenThin = 6.dp.toPx()
                        val cycle =
                            thick + gapAfterThick + thin + gapBetweenThin + thin + gapAfterThick
                        var x = -size.height
                        while (x < size.width + size.height) {
                            drawLine(
                                color = stripeLineThick,
                                start = Offset(x, size.height),
                                end = Offset(x + size.height, 0f),
                                strokeWidth = thick,
                                cap = StrokeCap.Round,
                            )
                            var xt = x + thick + gapAfterThick
                            drawLine(
                                color = stripeLineThin,
                                start = Offset(xt, size.height),
                                end = Offset(xt + size.height, 0f),
                                strokeWidth = thin,
                                cap = StrokeCap.Round,
                            )
                            xt += thin + gapBetweenThin
                            drawLine(
                                color = stripeLineThin,
                                start = Offset(xt, size.height),
                                end = Offset(xt + size.height, 0f),
                                strokeWidth = thin,
                                cap = StrokeCap.Round,
                            )
                            x += cycle
                        }
                    },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 130.dp, end = 20.dp, top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KomiText(
                    text = author?.login ?: repository.owner.login,
                    role = KomiTextRole.Label,
                    fontSize = 13.sp,
                    color = colors.onSurface.copy(alpha = 0.75f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(onClick = onOwnerClick),
                    uppercase = false,
                )
                if (isCurrentUserOwner) {
                    KomiIcon(
                        imageVector = Icons.Filled.Verified,
                        contentDescription = stringResource(Res.string.self_owned_badge),
                        modifier = Modifier.size(16.dp),
                        tint = colors.primary,
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 130.dp, end = 20.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                androidx.compose.foundation.text.BasicText(
                    text = repository.name,
                    style = LocalPersonality.current.type.display.copy(
                        fontWeight = FontWeight.Black,
                        fontSize = 30.sp,
                        letterSpacing = (-0.4).sp,
                        color = colors.onSurface,
                    ),
                    maxLines = 1,
                    softWrap = false,
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                        minFontSize = 18.sp,
                        maxFontSize = 30.sp,
                        stepSize = 1.sp,
                    ),
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (repository.isFork) {
                    KomiChip(
                        label = stringResource(Res.string.forked_repository),
                        kind = KomiChipKind.Info,
                        size = KomiChipSize.Sm,
                        leadingIcon = Icons.AutoMirrored.Outlined.CallSplit,
                    )
                }
            }
            val lastPushIso = repository.pushedAt?.takeIf { it.isNotBlank() } ?: repository.updatedAt
            val lastPushLabel = formatReleasedAgo(lastPushIso)
            if (lastPushLabel != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    val statColor = colors.onSurface.copy(alpha = 0.88f)
                    KomiIcon(
                        imageVector = Icons.Outlined.History,
                        contentDescription = null,
                        tint = statColor,
                        modifier = Modifier.size(15.dp),
                    )
                    KomiText(
                        text = stringResource(Res.string.details_last_push, lastPushLabel),
                        role = KomiTextRole.Label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = statColor,
                        uppercase = false,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            KomiText(
                text = repository.description ?: stringResource(Res.string.no_description),
                role = KomiTextRole.Body,
                fontSize = 14.sp,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            val showAppSwitcher =
                installedApps.size > 1 || (installedApps.isNotEmpty() && installedApp == null)
            val showStatusPill =
                installedApp != null &&
                    (!showAppSwitcher || installedApp.isPendingInstall || installedApp.isUpdateAvailable)
            val showAppLabel = appLabel != null && installedApp == null
            if (showStatusPill || showAppSwitcher || showAppLabel) {
                Spacer(Modifier.height(12.dp))
                val switcherApps =
                    remember(installedApps, showAppSwitcher) {
                        if (showAppSwitcher) installedApps.sortedBy { it.appName.lowercase() } else emptyList()
                    }
                val firstChipIndex = (if (showStatusPill) 1 else 0) + (if (showAppLabel) 1 else 0)
                val selectedIndex =
                    switcherApps.indexOfFirst { it.packageName == installedApp?.packageName }
                val rowState = rememberLazyListState()
                val targetIndex = when {
                    showAppLabel -> 0
                    selectedIndex >= 0 -> firstChipIndex + selectedIndex
                    else -> -1
                }
                LaunchedEffect(targetIndex) {
                    if (targetIndex < 0) return@LaunchedEffect
                    val target = targetIndex
                    val layout = snapshotFlow { rowState.layoutInfo }
                        .first { it.visibleItemsInfo.isNotEmpty() }
                    val item = layout.visibleItemsInfo.firstOrNull { it.index == target }
                    val fullyVisible =
                        item != null &&
                            item.offset >= layout.viewportStartOffset &&
                            item.offset + item.size <= layout.viewportEndOffset
                    if (!fullyVisible) rowState.animateScrollToItem(target)
                }
                LazyRow(
                    state = rowState,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (showStatusPill && installedApp != null) {
                        item(key = "status") { InstalledStatusPill(installedApp = installedApp) }
                    }
                    if (showAppLabel && appLabel != null) {
                        item(key = "app") {
                            KomiChip(
                                label = appLabel,
                                kind = KomiChipKind.Filter,
                                size = KomiChipSize.Sm,
                                selected = true,
                                leadingIcon = Icons.Default.UnfoldMore,
                                onClick = onAppLabelClick,
                            )
                        }
                    }
                    items(switcherApps, key = { it.packageName }) { app ->
                        KomiChip(
                            label = app.appName,
                            kind = KomiChipKind.Filter,
                            size = KomiChipSize.Sm,
                            selected = app.packageName == installedApp?.packageName,
                            leadingContent = {
                                InstalledAppIcon(
                                    packageName = app.packageName,
                                    appName = app.appName,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(RoundedCornerShape(shape.cornerSmall)),
                                )
                            },
                            onClick = { onSelectInstalledApp(app.packageName) },
                        )
                    }
                }
            }
            if (platformReleases.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 20.dp),
                ) {
                    platformReleases.forEach { (platform, platformRelease) ->
                        val isDevice = platform == devicePlatform
                        KomiChip(
                            label = platformChipLabel(platform.toLabel(), platformRelease),
                            kind = KomiChipKind.Info,
                            size = KomiChipSize.Sm,
                            leadingContent = {
                                platform.toIcon()?.let { icon ->
                                    KomiIcon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isDevice) colors.onSurface else colors.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = onPlatformClick?.let { handler ->
                                {
                                    handler(platform)
                                }
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
        HeaderAvatar(
            avatarUrl = avatarUrl,
            cornerShape = RoundedCornerShape(shape.cornerSmall),
            isDark = isDark,
            downloadStage = downloadStage,
            accent = { animatedAccent },
            progress = { animatedProgress },
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 20.dp, y = 30.dp)
                .size(100.dp),
        )
    }
}

@Composable
private fun HeaderAvatar(
    avatarUrl: String?,
    cornerShape: RoundedCornerShape,
    isDark: Boolean,
    downloadStage: DownloadStage,
    accent: () -> Color,
    progress: () -> Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(cornerShape)
                .drawBehind {
                    val a = accent()
                    drawRect(if (isDark) a.copy(alpha = 0.20f) else a.copy(alpha = 0.14f))
                }
                .border(2.5.dp, accent(), cornerShape),
            contentAlignment = Alignment.Center,
        ) {
            GitHubStoreImage(
                imageModel = { avatarUrl },
                modifier = Modifier.size(92.dp).clip(cornerShape),
            )
        }

        when (downloadStage) {
            DownloadStage.DOWNLOADING -> {
                KomiCircularProgress(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = accent().copy(alpha = 0.2f),
                )
                KomiCircularProgress(
                    progress = progress,
                    modifier = Modifier.fillMaxSize(),
                    color = accent(),
                )
            }

            DownloadStage.VERIFYING, DownloadStage.INSTALLING -> {
                KomiCircularProgress(
                    modifier = Modifier.fillMaxSize(),
                    color = accent(),
                )
            }

            DownloadStage.IDLE -> { }
        }
    }
}

@Composable
private fun InstalledStatusPill(installedApp: InstalledApp) {
    val colors = LocalPersonality.current.colors
    val shape = RoundedCornerShape(LocalPersonality.current.shape.cornerSmall)
    Row(
        modifier = Modifier
            .clip(shape)
            .border(width = 1.dp, color = colors.primary, shape = shape)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        KomiText(
            text = stringResource(
                when {
                    installedApp.isPendingInstall -> Res.string.pending_install
                    installedApp.isUpdateAvailable -> Res.string.update_available
                    else -> Res.string.installed
                },
            ),
            role = KomiTextRole.Label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = colors.primary,
            uppercase = false,
        )
    }
}

private fun platformChipLabel(platformLabel: String, release: GithubRelease): String {
    val version = VersionMath.normalizeVersion(release.tagName).ifBlank { release.tagName }
    val days = daysSinceIso(release.publishedAt)
    val year = release.publishedAt.take(4)
    return if (days != null && days > STALE_PLATFORM_DAYS) {
        "$platformLabel $version · $year"
    } else {
        "$platformLabel $version"
    }
}

private const val STALE_PLATFORM_DAYS = 365
