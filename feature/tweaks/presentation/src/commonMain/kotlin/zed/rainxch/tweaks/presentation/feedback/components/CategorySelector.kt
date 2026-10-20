package zed.rainxch.tweaks.presentation.feedback.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.feedback_category_label
import zed.rainxch.tweaks.presentation.feedback.model.FeedbackCategory

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategorySelector(
    selected: FeedbackCategory,
    onSelected: (FeedbackCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    Column(modifier = modifier) {
        KomiText(
            text = stringResource(Res.string.feedback_category_label),
            role = KomiTextRole.Overline,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FeedbackCategory.entries.forEach { category ->
                FeedbackPillChip(
                    label = stringResource(category.label),
                    isSelected = category == selected,
                    onClick = { onSelected(category) },
                )
            }
        }
    }
}

@Composable
internal fun FeedbackPillChip(
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
        label = "chip_container",
    )
    val content by animateColorAsState(
        targetValue = if (isSelected) {
            colors.onPrimary
        } else {
            colors.onSurface
        },
        animationSpec = LocalPersonality.current.motion.fastEffects(),
        label = "chip_content",
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
        )
    }
}
