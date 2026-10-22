package zed.rainxch.repopages.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality
import zed.rainxch.repopages.domain.model.IssueLabel
import zed.rainxch.repopages.presentation.utils.parseLabelColor

@Composable
internal fun LabelChip(label: IssueLabel) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val color = parseLabelColor(label.color)
    Box(
        modifier =
            Modifier
                .background(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(shape.cornerSmall))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        KomiText(
            text = label.name,
            role = KomiTextRole.Caption,
            color = colors.onSurface,
        )
    }
}
