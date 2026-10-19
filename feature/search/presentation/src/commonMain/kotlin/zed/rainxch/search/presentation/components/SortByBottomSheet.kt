package zed.rainxch.search.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.chips.KomiChip
import zed.rainxch.core.presentation.components.chips.KomiChipKind
import zed.rainxch.core.presentation.components.dividers.KomiHorizontalDivider
import zed.rainxch.core.presentation.components.overlays.KomiDialog
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.close
import zed.rainxch.githubstore.core.presentation.res.sort_by
import zed.rainxch.search.presentation.model.SortByUi
import zed.rainxch.search.presentation.model.SortOrderUi
import zed.rainxch.search.presentation.utils.label

@Composable
fun SortByBottomSheet(
    selectedSortBy: SortByUi,
    selectedSortOrder: SortOrderUi,
    onSortBySelected: (SortByUi) -> Unit,
    onSortOrderSelected: (SortOrderUi) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    KomiDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {},
        dismissButton = {
            KomiButton(
                onClick = onDismissRequest,
                label = stringResource(Res.string.close),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
            )
        },
        title = {
            KomiText(
                text = stringResource(Res.string.sort_by),
                role = KomiTextRole.CardTitle,
            )
        },
        text = {
            Column(
                modifier = modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SortByUi.entries.forEach { option ->
                    KomiButton(
                        onClick = { onSortBySelected(option) },
                        label = stringResource(option.label()),
                        variant = KomiButtonVariant.Text,
                        size = KomiButtonSize.Sm,
                        leadingIcon = if (option == selectedSortBy) Icons.Default.Check else null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                KomiHorizontalDivider()

                Spacer(Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SortOrderUi.entries.forEach { order ->
                        KomiChip(
                            label = stringResource(order.label()),
                            kind = KomiChipKind.Filter,
                            selected = order == selectedSortOrder,
                            onClick = { onSortOrderSelected(order) },
                        )
                    }
                }
            }
        },
    )
}
