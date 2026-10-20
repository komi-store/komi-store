package zed.rainxch.search.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.buttons.KomiButton
import zed.rainxch.core.presentation.components.buttons.KomiButtonSize
import zed.rainxch.core.presentation.components.buttons.KomiButtonVariant
import zed.rainxch.core.presentation.components.icon.KomiIcon
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.*

@Composable
fun SearchHistorySection(
    recentSearches: ImmutableList<String>,
    onHistoryItemClick: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KomiText(
                text = stringResource(Res.string.recent_searches),
                role = KomiTextRole.SectionTitle,
                color = colors.onSurfaceVariant,
            )

            KomiButton(
                onClick = onClearAll,
                label = stringResource(Res.string.clear_all_history),
                variant = KomiButtonVariant.Text,
                size = KomiButtonSize.Sm,
            )
        }

        recentSearches.forEach { query ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(shape.corner))
                        .clickable { onHistoryItemClick(query) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                KomiIcon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.onSurfaceVariant,
                )

                KomiText(
                    text = query,
                    role = KomiTextRole.Body,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Box(
                    modifier =
                        Modifier
                            .size(24.dp)
                            .clickable { onRemoveItem(query) },
                    contentAlignment = Alignment.Center,
                ) {
                    KomiIcon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(Res.string.remove_search_history_item),
                        modifier = Modifier.size(16.dp),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
