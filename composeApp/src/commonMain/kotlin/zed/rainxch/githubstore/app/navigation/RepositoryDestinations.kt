package zed.rainxch.githubstore.app.navigation

import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import zed.rainxch.core.presentation.locals.LocalAnimatedVisibilityScope
import zed.rainxch.core.presentation.locals.LocalSharedTransitionScope
import zed.rainxch.core.presentation.personality.model.PersonalityMotion
import zed.rainxch.details.presentation.DetailsRoot
import zed.rainxch.details.presentation.about.AboutRoot
import zed.rainxch.details.presentation.markdownviewer.MarkdownViewerRoot
import zed.rainxch.details.presentation.markdownviewer.MarkdownViewerViewModel
import zed.rainxch.details.presentation.whatsnew.WhatsNewRoot
import zed.rainxch.devprofile.presentation.DeveloperProfileRoot
import zed.rainxch.repopages.presentation.issuedetail.IssueDetailRoot
import zed.rainxch.repopages.presentation.issues.IssuesRoot
import zed.rainxch.repopages.presentation.pulls.PullsRoot
import zed.rainxch.repopages.presentation.security.SecurityRoot
import zed.rainxch.reviews.presentation.ReviewsRoot
import zed.rainxch.reviews.presentation.moderation.ModerationRoot

internal fun NavGraphBuilder.detailsDestinations(
    navController: NavHostController,
    sharedTransitionScope: SharedTransitionScope,
    motion: PersonalityMotion,
) {
    composable<GithubStoreGraph.DetailsScreen> { backStackEntry ->
        CompositionLocalProvider(
            LocalSharedTransitionScope provides sharedTransitionScope,
            LocalAnimatedVisibilityScope provides this@composable,
        ) {
            DetailsDestination(
                route = backStackEntry.toRoute(),
                onNavigate = { navController.navigate(it) },
                onNavigateBack = { navController.navigateUp() },
            )
        }
    }

    composable<GithubStoreGraph.DetailsAboutScreen>(
        enterTransition = { pushIn(motion) },
        exitTransition = { pushCovered(motion) },
        popEnterTransition = { popUncovered(motion) },
        popExitTransition = { popOut(motion) },
    ) { backStackEntry ->
        val args = backStackEntry.toRoute<GithubStoreGraph.DetailsAboutScreen>()
        AboutRoot(
            repositoryId = args.repositoryId,
            owner = args.owner,
            repo = args.repo,
            sourceHost = args.sourceHost,
            translateTo = args.translateTo,
            onNavigateBack = { navController.navigateUp() },
            onNavigateToMarkdownViewer = { url -> navController.navigate(GithubStoreGraph.MarkdownViewerScreen(url)) },
        )
    }

    composable<GithubStoreGraph.DetailsWhatsNewScreen>(
        enterTransition = { pushIn(motion) },
        exitTransition = { pushCovered(motion) },
        popEnterTransition = { popUncovered(motion) },
        popExitTransition = { popOut(motion) },
    ) { backStackEntry ->
        val args = backStackEntry.toRoute<GithubStoreGraph.DetailsWhatsNewScreen>()
        WhatsNewRoot(
            repositoryId = args.repositoryId,
            owner = args.owner,
            repo = args.repo,
            sourceHost = args.sourceHost,
            onNavigateBack = { navController.navigateUp() },
        )
    }
}

// The screens Details opens beyond its own pages: the markdown viewer, reviews and developer profiles.
internal fun NavGraphBuilder.detailsLinkedDestinations(navController: NavHostController) {
    composable<GithubStoreGraph.MarkdownViewerScreen> { backStackEntry ->
        val args = backStackEntry.toRoute<GithubStoreGraph.MarkdownViewerScreen>()
        MarkdownViewerRoot(
            onNavigateBack = { navController.navigateUp() },
            onNavigateToMarkdownViewer = { url -> navController.navigate(GithubStoreGraph.MarkdownViewerScreen(url)) },
            viewModel = koinViewModel<MarkdownViewerViewModel> { parametersOf(args.url) },
        )
    }

    composable<GithubStoreGraph.ReviewsScreen> { backStackEntry ->
        val args = backStackEntry.toRoute<GithubStoreGraph.ReviewsScreen>()
        ReviewsRoot(
            onNavigateBack = { navController.navigateUp() },
            onNavigateToAuthentication = { navController.navigate(GithubStoreGraph.AuthenticationScreen) },
            onNavigateToModeration = { navController.navigate(GithubStoreGraph.ReviewModerationScreen) },
            viewModel =
                koinViewModel {
                    parametersOf(
                        args.owner,
                        args.repo,
                        args.sourceHost,
                        args.openComposer,
                        args.composerRating,
                        args.version,
                        args.appLabel,
                    )
                },
        )
    }

    composable<GithubStoreGraph.ReviewModerationScreen> {
        ModerationRoot(
            onNavigateBack = { navController.navigateUp() },
            onOpenRepo = { repo ->
                navController.navigate(GithubStoreGraph.ReviewsScreen(owner = repo.owner, repo = repo.name, sourceHost = repo.host))
            },
            viewModel = koinViewModel(),
        )
    }

    composable<GithubStoreGraph.DeveloperProfileScreen> { backStackEntry ->
        val args = backStackEntry.toRoute<GithubStoreGraph.DeveloperProfileScreen>()
        DeveloperProfileRoot(
            onNavigateBack = { navController.navigateUp() },
            onNavigateToDetails = { repoId -> navController.navigate(GithubStoreGraph.DetailsScreen(repositoryId = repoId)) },
            onNavigateToUser = { username -> navController.navigate(GithubStoreGraph.DeveloperProfileScreen(username = username)) },
            viewModel = koinViewModel { parametersOf(args.username) },
        )
    }
}

