package zed.rainxch.devprofile.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.toImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.inputs.KomiTextField
import zed.rainxch.core.presentation.components.overlays.KomiDropdown
import zed.rainxch.core.presentation.components.overlays.KomiMenuItem
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.devprofile.domain.model.RepoFilterType
import zed.rainxch.devprofile.domain.model.RepoSortType
import zed.rainxch.devprofile.presentation.DeveloperProfileAction
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.filter_favorites
import zed.rainxch.githubstore.core.presentation.res.filter_installed
import zed.rainxch.githubstore.core.presentation.res.filter_with_installable
import zed.rainxch.githubstore.core.presentation.res.filter_with_releases
import zed.rainxch.githubstore.core.presentation.res.repositories
import zed.rainxch.githubstore.core.presentation.res.repository_singular
import zed.rainxch.githubstore.core.presentation.res.search_repositories
import zed.rainxch.githubstore.core.presentation.res.showing_x_of_y_repositories
import zed.rainxch.githubstore.core.presentation.res.sort
import zed.rainxch.githubstore.core.presentation.res.sort_most_stars
import zed.rainxch.githubstore.core.presentation.res.sort_name
import zed.rainxch.githubstore.core.presentation.res.sort_recently_updated

@Composable
fun FilterSortControls(
    currentFilter: RepoFilterType,
    currentSort: RepoSortType,
    searchQuery: String,
    repoCount: Int,
    totalCount: Int,
    onAction: (DeveloperProfileAction) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        KomiTextField(
            value = searchQuery,
            onValueChange = { onAction(DeveloperProfileAction.OnSearchQueryChange(it)) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = stringResource(Res.string.search_repositories),
            leadingIcon = Icons.Rounded.Search,
            clearable = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RepoFilterType.entries.forEach { filter ->
                    FilterPill(
                        label = when (filter) {
                            RepoFilterType.WITH_RELEASES -> stringResource(Res.string.filter_with_releases)
                            RepoFilterType.WITH_INSTALLABLE -> stringResource(Res.string.filter_with_installable)
                            RepoFilterType.INSTALLED -> stringResource(Res.string.filter_installed)
                            RepoFilterType.FAVORITES -> stringResource(Res.string.filter_favorites)
                        },
                        isSelected = currentFilter == filter,
                        onClick = { onAction(DeveloperProfileAction.OnFilterChange(filter)) },
                    )
                }
            }

            SortButton(
                currentSort = currentSort,
                onSortChange = { onAction(DeveloperProfileAction.OnSortChange(it)) },
            )
        }

        KomiText(
            text = if (repoCount == totalCount) {
                "$repoCount ${stringResource(
                    if (repoCount == 1) Res.string.repository_singular else Res.string.repositories,
                )}"
            } else {
                stringResource(Res.string.showing_x_of_y_repositories, repoCount, totalCount)
            },
            maxLines = 1,
            role = KomiTextRole.Body,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilterPill(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val container by animateColorAsState(
        targetValue = if (isSelected) {
            colors.primary
        } else {
            colors.surfaceContainerHigh
        },
        animationSpec = LocalPersonality.current.motion.fastEffects(),
        label = "filter_container",
    )
    val content by animateColorAsState(
        targetValue = if (isSelected) {
            colors.onPrimary
        } else {
            colors.onSurface
        },
        animationSpec = LocalPersonality.current.motion.fastEffects(),
        label = "filter_content",
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        KomiText(
            text = label,
            role = KomiTextRole.LabelSmall,
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
private fun SortButton(
    currentSort: RepoSortType,
    onSortChange: (RepoSortType) -> Unit,
) {
    val colors = LocalPersonality.current.colors
    KomiDropdown(
        entries = RepoSortType.entries
            .map { sort ->
                KomiMenuItem(
                    id = sort.name,
                    label = when (sort) {
                        RepoSortType.UPDATED -> stringResource(Res.string.sort_recently_updated)
                        RepoSortType.STARS -> stringResource(Res.string.sort_most_stars)
                        RepoSortType.NAME -> stringResource(Res.string.sort_name)
                    },
                )
            }
            .toImmutableList(),
        onSelect = { item ->
            RepoSortType.entries.firstOrNull { it.name == item.id }?.let(onSortChange)
        },
        value = currentSort.name,
        trigger = { onClick ->
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(LocalPersonality.current.shape.cornerSmall))
                    .background(colors.surfaceContainerHigh)
                    .clickable { onClick() },
                contentAlignment = Alignment.Center,
            ) {
                KomiIcon(
                    imageVector = Icons.AutoMirrored.Filled.Sort,
                    contentDescription = stringResource(Res.string.sort),
                    modifier = Modifier.size(18.dp),
                    tint = colors.onSurface,
                )
            }
        },
    )
}
