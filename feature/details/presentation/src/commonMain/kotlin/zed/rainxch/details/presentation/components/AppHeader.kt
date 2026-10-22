package zed.rainxch.details.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import kotlinx.coroutines.flow.first
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.account.github.GithubUserProfile
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.isReallyInstalled
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.utils.PlatformRelease
import zed.rainxch.core.presentation.components.GitHubStoreImage
import zed.rainxch.core.presentation.components.InstalledAppIcon
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.chips.KomiChipSize
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiTooltip
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.shapes.MorphShape
import zed.rainxch.core.presentation.components.surfaces.KomiScreentone
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfacePaper
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow
import zed.rainxch.core.presentation.spacing.Spacing
import zed.rainxch.core.presentation.utils.formatReleasedAgo
import zed.rainxch.details.presentation.model.DownloadStage
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.details_last_push
import zed.rainxch.githubstore.core.presentation.res.forked_repository
import zed.rainxch.githubstore.core.presentation.res.no_description
import zed.rainxch.githubstore.core.presentation.res.self_owned_badge

// Classic sets the header on a tinted card (the page takes its colors from the app's icon, see
// DetailsRoot) and badges the picture with the install status; Manga inks it into a panel with the
// status as a sticker on its corner. Both share the body below.
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
    val status = headerStatusOf(installedApp, showAppSwitcher)
    val switcherApps =
        remember(installedApps, showAppSwitcher) {
            if (showAppSwitcher) installedApps.sortedBy { it.appName.lowercase() } else emptyList()
        }
    val progress by animateFloatAsState(
        targetValue = (downloadProgress ?: 0) / 100f,
        animationSpec = personality.motion.defaultEffects(),
        label = "header-progress",
    )
    val avatarUrl = author?.avatarUrl ?: repository.owner.avatarUrl
    val deviceApp = installedApp?.takeIf { it.isReallyInstalled() }
    // Plays once per visit, not each time the header scrolls back into view.
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val playEntrance = !entered && !manga && personality.motion.animates
    val body: @Composable () -> Unit = {
        HeaderBody(
            owner = author?.login ?: repository.owner.login,
            avatarUrl = avatarUrl,
            repository = repository,
            deviceApp = deviceApp,
            status = status.takeIf { !manga },
            downloadStage = downloadStage,
            progress = { progress },
            playEntrance = playEntrance,
            isCurrentUserOwner = isCurrentUserOwner,
            onOwnerClick = onOwnerClick,
        ) {
            HeaderChips(
                installedApp = installedApp,
                switcherApps = switcherApps,
                appLabel = appLabel?.takeIf { installedApp == null },
                onAppLabelClick = onAppLabelClick,
                onSelectInstalledApp = onSelectInstalledApp,
                isFork = repository.isFork,
                platformReleases = platformReleases,
                devicePlatform = devicePlatform,
                onPlatformClick = onPlatformClick,
                playEntrance = playEntrance,
            )
        }
    }
    // Manga's sticker hangs off the panel's corner, so the panel leaves room for it.
    val stickerRoom = if (manga) Modifier.padding(top = Spacing.md, end = Spacing.sm) else Modifier
    Box(modifier = modifier.fillMaxWidth().then(stickerRoom)) {
        KomiSurface(
            modifier = Modifier.fillMaxWidth(),
            paper = KomiSurfacePaper.Tinted,
            screentone = KomiScreentone.Corner,
            contentPadding = PaddingValues(Spacing.lg),
            content = body,
        )
        if (manga && status != null) {
            StatusSticker(
                status = status,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = Spacing.sm, y = -Spacing.md),
            )
        }
    }
}

@Composable
private fun HeaderBody(
    owner: String,
    avatarUrl: String?,
    repository: GithubRepoSummary,
    deviceApp: InstalledApp?,
    status: HeaderStatus?,
    downloadStage: DownloadStage,
    progress: () -> Float,
    playEntrance: Boolean,
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
                deviceApp = deviceApp,
                avatarUrl = avatarUrl,
                status = status,
                downloadStage = downloadStage,
                progress = progress,
                playEntrance = playEntrance,
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

// The installed app's own icon when it is on the device, else the owner's avatar. Classic sets the
// icon on a cookie-shaped plate (the avatar is cut to the cookie), pops it in and badges its corner
// with [status]; Manga squares and tilts it. While a download runs, the picture shrinks into the
// progress ring, and in Classic the cookie morphs into a circle.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeaderPicture(
    deviceApp: InstalledApp?,
    avatarUrl: String?,
    status: HeaderStatus?,
    downloadStage: DownloadStage,
    progress: () -> Float,
    playEntrance: Boolean,
) {
    val personality = LocalPersonality.current
    val manga = personality is MangaPersonality
    val colors = personality.colors
    val motion = personality.motion
    val busy = downloadStage != DownloadStage.IDLE && downloadStage != DownloadStage.PAUSED
    val scale by animateFloatAsState(
        targetValue = if (busy) BUSY_PICTURE_SCALE else 1f,
        animationSpec = motion.bouncySpatial(),
        label = "header-picture-scale",
    )
    val round by animateFloatAsState(
        targetValue = if (busy) 1f else 0f,
        animationSpec = motion.defaultSpatial(),
        label = "header-picture-round",
    )
    val pop = remember { Animatable(if (playEntrance) POP_START_SCALE else 1f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, motion.bouncySpatial()) }
    val cookie = remember { Morph(MaterialShapes.Cookie4Sided, MaterialShapes.Circle) }
    val shape = remember(manga, round) { if (manga) RectangleShape else MorphShape(cookie, round) }
    Box(
        modifier =
            Modifier
                .size(if (manga) 84.dp else 80.dp)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                },
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
                    .background(if (manga || deviceApp == null) colors.surfaceContainerHigh else colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            PictureImage(deviceApp = deviceApp, avatarUrl = avatarUrl, plated = !manga)
        }
        DownloadRing(downloadStage = downloadStage, progress = progress)
        if (status != null) {
            AnimatedVisibility(
                visible = !busy,
                modifier = Modifier.align(Alignment.BottomEnd).offset(x = Spacing.xs, y = Spacing.xs),
                enter = scaleIn(motion.bouncySpatial()),
                exit = scaleOut(motion.fastSpatial()),
            ) {
                StatusBadge(status)
            }
        }
    }
}

