package zed.rainxch.githubstore.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.bars.KomiBottomBar
import zed.rainxch.core.presentation.components.bars.KomiNavItem
import zed.rainxch.core.presentation.personality.utils.PersonalityPreview

@Composable
fun BottomNavigation(
    currentScreen: GithubStoreGraph,
    onNavigate: (GithubStoreGraph) -> Unit,
    isUpdateAvailable: Boolean,
    modifier: Modifier = Modifier,
    hasUnreadAnnouncements: Boolean = false,
) {
    val allowedScreens = BottomNavigationUtils.allowedScreens()
    if (allowedScreens.none { it.screen::class == currentScreen::class }) return

    fun idOf(screen: GithubStoreGraph): String = screen::class.simpleName ?: screen.toString()

    val items =
        allowedScreens
            .map { item ->
                KomiNavItem(
                    id = idOf(item.screen),
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
            ?.let { idOf(it.screen) }
            ?: items.first().id

    KomiBottomBar(
        items = items,
        selectedId = selectedId,
        onSelect = { id ->
            val target = allowedScreens.firstOrNull { idOf(it.screen) == id }?.screen
            if (target != null && target != currentScreen) onNavigate(target)
        },
        modifier = modifier,
    )
}

@Preview
@Composable
fun BottomNavigationPreview() {
    PersonalityPreview {
        BottomNavigation(
            currentScreen = GithubStoreGraph.ExploreScreen,
            onNavigate = {},
            isUpdateAvailable = true,
        )
    }
}
