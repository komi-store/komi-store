package zed.rainxch.core.presentation.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.components.GitHubStoreImage
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.chips.KomiChipSize
import zed.rainxch.core.presentation.components.reviews.CompactRating
import zed.rainxch.core.presentation.components.surfaces.KomiScreentone
import zed.rainxch.core.presentation.components.surfaces.KomiSurface
import zed.rainxch.core.presentation.components.surfaces.KomiSurfaceElevation
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.manga.MangaAccent
import zed.rainxch.core.presentation.personality.manga.MangaPaper
import zed.rainxch.core.presentation.personality.manga.decoration.StarburstShape
import zed.rainxch.core.presentation.personality.manga.decoration.halftoneOverlay
import zed.rainxch.core.presentation.personality.manga.decoration.screentoneFill
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.model.PersonalityColors
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.spacing.Spacing
import zed.rainxch.core.presentation.utils.formatReleasedAgo
import zed.rainxch.core.presentation.utils.toIcon
import zed.rainxch.core.presentation.utils.toLabel
import kotlin.math.roundToInt

@Composable
fun KomiRepoCard(
    name: String,
    owner: String,
    language: String,
    description: String,
    platforms: ImmutableList<DiscoveryPlatform>,
    stars: Int,
    downloads: Int,
    releasedAgoDays: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: (() -> Unit)? = null,
    monogram: String? = null,
    imageUrl: String? = null,
    feed: KomiRepoCardFeed = KomiRepoCardFeed.Plain,
    rank: Int = 1,
    version: String? = null,
    dailyStars: Int = 0,
    compact: Boolean = false,
    index: Int = 0,
    tilt: Boolean = false,
    releasedAt: String? = null,
    ratingAverage: Double? = null,
    ratingCount: Int = 0,
) {
    val colors = LocalPersonality.current.colors
    val tiltDeg =
        if (tilt) {
            if (index % 2 != 0) 0.4f else -0.4f
        } else {
            0f
        }
    val gap = if (compact) 9.dp else 11.dp
    val badge =
        CardBadgeInfo(
            feed = feed,
            rank = rank,
            dailyStars = dailyStars,
            version = version,
            releasedAgoDays = releasedAgoDays,
        )

    KomiSurface(
        modifier = modifier.fillMaxWidth(),
        elevation = KomiSurfaceElevation.Card,
        screentone = KomiScreentone.Corner,
        onClick = onOpen,
        onLongClick = onLongPress,
        tilt = tiltDeg,
        contentPadding = PaddingValues(if (compact) 13.dp else 15.dp),
    ) {
        Column {
            RepoCardHeader(
                name = name,
                owner = owner,
                language = language,
                monogram = monogram ?: name.take(2),
                imageUrl = imageUrl,
                compact = compact,
                ratingAverage = ratingAverage,
                ratingCount = ratingCount,
                badge = badge,
                colors = colors,
            )

            KomiText(
                text = description,
                role = KomiTextRole.Body,
                color = colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = gap),
            )

            RepoCardPlatforms(
                platforms = platforms,
                compact = compact,
                gap = gap,
                badge = badge,
                colors = colors,
            )

            HorizontalDivider(
                modifier = Modifier.padding(top = if (compact) 9.dp else 12.dp),
                thickness = 2.dp,
                color = colors.divider,
            )
            RepoCardFooter(
                stars = stars,
                downloads = downloads,
                releasedAt = releasedAt,
                compact = compact,
                colors = colors,
            )
        }
    }
}

