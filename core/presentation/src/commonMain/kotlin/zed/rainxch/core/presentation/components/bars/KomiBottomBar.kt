package zed.rainxch.core.presentation.components.bars

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarDefaults
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.core.presentation.components.badge.KomiBadge
import zed.rainxch.core.presentation.components.badge.KomiBadgeSize
import zed.rainxch.core.presentation.components.badge.KomiBadgedBox
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality
import zed.rainxch.core.presentation.personality.MangaPersonality
import zed.rainxch.core.presentation.personality.classicPersonality
import zed.rainxch.core.presentation.personality.manga.MangaAccent
import zed.rainxch.core.presentation.personality.manga.MangaPaper
import zed.rainxch.core.presentation.personality.manga.decoration.inkFocusRing
import zed.rainxch.core.presentation.personality.mangaPersonality
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview
import zed.rainxch.core.presentation.testing.KomiTestTags

@Composable
fun KomiBottomBar(
    items: ImmutableList<KomiNavItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val personality = LocalPersonality.current) {
        is MangaPersonality -> {
            MangaBottomBar(
                personality = personality,
                items = items,
                selectedId = selectedId,
                onSelect = onSelect,
                modifier = modifier,
            )
        }

        is ClassicPersonality -> {
            ClassicBottomBar(
                items = items,
                selectedId = selectedId,
                onSelect = onSelect,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun MangaBottomBar(
    personality: MangaPersonality,
    items: ImmutableList<KomiNavItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = personality.colors
    val pillShape =
        remember(personality.shape.cornerSmall) { RoundedCornerShape(personality.shape.cornerSmall) }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .background(colors.surface)
                .navigationBarsPadding()
                .height(66.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEach { item ->
            val selected = item.id == selectedId
            val interaction = remember { MutableInteractionSource() }
            val focused by interaction.collectIsFocusedAsState()

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag(KomiTestTags.navItem(item.id))
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = { onSelect(item.id) },
                        ).inkFocusRing(focused = { focused }, color = colors.primary),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                KomiBadgedBox(
                    badge = { KomiBadge(count = item.badgeCount, size = KomiBadgeSize.Sm) },
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(width = 48.dp, height = 30.dp)
                                .background(
                                    color = if (selected) colors.primary else Color.Transparent,
                                    shape = pillShape,
                                ).border(
                                    width = 2.5.dp,
                                    color = if (selected) colors.outline else Color.Transparent,
                                    shape = pillShape,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon ?: item.icon else item.icon,
                            contentDescription = item.label,
                            modifier = Modifier.size(20.dp),
                            tint = if (selected) colors.onPrimary else colors.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                KomiText(
                    text = item.label,
                    modifier = Modifier.testTag(KomiTestTags.navLabel(item.id)),
                    role = KomiTextRole.LabelSmall,
                    color = if (selected) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ClassicBottomBar(
    items: ImmutableList<KomiNavItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // On the page itself, like the top bar: the selected item's indicator is enough.
    ClassicNavRow(
        items = items,
        selectedId = selectedId,
        onSelect = onSelect,
        containerColor = LocalPersonality.current.colors.background,
        windowInsets = ShortNavigationBarDefaults.windowInsets,
        modifier = modifier,
    )
}

// Material's short navigation bar; the tab card puts the same row inside itself.
@Composable
internal fun ClassicNavRow(
    items: ImmutableList<KomiNavItem>,
    selectedId: String,
    onSelect: (String) -> Unit,
    containerColor: Color,
    windowInsets: WindowInsets,
    modifier: Modifier = Modifier,
) {
    ShortNavigationBar(modifier = modifier, containerColor = containerColor, windowInsets = windowInsets) {
        items.forEach { item ->
            val selected = item.id == selectedId
            ShortNavigationBarItem(
                selected = selected,
                onClick = { onSelect(item.id) },
                icon = {
                    KomiBadgedBox(
                        badge = { KomiBadge(count = item.badgeCount, size = KomiBadgeSize.Sm) },
                    ) {
                        Icon(
                            imageVector = if (selected) item.selectedIcon ?: item.icon else item.icon,
                            contentDescription = null,
                        )
                    }
                },
                label = {
                    KomiText(
                        text = item.label,
                        modifier = Modifier.testTag(KomiTestTags.navLabel(item.id)),
                        role = KomiTextRole.LabelSmall,
                        color = LocalContentColor.current,
                        maxLines = 1,
                    )
                },
            )
        }
    }
}

private val PreviewNav =
    persistentListOf(
        KomiNavItem(id = "home", label = "Home", icon = Icons.Default.Home),
        KomiNavItem(id = "charts", label = "Top Charts", icon = Icons.Default.Leaderboard, badgeCount = 3),
        KomiNavItem(id = "search", label = "Search", icon = Icons.Default.Search),
        KomiNavItem(id = "you", label = "You", icon = Icons.Default.Person, badgeCount = 12),
    )

@Composable
private fun PreviewBottomBar() {
    KomiBottomBar(items = PreviewNav, selectedId = "home", onSelect = {})
}

@Preview
@Composable
private fun KomiBottomBarMangaPreview() {
    PersonalityPreview(mangaPersonality()) { PreviewBottomBar() }
}

@Preview
@Composable
private fun KomiBottomBarMangaNightPreview() {
    PersonalityPreview(
        mangaPersonality(
            paper = MangaPaper.NIGHT,
            accent = MangaAccent.SUN,
        ),
    ) { PreviewBottomBar() }
}

@Preview
@Composable
private fun KomiBottomBarClassicPreview() {
    PersonalityPreview(classicPersonality()) { PreviewBottomBar() }
}

@Preview
@Composable
private fun KomiBottomBarClassicDarkPreview() {
    PersonalityPreview(classicPersonality(dark = true)) { PreviewBottomBar() }
}
