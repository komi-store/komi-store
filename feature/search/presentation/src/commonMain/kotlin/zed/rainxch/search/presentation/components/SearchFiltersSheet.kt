package zed.rainxch.search.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.overlays.KomiSheet
import zed.rainxch.core.presentation.components.overlays.KomiSheetPlacement
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.search_filters_apply
import zed.rainxch.githubstore.core.presentation.res.search_filters_reset
import zed.rainxch.githubstore.core.presentation.res.search_filters_section_language
import zed.rainxch.githubstore.core.presentation.res.search_filters_section_platform
import zed.rainxch.githubstore.core.presentation.res.search_filters_section_sort
import zed.rainxch.githubstore.core.presentation.res.search_filters_section_source
import zed.rainxch.githubstore.core.presentation.res.search_filters_title
import zed.rainxch.search.presentation.model.ProgrammingLanguageUi
import zed.rainxch.search.presentation.model.SearchSourceUi
import zed.rainxch.search.presentation.model.SortByUi
import zed.rainxch.search.presentation.utils.label
import zed.rainxch.core.presentation.utils.toLabel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchFiltersSheet(
    selectedSource: SearchSourceUi,
    availableSources: ImmutableList<SearchSourceUi>,
    selectedPlatform: DiscoveryPlatform,
    selectedLanguage: ProgrammingLanguageUi,
    selectedSortBy: SortByUi,
    onSourceSelected: (SearchSourceUi) -> Unit,
    onPlatformSelected: (DiscoveryPlatform) -> Unit,
    onOpenLanguagePicker: () -> Unit,
    onOpenSortPicker: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    KomiSheet(
        onDismiss = onDismiss,
        placement = KomiSheetPlacement.Bottom,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KomiText(
                    text = stringResource(Res.string.search_filters_title),
                    role = KomiTextRole.CardTitle,
                    modifier = Modifier.weight(1f),
                )
                KomiButton(
                    onClick = onReset,
                    label = stringResource(Res.string.search_filters_reset),
                    variant = KomiButtonVariant.Text,
                    size = KomiButtonSize.Sm,
                )
            }

            FilterSection(title = stringResource(Res.string.search_filters_section_source)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    availableSources.forEach { source ->
                        SelectableChip(
                            text = source.label,
                            selected = selectedSource == source,
                            onClick = { onSourceSelected(source) },
                        )
                    }
                }
            }

            FilterSection(title = stringResource(Res.string.search_filters_section_platform)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DiscoveryPlatform.entries.forEach { platform ->
                        SelectableChip(
                            text = platform.toLabel(),
                            selected = selectedPlatform == platform,
                            onClick = { onPlatformSelected(platform) },
                        )
                    }
                }
            }

            FilterSection(title = stringResource(Res.string.search_filters_section_language)) {
                NavRow(
                    leadingIcon = Icons.Outlined.Language,
                    value = stringResource(selectedLanguage.label()),
                    onClick = onOpenLanguagePicker,
                )
            }

            FilterSection(title = stringResource(Res.string.search_filters_section_sort)) {
                NavRow(
                    leadingIcon = Icons.AutoMirrored.Filled.Sort,
                    value = stringResource(selectedSortBy.label()),
                    onClick = onOpenSortPicker,
                )
            }

            Spacer(Modifier.height(4.dp))

            KomiButton(
                onClick = onDismiss,
                label = stringResource(Res.string.search_filters_apply),
                variant = KomiButtonVariant.Primary,
                size = KomiButtonSize.Lg,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun FilterSection(
    title: String,
    content: @Composable () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        KomiText(
            text = title,
            role = KomiTextRole.Label,
            color = colors.onSurfaceVariant,
        )

        content()
    }
}

@Composable
private fun SelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val container = if (selected) {
        colors.primary
    } else {
        colors.surfaceContainerHigh
    }
    val content = if (selected) {
        colors.onPrimary
    } else {
        colors.onSurface
    }
    val border = if (selected) {
        BorderStroke(0.dp, colors.primary)
    } else {
        BorderStroke(1.dp, colors.divider)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(shape.cornerSmall))
            .border(border, RoundedCornerShape(shape.cornerSmall))
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (selected) {
                KomiIcon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = content,
                )
            }

            KomiText(
                text = text,
                role = KomiTextRole.LabelSmall,
                color = content,
            )
        }
    }
}

@Composable
private fun NavRow(
    leadingIcon: ImageVector,
    value: String,
    onClick: () -> Unit,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(shape.corner))
            .border(
                width = 1.dp,
                color = colors.divider,
                shape = RoundedCornerShape(shape.corner),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            KomiIcon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )

            Spacer(Modifier.width(12.dp))

            KomiText(
                text = value,
                role = KomiTextRole.Label,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )

            KomiIcon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
