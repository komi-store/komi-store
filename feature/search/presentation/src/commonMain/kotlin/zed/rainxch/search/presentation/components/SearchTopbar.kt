package zed.rainxch.search.presentation.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.isDesktop
import zed.rainxch.core.presentation.components.badge.KomiBadge
import zed.rainxch.core.presentation.components.badge.KomiBadgeTone
import zed.rainxch.core.presentation.components.badge.KomiBadgedBox
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.buttons.KomiIconButton
import zed.rainxch.core.presentation.components.buttons.KomiIconButtonSize
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.bottom_nav_search_title
import zed.rainxch.githubstore.core.presentation.res.search_filters_button
import zed.rainxch.githubstore.core.presentation.res.search_repositories_hint
import zed.rainxch.search.presentation.SearchAction

@Composable
internal fun SearchTopbar(
    query: String,
    activeFilterCount: Int,
    focusRequester: FocusRequester,
    onAction: (SearchAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    KomiTopBar(
        title = stringResource(Res.string.bottom_nav_search_title),
        modifier = modifier,
        // The Desktop sidebar holds Search; on a phone it is pushed over a tab.
        onBack = if (isDesktop()) null else ({ onAction(SearchAction.OnNavigateBackClick) }),
        search = {
            KomiTextField(
                value = query,
                onValueChange = { value ->
                    onAction(SearchAction.OnSearchChange(value))
                },
                placeholder = stringResource(Res.string.search_repositories_hint),
                leadingIcon = Icons.Rounded.Search,
                onClear = { onAction(SearchAction.OnClearClick) },
                keyboardType = KeyboardType.Text,
                onCommit = { onAction(SearchAction.OnSearchImeClick) },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                multiline = false,
            )
        },
        actions = {
            FiltersButton(
                activeCount = activeFilterCount,
                onClick = { onAction(SearchAction.OnToggleFiltersSheet) },
            )
        },
    )
}

// Filled once a filter is on, with how many as a badge.
@Composable
private fun FiltersButton(
    activeCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KomiBadgedBox(
        badge = {
            if (activeCount > 0) KomiBadge(count = activeCount, tone = KomiBadgeTone.Neutral)
        },
        modifier = modifier,
    ) {
        KomiIconButton(
            icon = Icons.Rounded.FilterList,
            contentDescription = stringResource(Res.string.search_filters_button),
            onClick = onClick,
            variant = if (activeCount > 0) KomiButtonVariant.Primary else KomiButtonVariant.Tonal,
            size = KomiIconButtonSize.Lg,
        )
    }
}
