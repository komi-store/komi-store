package zed.rainxch.githubstore.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.bars.KomiBottomBar
import zed.rainxch.core.presentation.components.bars.KomiNavItem
import zed.rainxch.core.presentation.components.bars.KomiTabCardState
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview

@Composable
fun BottomNavigation(
    currentScreen: GithubStoreGraph,
    onNavigate: (GithubStoreGraph) -> Unit,
    isUpdateAvailable: Boolean,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    hasUnreadAnnouncements: Boolean = false,
    tabCard: KomiTabCardState? = null,
) {
    val allowedScreens = BottomNavigationUtils.allowedScreens()
    if (allowedScreens.none { it.screen::class == currentScreen::class }) return

    val items =
        allowedScreens
            .map { item ->
                KomiNavItem(
                    id = tabIdOf(item.screen),
                    label = stringResource(item.titleRes),
                    icon = item.iconOutlined,
                    selectedIcon = item.iconFilled,
                    badgeCount =
                        when {
                            item.screen == GithubStoreGraph.AppsScreen && isUpdateAvailable -> 1
                            item.screen == GithubStoreGraph.ProfileGraph.ProfileScreen && hasUnreadAnnouncements -> 1
                            else -> 0
                        },
                )
            }.toImmutableList()

    val selectedId =
        allowedScreens
            .firstOrNull { it.screen::class == currentScreen::class }
            ?.let { tabIdOf(it.screen) }
            ?: items.first().id

    val onSelect: (String) -> Unit = { id ->
        val target = allowedScreens.firstOrNull { tabIdOf(it.screen) == id }?.screen
        if (target != null && target != currentScreen) onNavigate(target)
    }
    if (tabCard != null) {
        // Classic's tab screens draw the tab card themselves; its search field opens Search.
        tabCard.setTabs(items = items, onSelect = onSelect, onSearch = onSearch)
    } else {
        KomiBottomBar(items = items, selectedId = selectedId, onSelect = onSelect, modifier = modifier)
    }
}

// The tab a screen belongs to; tab screens hand their search and actions to the card under it.
fun tabIdOf(screen: GithubStoreGraph): String = screen::class.simpleName ?: screen.toString()

@Preview
@Composable
private fun BottomNavigationPreview() {
    PersonalityPreview {
        BottomNavigation(
            currentScreen = GithubStoreGraph.ExploreScreen,
            onNavigate = {},
            isUpdateAvailable = true,
            onSearch = {},
        )
    }
}
