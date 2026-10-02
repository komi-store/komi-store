package zed.rainxch.githubstore.app.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import zed.rainxch.core.presentation.components.adaptive.AdaptiveDetailArgs
import zed.rainxch.details.presentation.DetailsRoot
import zed.rainxch.details.presentation.DetailsViewModel
import zed.rainxch.details.presentation.about.AboutRoot
import zed.rainxch.details.presentation.whatsnew.WhatsNewRoot

private sealed interface DetailPaneRoute {
    data object Main : DetailPaneRoute

    data class About(
        val repositoryId: Long,
        val owner: String,
        val repo: String,
        val sourceHost: String?,
        val translateTo: String? = null,
    ) : DetailPaneRoute

    data class WhatsNew(
        val repositoryId: Long,
        val owner: String,
        val repo: String,
        val sourceHost: String?,
    ) : DetailPaneRoute
}

@Composable
fun AdaptiveDetailPaneContent(
    args: AdaptiveDetailArgs,
    navController: NavHostController,
    onCrossNavToRepo: (AdaptiveDetailArgs) -> Unit,
    onClearPane: () -> Unit,
) {
    key(args) {
        var route: DetailPaneRoute by remember { mutableStateOf(DetailPaneRoute.Main) }

        AnimatedContent(
            targetState = route,
            transitionSpec = {
                val forward = targetState !is DetailPaneRoute.Main
                if (forward) {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> fullWidth },
                        animationSpec = tween(durationMillis = 280),
                    ) togetherWith
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -fullWidth / 4 },
                            animationSpec = tween(durationMillis = 280),
                        )
                } else {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -fullWidth / 4 },
                        animationSpec = tween(durationMillis = 280),
                    ) togetherWith
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(durationMillis = 280),
                        )
                }
            },
            label = "detail-pane-route",
        ) { current ->
            when (current) {
                DetailPaneRoute.Main -> {
                    MainDetailPane(
                        args = args,
                        navController = navController,
                        onCrossNavToRepo = onCrossNavToRepo,
                        onClearPane = onClearPane,
                        onOpenAbout = { repoId, owner, repo, sourceHost, translateTo ->
                            route = DetailPaneRoute.About(repoId, owner, repo, sourceHost, translateTo)
                        },
                        onOpenWhatsNew = { repoId, owner, repo, sourceHost ->
                            route = DetailPaneRoute.WhatsNew(repoId, owner, repo, sourceHost)
                        },
                    )
                }

                is DetailPaneRoute.About -> {
                    val aboutKey =
                        "about|${current.repositoryId}|${current.owner}|${current.repo}|${current.sourceHost.orEmpty()}"
                    AboutRoot(
                        repositoryId = current.repositoryId,
                        owner = current.owner,
                        repo = current.repo,
                        sourceHost = current.sourceHost,
                        translateTo = current.translateTo,
                        onNavigateBack = { route = DetailPaneRoute.Main },
                        onNavigateToMarkdownViewer = { url ->
                            navController.navigate(GithubStoreGraph.MarkdownViewerScreen(url))
                        },
                        viewModel =
                            koinViewModel(key = aboutKey) {
                                parametersOf(
                                    current.repositoryId,
                                    current.owner,
                                    current.repo,
                                    current.sourceHost,
                                )
                            },
                    )
                }

                is DetailPaneRoute.WhatsNew -> {
                    val whatsNewKey =
                        "whatsnew|${current.repositoryId}|${current.owner}|${current.repo}|${current.sourceHost.orEmpty()}"
                    WhatsNewRoot(
                        repositoryId = current.repositoryId,
                        owner = current.owner,
                        repo = current.repo,
                        sourceHost = current.sourceHost,
                        onNavigateBack = { route = DetailPaneRoute.Main },
                        viewModel =
                            koinViewModel(key = whatsNewKey) {
                                parametersOf(
                                    current.repositoryId,
                                    current.owner,
                                    current.repo,
                                    current.sourceHost,
                                )
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainDetailPane(
    args: AdaptiveDetailArgs,
    navController: NavHostController,
    onCrossNavToRepo: (AdaptiveDetailArgs) -> Unit,
    onClearPane: () -> Unit,
    onOpenAbout: (Long, String, String, String?, String?) -> Unit,
    onOpenWhatsNew: (Long, String, String, String?) -> Unit,
) {
    val vmKey =
        buildString {
            append(args.repositoryId)
            append('|')
            append(args.owner.orEmpty())
            append('|')
            append(args.repo.orEmpty())
            append('|')
            append(args.sourceHost.orEmpty())
            append('|')
            append(args.initialAssetName.orEmpty())
            append('|')
            append(args.packageName.orEmpty())
        }
    val viewModel: DetailsViewModel =
        koinViewModel(key = vmKey) {
            parametersOf(
                args.repositoryId,
                args.owner.orEmpty(),
                args.repo.orEmpty(),
                args.isComingFromUpdate,
                args.sourceHost,
                args.initialAssetName,
                args.packageName,
            )
        }
    DetailsRoot(
        onNavigateBack = onClearPane,
        onOpenRepositoryInApp = { repoId ->
            onCrossNavToRepo(AdaptiveDetailArgs(repositoryId = repoId))
        },
        onNavigateToDeveloperProfile = { username ->
            navController.navigate(
                GithubStoreGraph.DeveloperProfileScreen(username = username),
            )
        },
        onNavigateToSearchByPlatform = { platform ->
            navController.navigate(
                GithubStoreGraph.SearchScreen(
                    initialPlatform = platform.name,
                ),
            )
        },
        onNavigateToAbout = onOpenAbout,
        onNavigateToWhatsNew = onOpenWhatsNew,
        onNavigateToIssues = { owner, repo ->
            navController.navigate(GithubStoreGraph.RepoIssuesScreen(owner = owner, repo = repo))
        },
        onNavigateToSecurity = { owner, repo ->
            navController.navigate(GithubStoreGraph.RepoSecurityScreen(owner = owner, repo = repo))
        },
        onNavigateToMarkdownViewer = { url ->
            navController.navigate(GithubStoreGraph.MarkdownViewerScreen(url))
        },
        viewModel = viewModel,
    )
}
