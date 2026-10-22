package zed.rainxch.details.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Upgrade
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.account.github.GithubUserProfile
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.isReallyInstalled
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.utils.PlatformRelease
import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.core.presentation.components.GitHubStoreImage
import zed.rainxch.core.presentation.components.InstalledAppIcon
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.chips.KomiChipSize
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiTooltip
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.surfaces.KomiScreentone
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow
import zed.rainxch.core.presentation.spacing.Spacing
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
import kotlin.math.roundToInt

// Classic sets the header on the page itself; Manga inks it into a panel with the install status
// as a sticker on its corner. Both share the body below.
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
    val personality = LocalPersonality.current
    val manga = personality is MangaPersonality
    val showAppSwitcher = installedApps.size > 1 || (installedApps.isNotEmpty() && installedApp == null)
    val status =
        installedApp
            ?.takeIf { !showAppSwitcher || it.isPendingInstall || it.isUpdateAvailable }
            ?.let(::headerStatusOf)
    val switcherApps =
        remember(installedApps, showAppSwitcher) {
            if (showAppSwitcher) installedApps.sortedBy { it.appName.lowercase() } else emptyList()
        }
    val progress by animateFloatAsState(
        targetValue = (downloadProgress ?: 0) / 100f,
        animationSpec = personality.motion.defaultEffects(),
        label = "header-progress",
    )
    val body: @Composable () -> Unit = {
        HeaderBody(
            owner = author?.login ?: repository.owner.login,
            avatarUrl = author?.avatarUrl ?: repository.owner.avatarUrl,
            repository = repository,
            installedApp = installedApp,
            downloadStage = downloadStage,
            progress = { progress },
            isCurrentUserOwner = isCurrentUserOwner,
            onOwnerClick = onOwnerClick,
        ) {
            HeaderChips(
                status = status.takeIf { !manga },
                installedApp = installedApp,
                switcherApps = switcherApps,
                appLabel = appLabel?.takeIf { installedApp == null },
                onAppLabelClick = onAppLabelClick,
                onSelectInstalledApp = onSelectInstalledApp,
                isFork = repository.isFork,
                platformReleases = platformReleases,
                devicePlatform = devicePlatform,
                onPlatformClick = onPlatformClick,
            )
        }
    }
    if (manga) {
        Box(modifier = modifier.fillMaxWidth().padding(top = Spacing.md, end = Spacing.sm)) {
            KomiSurface(
                modifier = Modifier.fillMaxWidth(),
                screentone = KomiScreentone.Corner,
                contentPadding = PaddingValues(Spacing.lg),
                content = body,
            )
            if (status != null) {
                StatusSticker(
                    status = status,
                    modifier = Modifier.align(Alignment.TopEnd).offset(x = Spacing.sm, y = -Spacing.md),
                )
            }
        }
    } else {
        Box(modifier = modifier.fillMaxWidth().padding(vertical = Spacing.sm)) { body() }
    }
}

@Composable
private fun HeaderBody(
    owner: String,
    avatarUrl: String?,
    repository: GithubRepoSummary,
    installedApp: InstalledApp?,
    downloadStage: DownloadStage,
    progress: () -> Float,
    isCurrentUserOwner: Boolean,
    onOwnerClick: () -> Unit,
    chips: @Composable () -> Unit,
) {
    val personality = LocalPersonality.current
    val manga = personality is MangaPersonality
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            HeaderPicture(
                installedApp = installedApp,
                avatarUrl = avatarUrl,
                downloadStage = downloadStage,
                progress = progress,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                if (manga) OwnerLine(owner, isCurrentUserOwner, onOwnerClick)
                RepoTitle(name = repository.name)
                if (!manga) OwnerLine(owner, isCurrentUserOwner, onOwnerClick)
                LastPush(repository = repository)
            }
        }
        KomiText(
            text = repository.description ?: stringResource(Res.string.no_description),
            role = KomiTextRole.Body,
            color = personality.colors.onSurfaceVariant,
        )
        chips()
    }
}

