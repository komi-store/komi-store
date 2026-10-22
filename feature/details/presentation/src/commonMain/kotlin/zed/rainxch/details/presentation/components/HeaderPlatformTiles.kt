package zed.rainxch.details.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.utils.PlatformRelease
import zed.rainxch.core.domain.utils.VersionMath
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow
import zed.rainxch.core.presentation.personality.model.PersonalityColors
import zed.rainxch.core.presentation.spacing.Spacing
import zed.rainxch.core.presentation.utils.daysSinceIso
import zed.rainxch.core.presentation.utils.toIcon
import zed.rainxch.core.presentation.utils.toLabel

// One tile per platform, each at most 96dp wide (the device's a little wider) so a couple of platforms
// don't stretch across the card. Classic joins them into one connected group; Manga inks each apart.
// With [playEntrance], the tiles rise in one after another.
@Composable
internal fun PlatformDock(
    platformReleases: List<PlatformRelease>,
    devicePlatform: DiscoveryPlatform?,
    onPlatformClick: ((DiscoveryPlatform) -> Unit)?,
    playEntrance: Boolean,
    modifier: Modifier = Modifier,
) {
    val manga = LocalPersonality.current is MangaPersonality
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(if (manga) Spacing.sm else CONNECTED_GAP),
    ) {
        platformReleases.forEachIndexed { index, (platform, platformRelease) ->
            val isDevice = platform == devicePlatform
            key(platform) {
                PlatformTile(
                    platform = platform,
                    release = platformRelease,
                    isDevice = isDevice,
                    first = index == 0,
                    last = index == platformReleases.lastIndex,
                    entranceDelayMillis = if (playEntrance) index * STAGGER_MILLIS else null,
                    onClick = onPlatformClick?.let { handler -> { handler(platform) } },
                    modifier =
                        Modifier
                            .weight(if (isDevice) DEVICE_TILE_WEIGHT else 1f, fill = false)
                            .widthIn(max = if (isDevice) 120.dp else 96.dp)
                            .fillMaxWidth(),
                )
            }
        }
    }
}

// Its icon over the version of its newest build, or the year when that build is over a year old.
// The device's tile is filled; in Manga every tile is inked. In Classic the ends of the group are
// round, the device's tile is a pill and a pressed tile squares its inner corners, as Material's
// connected button groups do.
@Composable
private fun PlatformTile(
    platform: DiscoveryPlatform,
    release: GithubRelease,
    isDevice: Boolean,
    first: Boolean,
    last: Boolean,
    entranceDelayMillis: Int?,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val manga = personality is MangaPersonality
    val stale = (daysSinceIso(release.publishedAt) ?: 0) > STALE_PLATFORM_DAYS
    val (container, content) = platformTileColors(colors, isDevice = isDevice, stale = stale)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val inner by animateDpAsState(
        targetValue = if (pressed) PRESSED_INNER_CORNER else INNER_CORNER,
        animationSpec = personality.motion.fastSpatial(),
        label = "platform-tile-corner",
    )
    val shape = tileShape(manga = manga, isDevice = isDevice, first = first, last = last, inner = inner)
    val entrance = remember { Animatable(if (entranceDelayMillis != null) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (entranceDelayMillis == null) return@LaunchedEffect
        delay(entranceDelayMillis.toLong())
        entrance.animateTo(1f, personality.motion.bouncySpatial())
    }
    val ink =
        if (manga) {
            Modifier.hardShadow(offset = DpOffset(2.dp, 2.dp), color = colors.shadow)
        } else {
            Modifier
        }
    Column(
        modifier =
            modifier
                .graphicsLayer {
                    alpha = entrance.value.coerceIn(0f, 1f)
                    translationY = (1f - entrance.value) * ENTRANCE_RISE.toPx()
                }.then(ink)
                .clip(shape)
                .background(container)
                .then(if (manga) Modifier.border(2.dp, colors.outline) else Modifier)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(interactionSource = interaction, indication = LocalIndication.current, onClick = onClick)
                    } else {
                        Modifier
                    },
                ).padding(vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        platform.toIcon()?.let { icon ->
            KomiIcon(
                imageVector = icon,
                contentDescription = platform.toLabel(),
                tint = content,
                modifier = Modifier.size(20.dp),
            )
        }
        KomiText(
            text = if (stale) release.publishedAt.take(4) else platformVersion(release),
            role = KomiTextRole.LabelSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun tileShape(
    manga: Boolean,
    isDevice: Boolean,
    first: Boolean,
    last: Boolean,
    inner: Dp,
): Shape =
    when {
        manga -> {
            RectangleShape
        }

        isDevice -> {
            RoundedCornerShape(PILL)
        }

        else -> {
            val start = if (first) PILL else inner
            val end = if (last) PILL else inner
            RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end)
        }
    }

private fun platformTileColors(
    colors: PersonalityColors,
    isDevice: Boolean,
    stale: Boolean,
): Pair<Color, Color> =
    when {
        isDevice -> colors.primary to colors.onPrimary
        stale -> colors.surface to colors.disabledContent
        else -> colors.surface to colors.onSurfaceVariant
    }

private fun platformVersion(release: GithubRelease): String = VersionMath.normalizeVersion(release.tagName).ifBlank { release.tagName }

private const val STALE_PLATFORM_DAYS = 365
private const val DEVICE_TILE_WEIGHT = 1.25f
private const val STAGGER_MILLIS = 50
private val CONNECTED_GAP = 2.dp
private val INNER_CORNER = 8.dp
private val PRESSED_INNER_CORNER = 4.dp
private val PILL = 999.dp
private val ENTRANCE_RISE = 12.dp
