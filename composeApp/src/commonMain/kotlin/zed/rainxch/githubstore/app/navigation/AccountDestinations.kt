package zed.rainxch.githubstore.app.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import zed.rainxch.auth.presentation.AuthenticationRoot
import zed.rainxch.favourites.presentation.FavouritesRoot
import zed.rainxch.recentlyviewed.presentation.RecentlyViewedRoot
import zed.rainxch.starred.presentation.StarredReposRoot
import zed.rainxch.tweaks.presentation.TweaksRoot
import zed.rainxch.tweaks.presentation.appinfo.AppInfoRoot
import zed.rainxch.tweaks.presentation.hidden.HiddenRepositoriesRoot
import zed.rainxch.tweaks.presentation.hosttokens.HostTokensRoot
import zed.rainxch.tweaks.presentation.licenses.LicensesRoot
import zed.rainxch.tweaks.presentation.mirror.MirrorPickerRoot
import zed.rainxch.tweaks.presentation.skipped.SkippedUpdatesRoot

// Sign-in and the repository lists the Profile tab opens.
internal fun NavGraphBuilder.accountDestinations(navController: NavHostController) {
    composable<GithubStoreGraph.AuthenticationScreen> {
        AuthenticationRoot(onNavigateToHome = { navController.leaveAuthentication() })
    }

    composable<GithubStoreGraph.FavouritesScreen> {
        FavouritesRoot(
            onNavigateBack = { navController.navigateUp() },
            onNavigateToDetails = { repoId, owner, repo, sourceHost ->
                navController.navigate(
                    GithubStoreGraph.DetailsScreen(repositoryId = repoId, owner = owner, repo = repo, sourceHost = sourceHost),
                )
            },
            onNavigateToDeveloperProfile = { username -> navController.navigate(GithubStoreGraph.DeveloperProfileScreen(username)) },
            onNavigateToImportStars = { navController.navigate(GithubStoreGraph.ImportStarsScreen) },
        )
    }

    composable<GithubStoreGraph.StarredReposScreen> {
        StarredReposRoot(
            onNavigateBack = { navController.navigateUp() },
            onNavigateToDetails = { repoId -> navController.navigate(GithubStoreGraph.DetailsScreen(repositoryId = repoId)) },
            onNavigateToAuthentication = { navController.navigate(GithubStoreGraph.AuthenticationScreen) },
            onNavigateToDeveloperProfile = { username -> navController.navigate(GithubStoreGraph.DeveloperProfileScreen(username)) },
        )
    }

    composable<GithubStoreGraph.RecentlyViewedScreen> {
        RecentlyViewedRoot(
            onNavigateBack = { navController.navigateUp() },
            onNavigateToDetails = { repoId, owner, repo, sourceHost ->
                navController.navigate(
                    GithubStoreGraph.DetailsScreen(repositoryId = repoId, owner = owner, repo = repo, sourceHost = sourceHost),
                )
            },
            onNavigateToDeveloperProfile = { username -> navController.navigate(GithubStoreGraph.DeveloperProfileScreen(username)) },
        )
    }
}

internal fun NavGraphBuilder.settingsDestinations(navController: NavHostController) {
    composable<GithubStoreGraph.TweaksScreen> {
        TweaksRoot(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToHostTokens = { navController.navigateSingleTop(GithubStoreGraph.HostTokensScreen) },
            onNavigateToMirrorPicker = { navController.navigateSingleTop(GithubStoreGraph.MirrorPickerScreen) },
            onNavigateToSkippedUpdates = { navController.navigateSingleTop(GithubStoreGraph.SkippedUpdatesScreen) },
            onNavigateToHiddenRepositories = { navController.navigateSingleTop(GithubStoreGraph.HiddenRepositoriesScreen) },
        )
    }

    composable<GithubStoreGraph.AboutScreen> {
        AppInfoRoot(
            onNavigateBack = { navController.popBackStack() },
            onNavigateToLicenses = { navController.navigateSingleTop(GithubStoreGraph.LicensesScreen) },
        )
    }

    composable<GithubStoreGraph.LicensesScreen> {
        LicensesRoot(onNavigateBack = { navController.popBackStack() })
    }

    composable<GithubStoreGraph.SkippedUpdatesScreen> {
        SkippedUpdatesRoot(onNavigateBack = { navController.popBackStack() })
    }

    composable<GithubStoreGraph.HiddenRepositoriesScreen> {
        HiddenRepositoriesRoot(onNavigateBack = { navController.popBackStack() })
    }

    composable<GithubStoreGraph.HostTokensScreen> {
        HostTokensRoot(onNavigateBack = { navController.popBackStack() })
    }

    composable<GithubStoreGraph.MirrorPickerScreen> {
        MirrorPickerRoot(onNavigateBack = { navController.popBackStack() })
    }
}

// Signing in from Reviews returns there, and Reviews opens the composer it was asked for.
private fun NavHostController.leaveAuthentication() {
    val fromReviews = previousBackStackEntry?.destination?.route?.contains("ReviewsScreen") == true
    if (fromReviews) {
        navigateUp()
    } else {
        navigate(GithubStoreGraph.ExploreScreen) {
            popUpTo(0) {
                inclusive = true
            }
        }
    }
}
