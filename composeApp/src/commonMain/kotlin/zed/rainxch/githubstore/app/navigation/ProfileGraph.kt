package zed.rainxch.githubstore.app.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import kotlinx.coroutines.flow.StateFlow
import zed.rainxch.core.presentation.components.bars.KomiTabCardState
import zed.rainxch.core.presentation.components.bars.LocalTabCardSlot
import zed.rainxch.core.presentation.locals.LocalAnimatedVisibilityScope
import zed.rainxch.core.presentation.locals.LocalSharedTransitionScope
import zed.rainxch.profile.presentation.ProfileRoot
import zed.rainxch.profile.presentation.announcements.AnnouncementsScreen
import zed.rainxch.profile.presentation.announcements.AnnouncementsViewModel
import zed.rainxch.profile.presentation.whatsnew.WhatsNewHistoryScreen
import zed.rainxch.profile.presentation.whatsnew.WhatsNewViewModel

fun NavGraphBuilder.profileGraph(
    navController: NavHostController,
    unreadAnnouncementsCount: StateFlow<Int>,
    tabCard: KomiTabCardState?,
    sharedTransitionScope: SharedTransitionScope,
) {
    navigation<GithubStoreGraph.ProfileGraph>(
        startDestination = GithubStoreGraph.ProfileGraph.ProfileScreen,
    ) {
        composable<GithubStoreGraph.ProfileGraph.ProfileScreen> {
            val unreadCount by unreadAnnouncementsCount.collectAsStateWithLifecycle()
            CompositionLocalProvider(
                LocalSharedTransitionScope provides sharedTransitionScope,
                LocalAnimatedVisibilityScope provides this@composable,
                LocalTabCardSlot provides tabCard?.slot(tabIdOf(GithubStoreGraph.ProfileGraph.ProfileScreen)),
            ) {
                ProfileRoot(
                    onNavigateToAuthentication = {
                        navController.navigate(GithubStoreGraph.AuthenticationScreen)
                    },
                    onNavigateToStarredRepos = {
                        navController.navigate(GithubStoreGraph.StarredReposScreen)
                    },
                    onNavigateToFavouriteRepos = {
                        navController.navigate(GithubStoreGraph.FavouritesScreen)
                    },
                    onNavigateToRecentlyViewedRepos = {
                        navController.navigate(GithubStoreGraph.RecentlyViewedScreen)
                    },
                    onNavigateToDevProfile = { username ->
                        navController.navigate(
                            GithubStoreGraph.DeveloperProfileScreen(
                                username,
                            ),
                        )
                    },
                    onNavigateToWhatsNew = {
                        navController.navigate(GithubStoreGraph.WhatsNewHistoryScreen)
                    },
                    onNavigateToAnnouncements = {
                        navController.navigate(GithubStoreGraph.AnnouncementsScreen)
                    },
                    onNavigateToTweaks = {
                        navController.navigate(GithubStoreGraph.TweaksScreen)
                    },
                    onNavigateToAbout = {
                        navController.navigate(GithubStoreGraph.AboutScreen)
                    },
                    hasUnreadAnnouncements = unreadCount > 0,
                )
            }
        }
    }
}

// Both view models are app-wide: the unread badge and the what's-new sheet read them too.
internal fun NavGraphBuilder.profilePages(
    navController: NavHostController,
    whatsNewViewModel: WhatsNewViewModel,
    announcementsViewModel: AnnouncementsViewModel,
) {
    composable<GithubStoreGraph.WhatsNewHistoryScreen> {
        val historyEntries by whatsNewViewModel.historyEntries.collectAsStateWithLifecycle()
        WhatsNewHistoryScreen(
            entries = historyEntries,
            onNavigateBack = { navController.navigateUp() },
        )
    }

    composable<GithubStoreGraph.AnnouncementsScreen> {
        val announcementsState by announcementsViewModel.state.collectAsStateWithLifecycle()
        AnnouncementsScreen(
            state = announcementsState,
            onAction = announcementsViewModel::onAction,
            onNavigateBack = { navController.navigateUp() },
        )
    }
}
