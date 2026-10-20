package zed.rainxch.githubstore.app.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.repository.FeedCategory
import zed.rainxch.core.domain.repository.BrowseFilterStore
import zed.rainxch.core.presentation.components.badge.KomiBadge
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.manga.decoration.hardShadow
import zed.rainxch.core.presentation.personality.usesDecor
import zed.rainxch.core.presentation.utils.toIcon
import zed.rainxch.core.presentation.utils.toLabel
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.category_all
import zed.rainxch.githubstore.core.presentation.res.feed_category_ai
import zed.rainxch.githubstore.core.presentation.res.feed_category_media
import zed.rainxch.githubstore.core.presentation.res.feed_category_networking
import zed.rainxch.githubstore.core.presentation.res.feed_category_privacy
import zed.rainxch.githubstore.core.presentation.res.feed_category_reading
import zed.rainxch.githubstore.core.presentation.res.feed_category_social
import zed.rainxch.githubstore.core.presentation.res.feed_category_tools
import zed.rainxch.githubstore.core.presentation.res.sidebar_browse
import zed.rainxch.githubstore.core.presentation.res.sidebar_browse_jp
import zed.rainxch.githubstore.core.presentation.res.sidebar_categories
import zed.rainxch.githubstore.core.presentation.res.sidebar_categories_jp
import zed.rainxch.githubstore.core.presentation.res.sidebar_nav_charts_jp
import zed.rainxch.githubstore.core.presentation.res.sidebar_nav_explore_jp
import zed.rainxch.githubstore.core.presentation.res.sidebar_nav_search_jp
import zed.rainxch.githubstore.core.presentation.res.sidebar_nav_you_jp
import zed.rainxch.githubstore.core.presentation.res.sidebar_platform
import zed.rainxch.githubstore.core.presentation.res.sidebar_platform_jp

private val SidebarPlatforms =
    listOf(
        DiscoveryPlatform.All,
        DiscoveryPlatform.Android,
        DiscoveryPlatform.Windows,
        DiscoveryPlatform.Macos,
        DiscoveryPlatform.Linux,
    )

// Material's collapsed wide rail, wide enough for a label under each icon.
private val ClassicRailWidth = 96.dp

@Composable
fun DesktopSidebar(
    currentScreen: GithubStoreGraph?,
    onNavigate: (GithubStoreGraph) -> Unit,
    rail: Boolean,
    unreadAnnouncementsCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val isManga = LocalPersonality.current is MangaPersonality
    val store = koinInject<BrowseFilterStore>()
    val platform by store.platform.collectAsStateWithLifecycle()
    val category by store.category.collectAsStateWithLifecycle()

    val browseItems =
        BottomNavigationUtils.items().filterNot { it.screen == GithubStoreGraph.AppsScreen }
    val isBrowseScreen =
        currentScreen != null &&
            (
                currentScreen::class == GithubStoreGraph.ExploreScreen::class ||
                    currentScreen::class == GithubStoreGraph.ChartsScreen::class ||
                    currentScreen::class == GithubStoreGraph.SearchScreen()::class
            )
    val isHomeFeed =
        currentScreen != null && currentScreen::class == GithubStoreGraph.ExploreScreen::class

    Column(
        modifier =
            modifier
                .width(
                    when {
                        !rail -> 216.dp
                        isManga -> 62.dp
                        else -> ClassicRailWidth
                    },
                ).fillMaxHeight()
                .background(colors.surface)
                .drawBehind {
                    val w = (if (isManga) 3.dp else 1.dp).toPx()
                    drawRect(
                        color = if (isManga) colors.outline else colors.outlineVariant,
                        topLeft = Offset(size.width - w, 0f),
                        size = Size(w, size.height),
                    )
                }.verticalScroll(rememberScrollState())
                .padding(
                    top = if (rail) 12.dp else 16.dp,
                    bottom = 16.dp,
                    start = if (rail) 8.dp else 12.dp,
                    end = if (rail) 8.dp else 12.dp,
                ),
        verticalArrangement = Arrangement.spacedBy(if (rail) 6.dp else 4.dp),
    ) {
        SidebarSideHeading(
            stringResource(Res.string.sidebar_browse),
            stringResource(Res.string.sidebar_browse_jp),
            rail,
        )
        browseItems.forEach { item ->
            val selected = item.screen::class == currentScreen?.let { it::class }
            val badge =
                when (item.screen) {
                    GithubStoreGraph.ProfileGraph.ProfileScreen -> unreadAnnouncementsCount
                    else -> 0
                }
            SidebarNavRow(
                label = stringResource(item.titleRes),
                kicker = navKicker(item.screen),
                icon = if (selected) item.iconFilled else item.iconOutlined,
                selected = selected,
                rail = rail,
                badgeCount = badge,
                onClick = { onNavigate(item.screen) },
            )
        }

        if (isBrowseScreen) {
            Spacer(Modifier.height(if (rail) 2.dp else 6.dp))
            SidebarSideHeading(
                stringResource(Res.string.sidebar_platform),
                stringResource(Res.string.sidebar_platform_jp),
                rail,
            )
            SidebarPlatforms.forEach { p ->
                SidebarPlatformRow(
                    platform = p,
                    selected = p == platform,
                    rail = rail,
                    onClick = { store.setPlatform(p) },
                )
            }
        }

        if (isHomeFeed) {
            Spacer(Modifier.height(if (rail) 2.dp else 6.dp))
            SidebarSideHeading(
                stringResource(Res.string.sidebar_categories),
                stringResource(Res.string.sidebar_categories_jp),
                rail,
            )
            FeedCategory.entries.forEach { c ->
                SidebarCategoryRow(
                    category = c,
                    selected = c == category,
                    rail = rail,
                    onClick = { store.setCategory(c) },
                )
            }
        }
    }
}

