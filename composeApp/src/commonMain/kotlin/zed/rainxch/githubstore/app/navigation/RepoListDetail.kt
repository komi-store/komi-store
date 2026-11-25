package zed.rainxch.githubstore.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.adaptive.AdaptiveDetailArgs
import zed.rainxch.core.presentation.components.adaptive.AdaptiveListDetailScaffold
import zed.rainxch.core.presentation.components.adaptive.rememberAdaptiveListDetailState
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.adaptive_pick_repo_subtitle
import zed.rainxch.githubstore.core.presentation.res.adaptive_pick_repo_title

// A repository list that opens Details beside it on a wide window and as its own screen otherwise.
@Composable
internal fun RepoListDetail(
    onNavigate: (GithubStoreGraph) -> Unit,
    modifier: Modifier = Modifier,
    list: @Composable (openDetails: (AdaptiveDetailArgs) -> Unit) -> Unit,
) {
    val listDetailState = rememberAdaptiveListDetailState()
    AdaptiveListDetailScaffold(
        state = listDetailState,
        emptyPaneTitle = stringResource(Res.string.adaptive_pick_repo_title),
        emptyPaneSubtitle = stringResource(Res.string.adaptive_pick_repo_subtitle),
        list = { isExpanded ->
            list { args ->
                if (isExpanded) {
                    listDetailState.select(args)
                } else {
                    onNavigate(args.toDetailsScreen())
                }
            }
        },
        detail = { args ->
            AdaptiveDetailPaneContent(
                args = args,
                onNavigate = onNavigate,
                onCrossNavToRepo = { newArgs -> listDetailState.select(newArgs) },
                onClearPane = { listDetailState.clear() },
            )
        },
        modifier = modifier,
    )
}

private fun AdaptiveDetailArgs.toDetailsScreen(): GithubStoreGraph.DetailsScreen =
    GithubStoreGraph.DetailsScreen(
        repositoryId = repositoryId,
        owner = owner.orEmpty(),
        repo = repo.orEmpty(),
        isComingFromUpdate = isComingFromUpdate,
        sourceHost = sourceHost,
        packageName = packageName,
    )