// The installed app's own icon when it is on the device, else the owner's avatar. While a
// download runs, the picture shrinks into the progress ring; in Classic it also rounds to a circle.
@Composable
private fun HeaderPicture(
    installedApp: InstalledApp?,
    avatarUrl: String?,
    downloadStage: DownloadStage,
    progress: () -> Float,
) {
    val personality = LocalPersonality.current
    val manga = personality is MangaPersonality
    val colors = personality.colors
    val busy = downloadStage != DownloadStage.IDLE && downloadStage != DownloadStage.PAUSED
    val scale by animateFloatAsState(
        targetValue = if (busy) BUSY_PICTURE_SCALE else 1f,
        animationSpec = personality.motion.bouncySpatial(),
        label = "header-picture-scale",
    )
    val cornerPercent by animateFloatAsState(
        targetValue = if (busy) CIRCLE_CORNER_PERCENT else REST_CORNER_PERCENT,
        animationSpec = personality.motion.defaultSpatial(),
        label = "header-picture-corner",
    )
    val shape = if (manga) RectangleShape else RoundedCornerShape(percent = cornerPercent.roundToInt())
    val app = installedApp?.takeIf { it.isReallyInstalled() }
    Box(
        modifier = Modifier.size(if (manga) 84.dp else 80.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        rotationZ = if (manga) MANGA_PICTURE_TILT else 0f
                    }.then(
                        if (manga) {
                            Modifier
                                .hardShadow(offset = DpOffset(4.dp, 4.dp), color = colors.shadow)
                                .border(3.dp, colors.outline)
                        } else {
                            Modifier
                        },
                    ).clip(shape)
                    .background(colors.surfaceContainerHigh),
        ) {
            if (app != null) {
                InstalledAppIcon(
                    packageName = app.packageName,
                    appName = app.appName,
                    modifier = Modifier.fillMaxSize(),
                    avatarFallbackUrl = avatarUrl,
                )
            } else {
                GitHubStoreImage(imageModel = { avatarUrl }, modifier = Modifier.fillMaxSize())
            }
        }
        when (downloadStage) {
            DownloadStage.DOWNLOADING -> {
                KomiCircularProgress(progress = { 1f }, modifier = Modifier.fillMaxSize(), color = colors.primaryTint)
                KomiCircularProgress(progress = progress, modifier = Modifier.fillMaxSize())
            }

            DownloadStage.VERIFYING, DownloadStage.INSTALLING -> {
                KomiCircularProgress(modifier = Modifier.fillMaxSize())
            }

            DownloadStage.IDLE, DownloadStage.PAUSED -> {}
        }
    }
}

@Composable
private fun OwnerLine(
    owner: String,
    isCurrentUserOwner: Boolean,
    onClick: () -> Unit,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(personality.shape.cornerSmall))
                .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        KomiText(
            text = owner,
            role = if (personality is MangaPersonality) KomiTextRole.Overline else KomiTextRole.Label,
            color = colors.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (isCurrentUserOwner) {
            val selfOwned = stringResource(Res.string.self_owned_badge)
            KomiTooltip(text = selfOwned) {
                KomiIcon(
                    imageVector = Icons.Filled.Verified,
                    contentDescription = selfOwned,
                    modifier = Modifier.size(16.dp),
                    tint = colors.primary,
                )
            }
        }
    }
}

@Composable
private fun RepoTitle(name: String) {
    val personality = LocalPersonality.current
    val type = personality.type
    // BasicText keeps the repository's own case; KomiText would capitalize a display role in Manga.
    BasicText(
        text = name,
        style = type.display.copy(color = personality.colors.onSurface),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = type.sectionTitle.fontSize, maxFontSize = type.display.fontSize),
    )
}