// An installed app's icon carries its own shape, so on a plate it sits inset rather than cut.
@Composable
private fun PictureImage(
    deviceApp: InstalledApp?,
    avatarUrl: String?,
    plated: Boolean,
) {
    if (deviceApp != null) {
        InstalledAppIcon(
            packageName = deviceApp.packageName,
            appName = deviceApp.appName,
            modifier = Modifier.fillMaxSize(if (plated) PLATED_ICON_FRACTION else 1f),
            avatarFallbackUrl = avatarUrl,
        )
    } else {
        GitHubStoreImage(imageModel = { avatarUrl }, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun DownloadRing(
    downloadStage: DownloadStage,
    progress: () -> Float,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (downloadStage) {
            DownloadStage.DOWNLOADING -> {
                KomiCircularProgress(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = LocalPersonality.current.colors.primaryTint,
                )
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
        style = type.displayEmphasized.copy(color = personality.colors.onSurface),
        maxLines = 1,
        softWrap = false,
        autoSize = TextAutoSize.StepBased(minFontSize = type.sectionTitle.fontSize, maxFontSize = type.displayEmphasized.fontSize),
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

@Composable
private fun HeaderChips(
    installedApp: InstalledApp?,
    switcherApps: List<InstalledApp>,
    appLabel: String?,
    onAppLabelClick: () -> Unit,
    onSelectInstalledApp: (String) -> Unit,
    isFork: Boolean,
    platformReleases: List<PlatformRelease>,
    devicePlatform: DiscoveryPlatform?,
    onPlatformClick: ((DiscoveryPlatform) -> Unit)?,
    playEntrance: Boolean,
) {
    // Classic's Material chips already pad themselves to a 48dp touch target, so their rows need no gap.
    val rowGap = if (LocalPersonality.current is MangaPersonality) Spacing.sm else 0.dp
    Column(verticalArrangement = Arrangement.spacedBy(rowGap)) {
        if (appLabel != null || switcherApps.isNotEmpty()) {
            AppChipRow(
                installedApp = installedApp,
                switcherApps = switcherApps,
                appLabel = appLabel,
                onAppLabelClick = onAppLabelClick,
                onSelectInstalledApp = onSelectInstalledApp,
            )
        }
        if (isFork) {
            KomiChip(
                label = stringResource(Res.string.forked_repository),
                kind = KomiChipKind.Tonal,
                size = KomiChipSize.Sm,
                leadingIcon = Icons.AutoMirrored.Outlined.CallSplit,
            )
        }
        if (platformReleases.isNotEmpty()) {
            PlatformDock(
                platformReleases = platformReleases,
                devicePlatform = devicePlatform,
                onPlatformClick = onPlatformClick,
                playEntrance = playEntrance,
            )
        }
    }
}

// The monorepo app label and one chip per installed app; the selected chip scrolls into view.
@Composable
private fun AppChipRow(
    installedApp: InstalledApp?,
    switcherApps: List<InstalledApp>,
    appLabel: String?,
    onAppLabelClick: () -> Unit,
    onSelectInstalledApp: (String) -> Unit,
) {
    val personality = LocalPersonality.current
    val shape = personality.shape
    // A selected Classic filter chip takes the tinted card's own color, so Classic uses tonal ones here.
    val chipKind = if (personality is MangaPersonality) KomiChipKind.Filter else KomiChipKind.Tonal
    val firstChipIndex = if (appLabel != null) 1 else 0
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
        if (appLabel != null) {
            item(key = "app") {
                KomiChip(
                    label = appLabel,
                    kind = chipKind,
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
                kind = chipKind,
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

private const val BUSY_PICTURE_SCALE = 0.72f
private const val POP_START_SCALE = 0.6f
private const val PLATED_ICON_FRACTION = 0.76f
private const val MANGA_PICTURE_TILT = -3f
