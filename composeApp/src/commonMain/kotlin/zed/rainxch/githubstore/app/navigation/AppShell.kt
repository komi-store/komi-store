package zed.rainxch.githubstore.app.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import zed.rainxch.core.domain.isDesktop
import zed.rainxch.core.presentation.components.bars.KomiTabCardState
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.ClassicPersonality

// The desktop sidebar, the bottom bar or Classic's tab card around the screen the app shows.
@Composable
internal fun AppShell(
    currentScreen: GithubStoreGraph?,
    isUpdateAvailable: Boolean,
    unreadAnnouncementsCount: Int,
    onNavigateToTab: (GithubStoreGraph) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (tabCard: KomiTabCardState?, contentModifier: Modifier) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val rail = maxWidth < 1140.dp
        Row(modifier = Modifier.fillMaxSize()) {
            if (isDesktop() && currentScreen != null) {
                DesktopSidebar(
                    currentScreen = currentScreen,
                    onNavigate = onNavigateToTab,
                    rail = rail,
                    unreadAnnouncementsCount = unreadAnnouncementsCount,
                )
            }

            Box(
                modifier =
                    if (isDesktop()) {
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    } else {
                        Modifier.fillMaxSize()
                    },
            ) {
                val density = LocalDensity.current
                var bottomBarHeight by remember { mutableStateOf(0.dp) }
                // Classic's tab card, after Chocola; Manga keeps its bottom bar and the desktop its sidebar.
                val tabCardState = remember { KomiTabCardState() }
                val tabCard = tabCardState.takeIf { LocalPersonality.current is ClassicPersonality && !isDesktop() }
                val showBottomBar =
                    currentScreen != null &&
                        !isDesktop() &&
                        BottomNavigationUtils
                            .allowedScreens()
                            .any { it.screen::class == currentScreen::class }

                // Classic's tab screens draw their tab card over their own content.
                val bottomInset = if (showBottomBar && tabCard == null) bottomBarHeight else 0.dp
                content(
                    tabCard,
                    Modifier
                        .padding(bottom = bottomInset)
                        .consumeWindowInsets(PaddingValues(bottom = bottomInset)),
                )

                if (showBottomBar) {
                    BottomNavigation(
                        modifier =
                            Modifier
                                .align(Alignment.BottomCenter)
                                .onGloballyPositioned {
                                    bottomBarHeight = with(density) { it.size.height.toDp() }
                                },
                        currentScreen = currentScreen,
                        onNavigate = onNavigateToTab,
                        isUpdateAvailable = isUpdateAvailable,
                        onSearch = onSearch,
                        hasUnreadAnnouncements = unreadAnnouncementsCount > 0,
                        tabCard = tabCard,
                    )
                }
            }
        }
    }
}
