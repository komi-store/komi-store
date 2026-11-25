package zed.rainxch.githubstore.app.navigation

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.channels.ReceiveChannel
import org.koin.compose.viewmodel.koinViewModel
import zed.rainxch.apps.presentation.AppsViewModel
import zed.rainxch.core.domain.model.appearance.ContentWidth
import zed.rainxch.core.presentation.locals.LocalContentWidth
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.locals.LocalScrollbarEnabled
import zed.rainxch.githubstore.utils.HandleDesktopToolbarDeeplinks
import zed.rainxch.githubstore.utils.HandleKeyboardEvents
import zed.rainxch.profile.presentation.announcements.AnnouncementsViewModel
import zed.rainxch.profile.presentation.whatsnew.WhatsNewViewModel

// Owns the NavHostController the way a Root owns its ViewModel, outside the theme so a personality switch keeps the back stack.
@Composable
fun AppNavigation(
    deepLinkUris: ReceiveChannel<String>,
    onDeepLinkConsume: () -> Unit,
    isScrollbarEnabled: Boolean,
    contentWidth: ContentWidth,
    theme: @Composable (content: @Composable () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    overlay: @Composable (currentScreen: GithubStoreGraph?, onNavigate: (GithubStoreGraph) -> Unit) -> Unit,
) {
    val navController = rememberNavController()
    val currentScreen = navController.currentBackStackEntryAsState().value.getCurrentScreen()

    val appsViewModel = koinViewModel<AppsViewModel>()
    val appsState by appsViewModel.state.collectAsStateWithLifecycle()

    val whatsNewViewModel = koinViewModel<WhatsNewViewModel>()
    val announcementsViewModel = koinViewModel<AnnouncementsViewModel>()
    val announcementsUnreadCount by announcementsViewModel.unreadCount.collectAsStateWithLifecycle()

    HandleKeyboardEvents(onOpenSearch = { navController.openSearch() })

    HandleDesktopToolbarDeeplinks(
        deepLinkUris = deepLinkUris,
        onDeepLink = { navController.openDeepLink(it) },
        onDeepLinkConsume = onDeepLinkConsume,
    )

    theme {
        CompositionLocalProvider(
            LocalScrollbarEnabled provides isScrollbarEnabled,
            LocalContentWidth provides contentWidth,
        ) {
            AppShell(
                currentScreen = currentScreen,
                isUpdateAvailable = appsState.apps.any { it.installedApp.isUpdateAvailable },
                unreadAnnouncementsCount = announcementsUnreadCount,
                onNavigateToTab = { navController.navigateToTopLevel(it) },
                onSearch = { navController.navigate(GithubStoreGraph.SearchScreen()) },
                modifier = modifier,
            ) { tabCard, contentModifier ->
                SharedTransitionLayout(modifier = contentModifier) {
                    val motion = LocalPersonality.current.motion
                    NavHost(
                        navController = navController,
                        startDestination = GithubStoreGraph.ExploreScreen,
                        modifier = Modifier.background(LocalPersonality.current.colors.background),
                        enterTransition = { tabAwareEnter(motion) },
                        exitTransition = { tabAwareExit(motion) },
                        popEnterTransition = { fadeIn(motion.medium()) },
                        popExitTransition = { slideBackOut(motion) },
                    ) {
                        browseDestinations(navController, tabCard, this@SharedTransitionLayout)
                        searchDestination(navController, this@SharedTransitionLayout)
                        detailsDestinations(navController, this@SharedTransitionLayout, motion)
                        detailsLinkedDestinations(navController)
                        repoPagesDestinations(navController)
                        accountDestinations(navController)
                        importDestinations(navController)
                        profileGraph(
                            navController = navController,
                            unreadAnnouncementsCount = announcementsViewModel.unreadCount,
                            tabCard = tabCard,
                            sharedTransitionScope = this@SharedTransitionLayout,
                        )
                        profilePages(navController, whatsNewViewModel, announcementsViewModel)
                        settingsDestinations(navController)
                        appsDestinations(navController, tabCard, this@SharedTransitionLayout, appsViewModel)
                    }
                }
            }
        }

        overlay(currentScreen) { navController.navigate(it) }
    }
}
