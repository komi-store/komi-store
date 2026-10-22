package zed.rainxch.details.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.details.domain.model.ReleaseCategory
import zed.rainxch.details.presentation.DetailsAction
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.category_all
import zed.rainxch.githubstore.core.presentation.res.category_pre_release
import zed.rainxch.githubstore.core.presentation.res.category_stable

@Composable
fun VersionTypePicker(
    selectedCategory: ReleaseCategory,
    onAction: (DetailsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(ReleaseCategory.entries) { category ->
            KomiChip(
                label =
                    stringResource(
                        when (category) {
                            ReleaseCategory.STABLE -> Res.string.category_stable
                            ReleaseCategory.PRE_RELEASE -> Res.string.category_pre_release
                            ReleaseCategory.ALL -> Res.string.category_all
                        },
                    ),
                kind = KomiChipKind.Filter,
                selected = category == selectedCategory,
                onClick = { onAction(DetailsAction.SelectReleaseCategory(category)) },
            )
        }
    }
}