@Composable
private fun SidebarSideHeading(
    title: String,
    jp: String,
    rail: Boolean,
) {
    val colors = LocalPersonality.current.colors
    if (rail) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .width(30.dp)
                        .height(2.5.dp)
                        .background(colors.outline),
            )
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KomiText(
                text = title,
                role = KomiTextRole.Overline,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
            if (LocalPersonality.current.usesDecor) {
                KomiText(
                    text = jp,
                    role = KomiTextRole.Caption,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SidebarNavRow(
    label: String,
    kicker: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    rail: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val isManga = LocalPersonality.current is MangaPersonality
    if (!isManga) {
        ClassicNavRow(label = label, icon = icon, selected = selected, rail = rail, badgeCount = badgeCount, onClick = onClick)
        return
    }
    val shape = RoundedCornerShape(LocalPersonality.current.shape.cornerSmall)
    val fg = if (selected) colors.onPrimary else colors.onSurface

    val rowModifier =
        Modifier
            .fillMaxWidth()
            .then(
                if (selected && isManga) {
                    Modifier.hardShadow(
                        offset = DpOffset(3.dp, 3.dp),
                        color = colors.shadow,
                        shape = shape,
                    )
                } else {
                    Modifier
                },
            ).clip(shape)
            .then(if (selected) Modifier.background(colors.primary) else Modifier.hoverWash(colors.onSurface))
            .border(
                width = if (selected && isManga) 2.5.dp else 0.dp,
                color = if (selected && isManga) colors.outline else Color.Transparent,
                shape = shape,
            ).clickable(onClick = onClick)

    if (rail) {
        Box(
            modifier = rowModifier.height(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            NavIcon(icon, fg, badgeCount)
        }
    } else {
        Row(
            modifier = rowModifier.height(38.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            NavIcon(icon, fg, badgeCount)
            KomiText(
                text = label,
                role = KomiTextRole.Label,
                color = fg,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            kicker?.takeIf { LocalPersonality.current.usesDecor }?.let {
                KomiText(
                    text = it,
                    role = KomiTextRole.Caption,
                    color = fg.copy(alpha = 0.8f),
                    maxLines = 1,
                )
            }
        }
    }
}

// Material 3 Expressive's wide rail item: a pill behind the selected icon, the label under the
// icon when the rail is collapsed and beside it when expanded.
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ClassicNavRow(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    rail: Boolean,
    badgeCount: Int,
    onClick: () -> Unit,
) {
    WideNavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = { NavIcon(icon, LocalContentColor.current, badgeCount) },
        label = {
            KomiText(
                text = label,
                role = if (rail) KomiTextRole.LabelSmall else KomiTextRole.Label,
                color = LocalContentColor.current,
                maxLines = 1,
            )
        },
        railExpanded = !rail,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NavIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    badgeCount: Int,
) {
    if (badgeCount > 0) {
        Box(contentAlignment = Alignment.TopEnd) {
            KomiIcon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = tint,
            )
            KomiBadge(count = badgeCount, dot = true)
        }
    } else {
        KomiIcon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = tint,
        )
    }
}

@Composable
private fun SidebarPlatformRow(
    platform: DiscoveryPlatform,
    selected: Boolean,
    rail: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val isManga = LocalPersonality.current is MangaPersonality
    val label =
        if (platform == DiscoveryPlatform.All) {
            stringResource(Res.string.category_all)
        } else {
            platform.toLabel()
        }
    if (rail) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
                    .then(
                        if (selected) {
                            if (isManga) {
                                Modifier.border(
                                    2.dp,
                                    colors.outline,
                                    RoundedCornerShape(LocalPersonality.current.shape.cornerSmall),
                                )
                            } else {
                                Modifier.background(colors.primaryContainer)
                            }
                        } else {
                            Modifier.hoverWash(colors.onSurface)
                        },
                    ).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            val icon = platform.toIcon()
            if (icon != null) {
                KomiIcon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(16.dp),
                    tint = colors.onSurface,
                )
            } else {
                KomiText(
                    text = label,
                    role = KomiTextRole.Overline,
                    color = colors.onSurface,
                    maxLines = 1,
                )
            }
        }
    } else {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
                    .hoverWash(colors.onSurface)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            InkCheckbox(checked = selected)
            val icon = platform.toIcon()
            icon?.let { icon ->
                KomiIcon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = colors.onSurface,
                )
            }
            KomiText(
                text = label,
                role = KomiTextRole.Label,
                color = colors.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SidebarCategoryRow(
    category: FeedCategory,
    selected: Boolean,
    rail: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val isManga = LocalPersonality.current is MangaPersonality
    if (rail) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
                    .then(
                        if (selected) {
                            if (isManga) {
                                Modifier.border(
                                    2.dp,
                                    colors.outline,
                                    RoundedCornerShape(LocalPersonality.current.shape.cornerSmall),
                                )
                            } else {
                                Modifier.background(colors.primaryContainer)
                            }
                        } else {
                            Modifier.hoverWash(colors.onSurface)
                        },
                    ).clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            InkDiamond(filled = selected)
        }
    } else {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
                    .hoverWash(colors.onSurface)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            InkDiamond(filled = selected)
            KomiText(
                text = stringResource(category.sidebarLabelRes()),
                role = KomiTextRole.Label,
                color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun InkCheckbox(checked: Boolean) {
    val colors = LocalPersonality.current.colors
    val isManga = LocalPersonality.current is MangaPersonality
    val shape = if (isManga) RectangleShape else RoundedCornerShape(4.dp)
    val borderColor =
        if (isManga) {
            colors.outline
        } else if (checked) {
            colors.primary
        } else {
            colors.outlineVariant
        }
    Box(
        modifier =
            Modifier
                .size(14.dp)
                .clip(shape)
                .background(if (checked) colors.primary else Color.Transparent)
                .border(if (isManga) 2.dp else 1.5.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            KomiIcon(
                Icons.Rounded.Check,
                contentDescription = null,
                modifier = Modifier.size(10.dp),
                tint = colors.onPrimary,
            )
        }
    }
}

@Composable
private fun InkDiamond(filled: Boolean) {
    val colors = LocalPersonality.current.colors
    val isManga = LocalPersonality.current is MangaPersonality
    if (isManga) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .rotate(45f)
                    .then(
                        if (filled) {
                            Modifier.background(colors.primary)
                        } else {
                            Modifier.border(1.5.dp, colors.onSurfaceVariant)
                        },
                    ),
        )
    } else {
        Box(
            modifier =
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .then(
                        if (filled) {
                            Modifier.background(colors.primary)
                        } else {
                            Modifier.border(1.5.dp, colors.outlineVariant, CircleShape)
                        },
                    ),
        )
    }
}

@Composable
private fun Modifier.hoverWash(color: Color): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    return this
        .hoverable(interaction)
        .background(if (hovered) color.copy(alpha = 0.08f) else Color.Transparent)
}

@Composable
private fun navKicker(screen: GithubStoreGraph): String? =
    when (screen::class) {
        GithubStoreGraph.ExploreScreen::class -> stringResource(Res.string.sidebar_nav_explore_jp)
        GithubStoreGraph.ChartsScreen::class -> stringResource(Res.string.sidebar_nav_charts_jp)
        GithubStoreGraph.SearchScreen()::class -> stringResource(Res.string.sidebar_nav_search_jp)
        GithubStoreGraph.ProfileGraph.ProfileScreen::class -> stringResource(Res.string.sidebar_nav_you_jp)
        else -> null
    }

private fun FeedCategory.sidebarLabelRes() =
    when (this) {
        FeedCategory.All -> Res.string.category_all
        FeedCategory.Ai -> Res.string.feed_category_ai
        FeedCategory.Privacy -> Res.string.feed_category_privacy
        FeedCategory.Networking -> Res.string.feed_category_networking
        FeedCategory.Media -> Res.string.feed_category_media
        FeedCategory.Social -> Res.string.feed_category_social
        FeedCategory.Reading -> Res.string.feed_category_reading
        FeedCategory.Tools -> Res.string.feed_category_tools
    }
