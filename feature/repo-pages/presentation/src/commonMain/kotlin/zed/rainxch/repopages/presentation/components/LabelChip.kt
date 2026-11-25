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
import zed.rainxch.core.presentation.personality.labelFill
import zed.rainxch.repopages.presentation.model.IssueLabelUi

@Composable
internal fun LabelChip(
    label: IssueLabelUi,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    Box(
        modifier =
            modifier
                .background(color = label.color.labelFill, shape = RoundedCornerShape(shape.cornerSmall))
                .padding(horizontal = 8.dp, vertical = 2.dp),
    ) {
        KomiText(
            text = label.name,
            role = KomiTextRole.Caption,
            color = colors.onSurface,
        )
    }
}