@Composable
private fun LastPush(repository: GithubRepoSummary) {
    val label = formatReleasedAgo(repository.pushedAt?.takeIf { it.isNotBlank() } ?: repository.updatedAt) ?: return
    val colors = LocalPersonality.current.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        KomiIcon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        KomiText(
            text = stringResource(Res.string.details_last_push, label),
            role = KomiTextRole.Caption,
            color = colors.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderChips(
    status: HeaderStatus?,
    installedApp: InstalledApp?,
    switcherApps: List<InstalledApp>,
    appLabel: String?,
    onAppLabelClick: () -> Unit,
    onSelectInstalledApp: (String) -> Unit,
    isFork: Boolean,
    platformReleases: List<PlatformRelease>,
    devicePlatform: DiscoveryPlatform?,
    onPlatformClick: ((DiscoveryPlatform) -> Unit)?,
) {
    // Classic's Material chips already pad themselves to a 48dp touch target, so their rows need no gap.
    val rowGap = if (LocalPersonality.current is MangaPersonality) Spacing.sm else 0.dp
    // Without selectors to scroll through, the status joins the platform chips instead of a row of its own.
    val selectors = appLabel != null || switcherApps.isNotEmpty()
    val inlineStatus = status.takeIf { !selectors }
    Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
        if (selectors) {
            AppChipRow(
                status = status,
                installedApp = installedApp,
                switcherApps = switcherApps,
                appLabel = appLabel,
                onAppLabelClick = onAppLabelClick,
                onSelectInstalledApp = onSelectInstalledApp,
            )
        }
        if (inlineStatus != null || isFork || platformReleases.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(rowGap),
            ) {
                if (inlineStatus != null) StatusChip(inlineStatus)
                if (isFork) {
                    KomiChip(
                        label = stringResource(Res.string.forked_repository),
                        kind = KomiChipKind.Tonal,
                        size = KomiChipSize.Sm,
                        leadingIcon = Icons.AutoMirrored.Outlined.CallSplit,
                    )
                }
                platformReleases.forEach { (platform, platformRelease) ->
                    PlatformChip(
                        platform = platform,
                        release = platformRelease,
                        isDevice = platform == devicePlatform,
                        onClick = onPlatformClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlatformChip(
    platform: DiscoveryPlatform,
    release: GithubRelease,
    isDevice: Boolean,
    onClick: ((DiscoveryPlatform) -> Unit)?,
) {
    val colors = LocalPersonality.current.colors
    KomiChip(
        label = platformChipLabel(platform.toLabel(), release),
        kind = KomiChipKind.Tonal,
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
        onClick = onClick?.let { handler -> { handler(platform) } },
    )
}

// The status, the monorepo app label and one chip per installed app; the selected chip scrolls into view.
@Composable
private fun AppChipRow(
    status: HeaderStatus?,
    installedApp: InstalledApp?,
    switcherApps: List<InstalledApp>,
    appLabel: String?,
    onAppLabelClick: () -> Unit,
    onSelectInstalledApp: (String) -> Unit,
) {
    val shape = LocalPersonality.current.shape
    val firstChipIndex = (if (status != null) 1 else 0) + (if (appLabel != null) 1 else 0)
    val selectedIndex = switcherApps.indexOfFirst { it.packageName == installedApp?.packageName }
    val targetIndex =
        when {
            appLabel != null -> 0
            selectedIndex >= 0 -> firstChipIndex + selectedIndex
            else -> -1
        }
    val rowState = rememberLazyListState()
    LaunchedEffect(targetIndex) {
        if (targetIndex < 0) return@LaunchedEffect
        val layout = snapshotFlow { rowState.layoutInfo }.first { it.visibleItemsInfo.isNotEmpty() }
        val item = layout.visibleItemsInfo.firstOrNull { it.index == targetIndex }
        val fullyVisible =
            item != null &&
                item.offset >= layout.viewportStartOffset &&
                item.offset + item.size <= layout.viewportEndOffset
        if (!fullyVisible) rowState.animateScrollToItem(targetIndex)
    }
    LazyRow(
        state = rowState,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (status != null) {
            item(key = "status") { StatusChip(status) }
        }
        if (appLabel != null) {
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
                        modifier = Modifier.size(16.dp).clip(RoundedCornerShape(shape.cornerSmall)),
                    )
                },
                onClick = { onSelectInstalledApp(app.packageName) },
            )
        }
    }
}

@Composable
private fun StatusChip(status: HeaderStatus) {
    KomiChip(
        label = stringResource(status.label),
        kind = KomiChipKind.Tonal,
        size = KomiChipSize.Sm,
        selected = status.emphasized,
        leadingIcon = status.icon,
    )
}

@Composable
private fun StatusSticker(
    status: HeaderStatus,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    KomiText(
        text = stringResource(status.label),
        role = KomiTextRole.Stamp,
        color = if (status.emphasized) colors.onPrimary else colors.onSurface,
        modifier =
            modifier
                .rotate(STICKER_TILT)
                .hardShadow(offset = DpOffset(3.dp, 3.dp), color = colors.shadow)
                .background(if (status.emphasized) colors.primary else colors.surface)
                .border(3.dp, colors.outline)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs),
    )
}

private enum class HeaderStatus(
    val label: StringResource,
    val icon: ImageVector,
    val emphasized: Boolean,
) {
    Pending(Res.string.pending_install, Icons.Rounded.InstallMobile, true),
    Update(Res.string.update_available, Icons.Rounded.Upgrade, true),
    Installed(Res.string.installed, Icons.Rounded.CheckCircle, false),
}

private fun headerStatusOf(app: InstalledApp): HeaderStatus =
    when {
        app.isPendingInstall -> HeaderStatus.Pending
        app.isUpdateAvailable -> HeaderStatus.Update
        else -> HeaderStatus.Installed
    }

private fun platformChipLabel(
    platformLabel: String,
    release: GithubRelease,
): String {
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
private const val BUSY_PICTURE_SCALE = 0.72f
private const val REST_CORNER_PERCENT = 30f
private const val CIRCLE_CORNER_PERCENT = 50f
private const val MANGA_PICTURE_TILT = -3f
private const val STICKER_TILT = 6f
