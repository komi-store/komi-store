package zed.rainxch.githubstore.app.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.components.adaptive.AdaptiveDetailArgs
import zed.rainxch.core.presentation.components.bars.KomiTabCardState
import zed.rainxch.core.presentation.components.bars.LocalTabCardSlot
import zed.rainxch.core.presentation.locals.LocalAnimatedVisibilityScope
import zed.rainxch.core.presentation.locals.LocalSharedTransitionScope
import zed.rainxch.feed.presentation.FeedRoot
import zed.rainxch.home.domain.model.HomeCategory
import zed.rainxch.home.presentation.HomeRoot
import zed.rainxch.home.presentation.categorylist.CategoryListRoot
import zed.rainxch.search.presentation.SearchRoot
import zed.rainxch.search.presentation.SearchViewModel

internal fun NavGraphBuilder.browseDestinations(
    navController: NavHostController,
    tabCard: KomiTabCardState?,
    sharedTransitionScope: SharedTransitionScope,
) {
    composable<GithubStoreGraph.ExploreScreen> {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides sharedTransitionScope,
            LocalAnimatedVisibilityScope provides this@composable,
            LocalTabCardSlot provides tabCard?.slot(tabIdOf(GithubStoreGraph.ExploreScreen)),
        ) {
            RepoListDetail(onNavigate = { navController.navigate(it) }) { openDetails ->
                FeedRoot(
                    onNavigateToDetails = { repoId -> openDetails(AdaptiveDetailArgs(repositoryId = repoId)) },
                    onNavigateToSearch = { navController.navigate(GithubStoreGraph.SearchScreen()) },
                    onNavigateToProfile = { navController.navigate(GithubStoreGraph.ProfileGraph.ProfileScreen) },
                )
            }
        }
    }

    composable<GithubStoreGraph.ChartsScreen> {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides sharedTransitionScope,
            LocalAnimatedVisibilityScope provides this@composable,
            LocalTabCardSlot provides tabCard?.slot(tabIdOf(GithubStoreGraph.ChartsScreen)),
        ) {
            RepoListDetail(onNavigate = { navController.navigate(it) }) { openDetails ->
                HomeRoot(
                    onNavigateToSearch = { navController.navigate(GithubStoreGraph.SearchScreen()) },
                    onNavigateToDetails = { repoId -> openDetails(AdaptiveDetailArgs(repositoryId = repoId)) },
                )
            }
        }
    }

    composable<GithubStoreGraph.CategoryListScreen> { backStackEntry ->
        val args = backStackEntry.toRoute<GithubStoreGraph.CategoryListScreen>()
        val category = runCatching { HomeCategory.valueOf(args.category) }.getOrDefault(HomeCategory.HOT_RELEASE)
        CategoryListRoot(
            category = category,
            onNavigateBack = { navController.navigateUp() },
            onNavigateToDetails = { repoId -> navController.navigate(GithubStoreGraph.DetailsScreen(repositoryId = repoId)) },
        )
    }
}

internal fun NavGraphBuilder.searchDestination(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
) {
    composable<GithubStoreGraph.SearchScreen> { backStackEntry ->
        val args = backStackEntry.toRoute<GithubStoreGraph.SearchScreen>()
        val initialPlatform = args.initialPlatform?.let { name -> runCatching { DiscoveryPlatform.valueOf(name) }.getOrNull() }
        val searchViewModel: SearchViewModel = koinViewModel { parametersOf(initialPlatform) }
        CompositionLocalProvider(
            LocalSharedTransitionScope provides sharedTransitionScope,
            LocalAnimatedVisibilityScope provides this@composable,
        ) {
            RepoListDetail(onNavigate = { navController.navigate(it) }) { openDetails ->
                SearchRoot(
                    onNavigateBack = { navController.navigateUp() },
                    onNavigateToDetails = { repoId, owner, repo, sourceHost ->
                        openDetails(AdaptiveDetailArgs(repositoryId = repoId, owner = owner, repo = repo, sourceHost = sourceHost))
                    },
                    onNavigateToDetailsFromLink = { owner, repo -> openDetails(AdaptiveDetailArgs(owner = owner, repo = repo)) },
                    onNavigateToDeveloperProfile = { username ->
                        navController.navigate(GithubStoreGraph.DeveloperProfileScreen(username = username))
                    },
                    viewModel = searchViewModel,
                )
            }
        }
    }
}
