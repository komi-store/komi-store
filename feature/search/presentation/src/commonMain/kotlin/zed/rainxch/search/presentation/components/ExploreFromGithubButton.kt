package zed.rainxch.search.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.TravelExplore
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.fetch_more_from_github
import zed.rainxch.githubstore.core.presentation.res.fetching_from_github
import zed.rainxch.githubstore.core.presentation.res.no_more_github_results
import zed.rainxch.search.presentation.SearchState

@Composable
internal fun ExploreFromGithubButton(
    status: SearchState.ExploreStatus,
    onExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (status) {
            SearchState.ExploreStatus.IDLE -> {
                KomiButton(
                    onClick = onExplore,
                    label = stringResource(Res.string.fetch_more_from_github),
                    variant = KomiButtonVariant.Outline,
                    leadingIcon = Icons.Outlined.TravelExplore,
                )
            }

            SearchState.ExploreStatus.LOADING -> {
                KomiButton(
                    onClick = {},
                    label = stringResource(Res.string.fetching_from_github),
                    variant = KomiButtonVariant.Outline,
                    enabled = false,
                    loading = true,
                )
            }

            SearchState.ExploreStatus.EXHAUSTED -> {
                KomiText(
                    text = stringResource(Res.string.no_more_github_results),
                    role = KomiTextRole.Body,
                    color = LocalPersonality.current.colors.outline,
                )
            }
        }
    }
}
