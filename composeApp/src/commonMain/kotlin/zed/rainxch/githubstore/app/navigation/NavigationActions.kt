package zed.rainxch.githubstore.app.navigation

import androidx.navigation.NavHostController
import zed.rainxch.auth.presentation.AuthDeepLinkBus
import zed.rainxch.auth.presentation.AuthDeepLinkEvent
import zed.rainxch.githubstore.app.deeplink.DeepLinkDestination
import zed.rainxch.tweaks.presentation.utils.TweaksDeepLinkBus

// Leaving a top-level screen saves its back stack and coming back restores it; Explore stays at the root.
internal fun NavHostController.navigateToTopLevel(route: GithubStoreGraph) {
    navigate(route) {
        popUpTo(GithubStoreGraph.ExploreScreen) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = route != GithubStoreGraph.ExploreScreen
    }
}

internal fun NavHostController.navigateSingleTop(route: GithubStoreGraph) {
    navigate(route) {
        launchSingleTop = true
    }
}

internal fun NavHostController.openSearch() {
    if (currentBackStackEntry.getCurrentScreen() !is GithubStoreGraph.SearchScreen) {
        navigateToTopLevel(GithubStoreGraph.SearchScreen())
    }
}

internal fun NavHostController.openDeepLink(destination: DeepLinkDestination) {
    when (destination) {
        is DeepLinkDestination.Repository -> {
            navigate(GithubStoreGraph.DetailsScreen(owner = destination.owner, repo = destination.repo))
        }

        is DeepLinkDestination.AuthHandoff -> {
            AuthDeepLinkBus.publish(AuthDeepLinkEvent.Handoff(destination.handoffId, destination.state))
            openAuthentication()
        }

        is DeepLinkDestination.AuthError -> {
            AuthDeepLinkBus.publish(AuthDeepLinkEvent.Error(destination.reason, destination.state))
            openAuthentication()
        }

        DeepLinkDestination.Home -> {
            openHome()
        }

        DeepLinkDestination.Search -> {
            openSearch()
        }

        DeepLinkDestination.Apps -> {
            navigateToTopLevel(GithubStoreGraph.AppsScreen)
        }

        DeepLinkDestination.Favourites -> {
            navigateToTopLevel(GithubStoreGraph.FavouritesScreen)
        }

        DeepLinkDestination.RecentlyViewed -> {
            navigateToTopLevel(GithubStoreGraph.RecentlyViewedScreen)
        }

        DeepLinkDestination.Tweaks -> {
            navigateSingleTop(GithubStoreGraph.TweaksScreen)
        }

        DeepLinkDestination.Feedback -> {
            navigateSingleTop(GithubStoreGraph.TweaksScreen)
            TweaksDeepLinkBus.requestOpenFeedback()
        }

        DeepLinkDestination.About -> {
            navigateSingleTop(GithubStoreGraph.AboutScreen)
        }

        DeepLinkDestination.TweaksLicenses -> {
            navigateSingleTop(GithubStoreGraph.LicensesScreen)
        }

        DeepLinkDestination.None -> {
        }
    }
}

private fun NavHostController.openHome() {
    if (currentBackStackEntry.getCurrentScreen() !is GithubStoreGraph.ExploreScreen) {
        navigate(GithubStoreGraph.ExploreScreen) {
            popUpTo(GithubStoreGraph.ExploreScreen) { inclusive = true }
            launchSingleTop = true
        }
    }
}

private fun NavHostController.openAuthentication() {
    if (currentBackStackEntry.getCurrentScreen() !is GithubStoreGraph.AuthenticationScreen) {
        navigateSingleTop(GithubStoreGraph.AuthenticationScreen)
    }
}
