package zed.rainxch.githubstore.app.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.toRoute

// The bottom bar's tabs in order, by a fragment of their route.
private val BOTTOM_NAV_ROUTES = listOf("ExploreScreen", "ChartsScreen", "AppsScreen", "ProfileScreen")

// Checked in order: a route can contain another's fragment, as DeveloperProfileScreen contains ProfileScreen.
private val SCREENS_BY_ROUTE: List<Pair<String, (NavBackStackEntry) -> GithubStoreGraph>> =
    listOf(
        "ExploreScreen" to { _ -> GithubStoreGraph.ExploreScreen },
        "ChartsScreen" to { _ -> GithubStoreGraph.ChartsScreen },
        "SearchScreen" to { entry -> entry.toRoute<GithubStoreGraph.SearchScreen>() },
        "AuthenticationScreen" to { _ -> GithubStoreGraph.AuthenticationScreen },
        "DetailsScreen" to { entry -> entry.toRoute<GithubStoreGraph.DetailsScreen>() },
        "DeveloperProfileScreen" to { entry -> entry.toRoute<GithubStoreGraph.DeveloperProfileScreen>() },
        "ProfileScreen" to { _ -> GithubStoreGraph.ProfileGraph.ProfileScreen },
        "TweaksScreen" to { _ -> GithubStoreGraph.TweaksScreen },
        "RecentlyViewedScreen" to { _ -> GithubStoreGraph.RecentlyViewedScreen },
        "FavouritesScreen" to { _ -> GithubStoreGraph.FavouritesScreen },
        "StarredReposScreen" to { _ -> GithubStoreGraph.StarredReposScreen },
        "AppsScreen" to { _ -> GithubStoreGraph.AppsScreen },
        "WhatsNewHistoryScreen" to { _ -> GithubStoreGraph.WhatsNewHistoryScreen },
        "AnnouncementsScreen" to { _ -> GithubStoreGraph.AnnouncementsScreen },
    )

fun NavBackStackEntry?.bottomNavIndex(): Int? {
    val route = this?.destination?.route ?: return null
    return BOTTOM_NAV_ROUTES.indexOfFirst { route.contains(it) }.takeIf { it >= 0 }
}

fun NavBackStackEntry?.getCurrentScreen(): GithubStoreGraph? =
    this?.let { entry ->
        val route = entry.destination.route.orEmpty()
        SCREENS_BY_ROUTE.firstOrNull { (fragment, _) -> route.contains(fragment) }?.second?.invoke(entry)
    }
