package zed.rainxch.search.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.filter_by_language
import zed.rainxch.search.presentation.model.ProgrammingLanguageUi

@Composable
fun LanguageFilterBottomSheet(
    selectedLanguage: ProgrammingLanguageUi,
    onLanguageSelect: (ProgrammingLanguageUi) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KomiSheet(
        onDismiss = onDismissRequest,
        modifier = modifier,
        placement = KomiSheetPlacement.Bottom,
        title = stringResource(Res.string.filter_by_language),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
        ) {
            items(ProgrammingLanguageUi.entries.toList()) { language ->
                KomiChip(
                    label = stringResource(language.label),
                    kind = KomiChipKind.Filter,
                    selected = selectedLanguage == language,
                    onClick = {
                        onLanguageSelect(language)
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
