package zed.rainxch.search.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.core.presentation.personality.model.primaryChipEdge
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.search_clear_filter_cd
import zed.rainxch.search.presentation.SearchAction
import zed.rainxch.search.presentation.model.ProgrammingLanguageUi
import zed.rainxch.search.presentation.model.SearchSourceUi
import zed.rainxch.search.presentation.model.SortByUi
import zed.rainxch.search.presentation.utils.label

// One removable chip per filter that is off its default; the platform has its own picker above.
@Composable
internal fun ActiveFiltersStrip(
    selectedSource: SearchSourceUi,
    selectedLanguage: ProgrammingLanguageUi,
    selectedSortBy: SortByUi,
    onAction: (SearchAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val showSource = selectedSource != SearchSourceUi.GitHub
    val showLanguage = selectedLanguage != ProgrammingLanguageUi.All
    val showSortBy = selectedSortBy != SortByUi.BestMatch
    if (!showSource && !showLanguage && !showSortBy) return
    val languageLabel = stringResource(selectedLanguage.label)
    val sortByLabel = stringResource(selectedSortBy.label())
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showSource) {
            ActiveFilterChip(
                label = selectedSource.label,
                leadingIcon = null,
                onRemove = { onAction(SearchAction.OnSourceSelected(SearchSourceUi.GitHub)) },
            )
        }
        if (showLanguage) {
            ActiveFilterChip(
                label = languageLabel,
                leadingIcon = Icons.Outlined.KeyboardArrowDown,
                onRemove = { onAction(SearchAction.OnLanguageSelected(ProgrammingLanguageUi.All)) },
            )
        }
        if (showSortBy) {
            ActiveFilterChip(
                label = sortByLabel,
                leadingIcon = Icons.AutoMirrored.Filled.Sort,
                onRemove = { onAction(SearchAction.OnSortBySelected(SortByUi.BestMatch)) },
            )
        }
    }
}

@Composable
private fun ActiveFilterChip(
    label: String,
    leadingIcon: ImageVector?,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = RoundedCornerShape(LocalPersonality.current.shape.cornerSmall)
    Row(
        modifier =
            modifier
                .clip(shape)
                .background(colors.primaryTint, shape)
                .border(1.dp, colors.primaryChipEdge, shape)
                .clickable(onClick = onRemove)
                .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingIcon != null) {
            KomiIcon(
                imageVector = leadingIcon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = colors.primary,
            )
        }

        KomiText(
            text = label,
            role = KomiTextRole.LabelSmall,
            color = colors.primary,
        )

        KomiIcon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(Res.string.search_clear_filter_cd),
            modifier = Modifier.size(14.dp),
            tint = colors.primary,
        )
    }
}
