package zed.rainxch.search.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.progress.KomiCircularProgress
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.no_repositories_found
import zed.rainxch.githubstore.core.presentation.res.retry
import zed.rainxch.githubstore.core.presentation.res.search_results_hidden_by_seen_filter
import zed.rainxch.githubstore.core.presentation.res.searching_for_unseen_repos
import zed.rainxch.githubstore.core.presentation.res.show_all_results
import zed.rainxch.search.presentation.SearchState

@Composable
internal fun SearchLoadingMessage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        KomiCircularProgress()
    }
}

@Composable
internal fun SearchErrorMessage(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KomiText(
                text = message,
                role = KomiTextRole.Body,
            )

            Spacer(Modifier.height(8.dp))

            KomiButton(
                label = stringResource(Res.string.retry),
                onClick = onRetry,
            )
        }
    }
}

@Composable
internal fun NoResultsMessage(
    canExplore: Boolean,
    exploreStatus: SearchState.ExploreStatus,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KomiText(
                text = stringResource(Res.string.no_repositories_found),
                role = KomiTextRole.Body,
            )

            if (canExplore) {
                Spacer(Modifier.height(8.dp))
                ExploreFromGithubButton(
                    status = exploreStatus,
                    onExplore = onExplore,
                )
            }
        }
    }
}

@Composable
internal fun SearchingUnseenMessage(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KomiCircularProgress()

            Spacer(Modifier.height(8.dp))

            KomiText(
                text = stringResource(Res.string.searching_for_unseen_repos),
                role = KomiTextRole.Body,
                color = LocalPersonality.current.colors.outline,
            )
        }
    }
}

@Composable
internal fun HiddenBySeenMessage(
    onShowAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            KomiText(
                text = stringResource(Res.string.search_results_hidden_by_seen_filter),
                role = KomiTextRole.Body,
            )

            Spacer(Modifier.height(8.dp))

            KomiButton(
                label = stringResource(Res.string.show_all_results),
                onClick = onShowAll,
            )
        }
    }
}
