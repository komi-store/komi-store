package zed.rainxch.githubstore.app.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import zed.rainxch.apps.presentation.AppsAction
import zed.rainxch.apps.presentation.AppsRoot
import zed.rainxch.apps.presentation.AppsViewModel
import zed.rainxch.apps.presentation.import.EXTERNAL_IMPORT_OPEN_LINK_SHEET_KEY
import zed.rainxch.apps.presentation.import.ExternalImportRoot
import zed.rainxch.apps.presentation.starred.StarredPickerRoot
import zed.rainxch.core.presentation.components.adaptive.AdaptiveDetailArgs
import zed.rainxch.core.presentation.components.bars.KomiTabCardState
import zed.rainxch.core.presentation.components.bars.LocalTabCardSlot
import zed.rainxch.core.presentation.locals.LocalAnimatedVisibilityScope
import zed.rainxch.core.presentation.locals.LocalSharedTransitionScope
import zed.rainxch.favourites.presentation.import.ImportStarsRoot

internal fun NavGraphBuilder.appsDestinations(
    navController: NavHostController,
    tabCard: KomiTabCardState?,
    sharedTransitionScope: SharedTransitionScope,
    appsViewModel: AppsViewModel,
) {
    composable<GithubStoreGraph.AppsScreen> { backStackEntry ->
        LaunchedEffect(backStackEntry) {
            val handle = backStackEntry.savedStateHandle
            if (handle.get<Boolean>(EXTERNAL_IMPORT_OPEN_LINK_SHEET_KEY) == true) {
                handle.remove<Boolean>(EXTERNAL_IMPORT_OPEN_LINK_SHEET_KEY)
                appsViewModel.onAction(AppsAction.OnAddByLinkClick)
            }
        }
        val appsState by appsViewModel.state.collectAsStateWithLifecycle()
        CompositionLocalProvider(
            LocalSharedTransitionScope provides sharedTransitionScope,
            LocalAnimatedVisibilityScope provides this@composable,
            LocalTabCardSlot provides tabCard?.slot(tabIdOf(GithubStoreGraph.AppsScreen)),
        ) {
            RepoListDetail(onNavigate = { navController.navigate(it) }) { openDetails ->
                AppsRoot(
                    onNavigateBack = { navController.navigateUp() },
                    onNavigateToRepo = { repoId, sourceHost, owner, repo, packageName ->
                        openDetails(
                            AdaptiveDetailArgs(
                                repositoryId = repoId,
                                isComingFromUpdate = true,
                                sourceHost = sourceHost,
                                owner = owner,
                                repo = repo,
                                packageName = packageName,
                            ),
                        )
                    },
                    onNavigateToExternalImport = { navController.navigate(GithubStoreGraph.ExternalImportScreen) },
                    onNavigateToStarredPicker = { navController.navigate(GithubStoreGraph.StarredPickerScreen) },
                    viewModel = appsViewModel,
                    state = appsState,
                )
            }
        }
    }
}

// Importing apps and stars, opened from Apps and Favourites.
internal fun NavGraphBuilder.importDestinations(navController: NavHostController) {
    composable<GithubStoreGraph.ExternalImportScreen> {
        ExternalImportRoot(
            onNavigateBack = { navController.navigateUp() },
            onNavigateToDetails = { repoId ->
                navController.navigate(GithubStoreGraph.DetailsScreen(repositoryId = repoId, isComingFromUpdate = true))
            },
            onAddManually = {
                navController.previousBackStackEntry
                    ?.savedStateHandle
                    ?.set(EXTERNAL_IMPORT_OPEN_LINK_SHEET_KEY, true)
                navController.navigateUp()
            },
        )
    }

    composable<GithubStoreGraph.StarredPickerScreen> {
        StarredPickerRoot(
            onNavigateBack = { navController.navigateUp() },
            onNavigateToDetails = { repoId, owner, repo ->
                navController.navigate(GithubStoreGraph.DetailsScreen(repositoryId = repoId, owner = owner, repo = repo))
            },
        )
    }

    composable<GithubStoreGraph.ImportStarsScreen> {
        ImportStarsRoot(
            onNavigateBack = { navController.navigateUp() },
            onNavigateToDetails = { repoId, owner, repo ->
                navController.navigate(GithubStoreGraph.DetailsScreen(repositoryId = repoId, owner = owner, repo = repo))
            },
        )
    }
}
