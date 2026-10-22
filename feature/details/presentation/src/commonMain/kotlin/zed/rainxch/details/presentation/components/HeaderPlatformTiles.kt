package zed.rainxch.details.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
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

// One equal tile per platform, each at most 96dp wide so a couple of platforms don't stretch across the card.
@Composable
internal fun PlatformDock(
    platformReleases: List<PlatformRelease>,
    devicePlatform: DiscoveryPlatform?,
    onPlatformClick: ((DiscoveryPlatform) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        platformReleases.forEach { (platform, platformRelease) ->
            PlatformTile(
                platform = platform,
                release = platformRelease,
                isDevice = platform == devicePlatform,
                onClick = onPlatformClick?.let { handler -> { handler(platform) } },
                modifier = Modifier.weight(1f, fill = false).widthIn(max = 96.dp).fillMaxWidth(),
            )
        }
    }
}

// One tile per platform: its icon over the version of its newest build, or the year when that build
// is over a year old. The device's tile is filled; in Manga every tile is inked.
@Composable
private fun PlatformTile(
    platform: DiscoveryPlatform,
    release: GithubRelease,
    isDevice: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val personality = LocalPersonality.current
    val colors = personality.colors
    val manga = personality is MangaPersonality
    val stale = (daysSinceIso(release.publishedAt) ?: 0) > STALE_PLATFORM_DAYS
    val (container, content) = platformTileColors(colors, manga = manga, isDevice = isDevice, stale = stale)
    val shape = if (manga) RectangleShape else RoundedCornerShape(personality.shape.cornerSmall)
    val ink =
        if (manga) {
            Modifier.hardShadow(offset = DpOffset(2.dp, 2.dp), color = colors.shadow)
        } else {
            Modifier
        }
    Column(
        modifier =
            modifier
                .then(ink)
                .clip(shape)
                .background(container)
                .then(if (manga) Modifier.border(2.dp, colors.outline) else Modifier)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(vertical = Spacing.sm),
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

private fun platformTileColors(
    colors: PersonalityColors,
    manga: Boolean,
    isDevice: Boolean,
    stale: Boolean,
): Pair<Color, Color> =
    when {
        isDevice && manga -> colors.primary to colors.onPrimary
        isDevice -> colors.primaryContainer to colors.onPrimaryContainer
        stale -> (if (manga) colors.surface else colors.surfaceContainerHigh) to colors.disabledContent
        else -> (if (manga) colors.surface else colors.surfaceContainerHigh) to colors.onSurfaceVariant
    }

private fun platformVersion(release: GithubRelease): String =
    VersionMath.normalizeVersion(release.tagName).ifBlank { release.tagName }

private const val STALE_PLATFORM_DAYS = 365