@Composable
private fun RepoCardHeader(
    name: String,
    owner: String,
    language: String,
    monogram: String,
    imageUrl: String?,
    compact: Boolean,
    ratingAverage: Double?,
    ratingCount: Int,
    badge: CardBadgeInfo,
    colors: PersonalityColors,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CardIconTile(
            monogram = monogram,
            imageUrl = imageUrl,
            size = if (compact) 40.dp else 60.dp,
            colors = colors,
        )
        Column(modifier = Modifier.weight(1f)) {
            KomiText(
                text = name,
                role = KomiTextRole.CardTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.padding(top = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KomiText(
                    text = "@$owner",
                    role = KomiTextRole.Caption,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!compact) {
                    Diamond(color = colors.onSurfaceVariant)
                    KomiText(
                        text = language,
                        role = KomiTextRole.Caption,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            // The review rating sits apart from the stats row, whose star is GitHub stars.
            if (ratingAverage != null && ratingCount > 0) {
                CompactRating(
                    average = ratingAverage,
                    count = ratingCount,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (!compact) {
            CardBadge(badge = badge, colors = colors)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepoCardPlatforms(
    platforms: ImmutableList<DiscoveryPlatform>,
    compact: Boolean,
    gap: Dp,
    badge: CardBadgeInfo,
    colors: PersonalityColors,
) {
    if (compact && (platforms.isNotEmpty() || badge.feed != KomiRepoCardFeed.Plain)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = gap),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            platforms.forEach { platform ->
                platform.toIcon()?.let { icon ->
                    Icon(
                        imageVector = icon,
                        contentDescription = platform.toLabel(),
                        modifier = Modifier.size(16.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
            // A narrow grid card has no room for the chart badge beside the name.
            Spacer(Modifier.weight(1f))
            CardBadge(badge = badge, colors = colors)
        }
    } else if (platforms.isNotEmpty()) {
        FlowRow(
            modifier = Modifier.padding(top = gap),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            platforms.forEach { platform ->
                KomiChip(
                    label = platform.toLabel(),
                    kind = KomiChipKind.Info,
                    size = KomiChipSize.Md,
                    leadingContent = {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            platform.toIcon()?.let { icon ->
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = colors.onSurface,
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RepoCardFooter(
    stars: Int,
    downloads: Int,
    releasedAt: String?,
    compact: Boolean,
    colors: PersonalityColors,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = if (compact) 9.dp else 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 13.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Stat(
                icon = Icons.Filled.Star,
                value = fmtCompact(stars),
                colors = colors,
                hero = true,
            )
            Stat(
                icon = Icons.Outlined.Download,
                value = fmtCompact(downloads),
                colors = colors,
            )
            if (!compact) {
                formatReleasedAgo(releasedAt)?.let { ago ->
                    Stat(
                        icon = Icons.Outlined.Schedule,
                        value = ago,
                        colors = colors,
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!compact) {
                KomiText(
                    text = "Read",
                    role = KomiTextRole.LabelSmall,
                    color = colors.onSurface,
                    modifier = Modifier.padding(end = 2.dp),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = colors.onSurface,
            )
        }
    }
}

private const val AVATAR_SATURATION = 0.15f
private const val AVATAR_CONTRAST_DARK = 1.05f
private const val AVATAR_CONTRAST_LIGHT = 1.15f
private const val AVATAR_BRIGHTNESS_DARK = 0.95f
private const val AVATAR_BRIGHTNESS_LIGHT = 1.02f
private const val CONTRAST_PIVOT = 0.5f
private const val CHANNEL_MAX = 255f
private const val LUMA_RED = 0.299f
private const val LUMA_GREEN = 0.587f
private const val LUMA_BLUE = 0.114f

private fun inkAvatarFilter(dark: Boolean): ColorFilter {
    val saturation = AVATAR_SATURATION
    val contrast = if (dark) AVATAR_CONTRAST_DARK else AVATAR_CONTRAST_LIGHT
    val brightness = if (dark) AVATAR_BRIGHTNESS_DARK else AVATAR_BRIGHTNESS_LIGHT
    val keep = 1f - saturation
    val cb = contrast * brightness
    val t = (-CONTRAST_PIVOT * contrast + CONTRAST_PIVOT) * CHANNEL_MAX
    val lr = LUMA_RED
    val lg = LUMA_GREEN
    val lb = LUMA_BLUE
    return ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                cb * (lr * keep + saturation),
                cb * (lg * keep),
                cb * (lb * keep),
                0f,
                t,
                cb * (lr * keep),
                cb * (lg * keep + saturation),
                cb * (lb * keep),
                0f,
                t,
                cb * (lr * keep),
                cb * (lg * keep),
                cb * (lb * keep + saturation),
                0f,
                t,
                0f,
                0f,
                0f,
                1f,
                0f,
            ),
        ),
    )
}

private val InkAvatarFilterDay = inkAvatarFilter(dark = false)
private val InkAvatarFilterNight = inkAvatarFilter(dark = true)

@Composable
private fun CardIconTile(
    monogram: String,
    imageUrl: String?,
    size: Dp,
    colors: PersonalityColors,
) {
    val hasImage = !imageUrl.isNullOrBlank()
    when (LocalPersonality.current) {
        is MangaPersonality -> {
            Box(
                modifier =
                    Modifier
                        .size(size)
                        .clipToBounds()
                        .background(color = colors.surface)
                        .then(
                            if (hasImage) {
                                Modifier
                            } else {
                                Modifier.screentoneFill(
                                    color = colors.onSurface,
                                    opacity = colors.screentoneOpacity + 0.06f,
                                )
                            },
                        ).border(width = 2.5.dp, color = colors.outline),
                contentAlignment = Alignment.Center,
            ) {
                if (hasImage) {
                    GitHubStoreImage(
                        imageModel = { imageUrl },
                        modifier =
                            Modifier
                                .matchParentSize()
                                .halftoneOverlay(
                                    color = colors.onSurface,
                                    opacity = colors.screentoneOpacity + 0.06f,
                                ),
                        colorFilter = if (colors.isDark) InkAvatarFilterNight else InkAvatarFilterDay,
                    )
                } else {
                    MonogramText(monogram = monogram, color = colors.onSurface, fontSize = (size.value * 0.46f).sp)
                }
            }
        }

        is ClassicPersonality -> {
            Box(
                modifier =
                    Modifier
                        .size(size)
                        .clip(RoundedCornerShape(14.dp))
                        .background(color = colors.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (hasImage) {
                    GitHubStoreImage(
                        imageModel = { imageUrl },
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    MonogramText(monogram = monogram, color = colors.onSurface, fontSize = (size.value * 0.42f).sp)
                }
            }
        }
    }
}

private data class CardBadgeInfo(
    val feed: KomiRepoCardFeed,
    val rank: Int,
    val dailyStars: Int,
    val version: String?,
    val releasedAgoDays: Int,
)

@Composable
private fun CardBadge(
    badge: CardBadgeInfo,
    colors: PersonalityColors,
) {
    if (badge.feed == KomiRepoCardFeed.Plain) return
    when (LocalPersonality.current) {
        is MangaPersonality -> {
            when (badge.feed) {
                KomiRepoCardFeed.Popular -> {
                    RankStamp(rank = badge.rank, colors = colors)
                }

                KomiRepoCardFeed.Trending -> {
                    if (badge.dailyStars > 0) {
                        BurstBadge(text = "+${fmtCompact(badge.dailyStars)}", colors = colors)
                    }
                }

                KomiRepoCardFeed.Release -> {
                    NewReleaseStamp(
                        version = badge.version,
                        showStamp = badge.releasedAgoDays <= 7,
                        colors = colors,
                    )
                }

                KomiRepoCardFeed.Plain -> {}
            }
        }

        is ClassicPersonality -> {
            ClassicBadge(
                feed = badge.feed,
                rank = badge.rank,
                dailyStars = badge.dailyStars,
                releasedAgoDays = badge.releasedAgoDays,
                colors = colors,
            )
        }
    }
}

@Composable
private fun RankStamp(
    rank: Int,
    colors: PersonalityColors,
) {
    Box(modifier = Modifier.size(52.dp).rotate(-8f), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .clip(CircleShape)
                    .background(colors.primary)
                    .border(width = 2.5.dp, color = colors.outline, shape = CircleShape),
        )
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .padding(
                        3.dp,
                    ).border(
                        width = 1.5.dp,
                        color = colors.onPrimaryMuted,
                        shape = CircleShape,
                    ),
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KomiText(text = "NO.", role = KomiTextRole.Overline, color = colors.onPrimary)
            KomiText(text = rank.toString().padStart(2, '0'), role = KomiTextRole.Numeral, color = colors.onPrimary)
        }
    }
}

@Composable
private fun BurstBadge(
    text: String,
    colors: PersonalityColors,
) {
    Box(modifier = Modifier.size(58.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(color = colors.primary, shape = StarburstShape())
                    .border(width = 2.dp, color = colors.outline, shape = StarburstShape()),
        )
        KomiText(
            text = text,
            role = KomiTextRole.Stamp,
            color = colors.onPrimary,
            modifier = Modifier.rotate(-6f),
        )
    }
}

@Composable
private fun NewReleaseStamp(
    version: String?,
    showStamp: Boolean,
    colors: PersonalityColors,
) {
    if (!showStamp) return
    Column(horizontalAlignment = Alignment.End) {
        Box(
            modifier =
                Modifier
                    .rotate(2f)
                    .background(color = colors.primary)
                    .border(width = 2.dp, color = colors.outline)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            KomiText(text = "New Release", role = KomiTextRole.Overline, color = colors.onPrimary)
        }
        if (version != null) {
            KomiText(
                text = "v$version",
                role = KomiTextRole.Mono,
                color = colors.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun ClassicBadge(
    feed: KomiRepoCardFeed,
    rank: Int,
    dailyStars: Int,
    releasedAgoDays: Int,
    colors: PersonalityColors,
) {
    val text =
        when (feed) {
            KomiRepoCardFeed.Popular -> "No. $rank"
            KomiRepoCardFeed.Trending -> "+${fmtCompact(dailyStars)}"
            KomiRepoCardFeed.Release -> "New"
            KomiRepoCardFeed.Plain -> ""
        }
    val show =
        (feed != KomiRepoCardFeed.Release || releasedAgoDays <= 7) &&
            (feed != KomiRepoCardFeed.Trending || dailyStars > 0)
    if (text.isNotEmpty() && show) {
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(color = colors.primaryContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            KomiText(text = text, role = KomiTextRole.LabelSmall, color = colors.onPrimaryContainer)
        }
    }
}

@Composable
private fun Stat(
    icon: ImageVector,
    value: String,
    colors: PersonalityColors,
    hero: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = if (hero) colors.primary else colors.onSurface,
        )
        KomiText(text = value, role = KomiTextRole.Caption, color = colors.onSurfaceVariant, maxLines = 1)
    }
}

// The monogram stands in for an app icon, so it scales with its tile rather than following the type scale.
@Composable
private fun MonogramText(
    monogram: String,
    color: Color,
    fontSize: TextUnit,
) {
    Text(
        text = monogram.uppercase(),
        color = color,
        maxLines = 1,
        style =
            LocalPersonality.current.type.display
                .copy(fontSize = fontSize, lineHeight = fontSize),
    )
}

@Composable
private fun Diamond(color: Color) {
    Box(modifier = Modifier.size(4.dp).rotate(45f).background(color))
}

private const val THOUSAND = 1_000.0
private const val MILLION = 1_000_000.0
private const val WHOLE_UNITS_FROM = 10.0
private const val ONE_DECIMAL = 10.0

private fun fmtCompact(n: Int): String =
    when {
        n >= MILLION -> shorten(n / MILLION) + "M"
        n >= THOUSAND -> shorten(n / THOUSAND) + "k"
        else -> n.toString()
    }

// Ten units and up drop the decimal: 12k, not 12.3k.
private fun shorten(value: Double): String {
    if (value >= WHOLE_UNITS_FROM) return value.roundToInt().toString()
    val rounded = (value * ONE_DECIMAL).roundToInt() / ONE_DECIMAL
    return if (rounded == rounded.toLong().toDouble()) {
        rounded
            .toLong()
            .toString()
    } else {
        rounded.toString()
    }
}

@Composable
private fun PreviewCards() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
        KomiRepoCard(
            name = "immich",
            owner = "immich-app",
            language = "TypeScript",
            description = "High-performance self-hosted photo and video management. Back up and browse your library from any device.",
            platforms = persistentListOf(DiscoveryPlatform.Android),
            stars = 102900,
            downloads = 4946278,
            releasedAgoDays = 4,
            feed = KomiRepoCardFeed.Popular,
            rank = 1,
            dailyStars = 1820,
            onOpen = {},
        )
        KomiRepoCard(
            name = "localsend",
            owner = "localsend",
            language = "Dart",
            description = "Open-source AirDrop alternative: share files to nearby devices over your local network.",
            platforms =
                persistentListOf(
                    DiscoveryPlatform.Android,
                    DiscoveryPlatform.Windows,
                    DiscoveryPlatform.Macos,
                    DiscoveryPlatform.Linux,
                ),
            stars = 62100,
            downloads = 8330000,
            releasedAgoDays = 5,
            feed = KomiRepoCardFeed.Trending,
            dailyStars = 980,
            version = "1.17.0",
            onOpen = {},
        )
        KomiRepoCard(
            name = "jellyfin",
            owner = "jellyfin",
            language = "C#",
            description = "The free software media system that puts you in control of streaming your media.",
            platforms = persistentListOf(DiscoveryPlatform.Android, DiscoveryPlatform.Linux),
            stars = 38400,
            downloads = 12040000,
            releasedAgoDays = 3,
            feed = KomiRepoCardFeed.Release,
            version = "10.9.11",
            onOpen = {},
        )
    }
}

@Preview
@Composable
private fun KomiRepoCardMangaPreview() {
    PersonalityPreview(personality = mangaPersonality()) { PreviewCards() }
}

@Preview
@Composable
private fun KomiRepoCardMangaNightPreview() {
    PersonalityPreview(
        personality =
            mangaPersonality(
                paper = MangaPaper.NIGHT,
                accent = MangaAccent.SUN,
            ),
    ) { PreviewCards() }
}

@Preview
@Composable
private fun KomiRepoCardClassicPreview() {
    PersonalityPreview(personality = classicPersonality()) { PreviewCards() }
}
