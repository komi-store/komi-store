package zed.rainxch.githubstore.app.navigation

import androidx.navigation.NavBackStackEntry
import androidx.navigation.toRoute

fun NavBackStackEntry?.bottomNavIndex(): Int? {
    val route = this?.destination?.route ?: return null
    return when {
        route.contains("ExploreScreen") -> 0
        route.contains("ChartsScreen") -> 1
        route.contains("AppsScreen") -> 2
        route.contains("ProfileScreen") -> 3
        else -> null
    }
}

fun NavBackStackEntry?.getCurrentScreen(): GithubStoreGraph? {
    if (this == null) return null
    val route = destination.route ?: return null

    return when {
        route.contains("ExploreScreen") -> GithubStoreGraph.ExploreScreen
        route.contains("ChartsScreen") -> GithubStoreGraph.ChartsScreen
        route.contains("SearchScreen") -> toRoute<GithubStoreGraph.SearchScreen>()
        route.contains("AuthenticationScreen") -> GithubStoreGraph.AuthenticationScreen
        route.contains("DetailsScreen") -> toRoute<GithubStoreGraph.DetailsScreen>()
        route.contains("DeveloperProfileScreen") -> toRoute<GithubStoreGraph.DeveloperProfileScreen>()
        route.contains("ProfileScreen") -> GithubStoreGraph.ProfileGraph.ProfileScreen
        route.contains("TweaksScreen") -> GithubStoreGraph.TweaksScreen
        route.contains("RecentlyViewedScreen") -> GithubStoreGraph.RecentlyViewedScreen
        route.contains("FavouritesScreen") -> GithubStoreGraph.FavouritesScreen
        route.contains("StarredReposScreen") -> GithubStoreGraph.StarredReposScreen
        route.contains("AppsScreen") -> GithubStoreGraph.AppsScreen
        route.contains("WhatsNewHistoryScreen") -> GithubStoreGraph.WhatsNewHistoryScreen
        route.contains("AnnouncementsScreen") -> GithubStoreGraph.AnnouncementsScreen
        else -> null
    }
}