internal fun NavGraphBuilder.repoPagesDestinations(navController: NavHostController) {
    composable<GithubStoreGraph.RepoIssuesScreen> { backStackEntry ->
        val args = backStackEntry.toRoute<GithubStoreGraph.RepoIssuesScreen>()
        IssuesRoot(
            onNavigateBack = { navController.navigateUp() },
            onOpenIssue = { issueNumber ->
                navController.navigate(
                    GithubStoreGraph.RepoIssueDetailScreen(owner = args.owner, repo = args.repo, issueNumber = issueNumber),
                )
            },
            viewModel = koinViewModel { parametersOf(args.owner, args.repo) },
        )
    }

    composable<GithubStoreGraph.RepoIssueDetailScreen> { backStackEntry ->
        val args = backStackEntry.toRoute<GithubStoreGraph.RepoIssueDetailScreen>()
        IssueDetailRoot(
            onNavigateBack = { navController.navigateUp() },
            viewModel = koinViewModel { parametersOf(args.owner, args.repo, args.issueNumber) },
        )
    }

    composable<GithubStoreGraph.RepoSecurityScreen> { backStackEntry ->
        val args = backStackEntry.toRoute<GithubStoreGraph.RepoSecurityScreen>()
        SecurityRoot(
            onNavigateBack = { navController.navigateUp() },
            viewModel = koinViewModel { parametersOf(args.owner, args.repo) },
        )
    }

    composable<GithubStoreGraph.RepoPullsScreen> { backStackEntry ->
        val args = backStackEntry.toRoute<GithubStoreGraph.RepoPullsScreen>()
        PullsRoot(
            onNavigateBack = { navController.navigateUp() },
            onOpenPull = { number ->
                navController.navigate(GithubStoreGraph.RepoIssueDetailScreen(owner = args.owner, repo = args.repo, issueNumber = number))
            },
            viewModel = koinViewModel { parametersOf(args.owner, args.repo) },
        )
    }
}

@Composable
private fun DetailsDestination(
    route: GithubStoreGraph.DetailsScreen,
    onNavigate: (GithubStoreGraph) -> Unit,
    onNavigateBack: () -> Unit,
) {
    DetailsRoot(
        onNavigateBack = onNavigateBack,
        onOpenRepositoryInApp = { repoId -> onNavigate(GithubStoreGraph.DetailsScreen(repositoryId = repoId)) },
        onNavigateToDeveloperProfile = { username -> onNavigate(GithubStoreGraph.DeveloperProfileScreen(username = username)) },
        onNavigateToAbout = { repoId, owner, repo, sourceHost, translateTo ->
            onNavigate(
                GithubStoreGraph.DetailsAboutScreen(
                    repositoryId = repoId,
                    owner = owner,
                    repo = repo,
                    sourceHost = sourceHost,
                    translateTo = translateTo,
                ),
            )
        },
        onNavigateToWhatsNew = { repoId, owner, repo, sourceHost ->
            onNavigate(GithubStoreGraph.DetailsWhatsNewScreen(repositoryId = repoId, owner = owner, repo = repo, sourceHost = sourceHost))
        },
        onNavigateToIssues = { owner, repo -> onNavigate(GithubStoreGraph.RepoIssuesScreen(owner = owner, repo = repo)) },
        onNavigateToSecurity = { owner, repo -> onNavigate(GithubStoreGraph.RepoSecurityScreen(owner = owner, repo = repo)) },
        onNavigateToReviews = { owner, repo, sourceHost, openComposer, composerRating, version, appLabel ->
            onNavigate(
                GithubStoreGraph.ReviewsScreen(
                    owner = owner,
                    repo = repo,
                    sourceHost = sourceHost,
                    openComposer = openComposer,
                    composerRating = composerRating,
                    version = version,
                    appLabel = appLabel,
                ),
            )
        },
        viewModel =
            koinViewModel {
                parametersOf(
                    route.repositoryId,
                    route.owner,
                    route.repo,
                    route.isComingFromUpdate,
                    route.sourceHost,
                    route.packageName,
                )
            },
    )
}
