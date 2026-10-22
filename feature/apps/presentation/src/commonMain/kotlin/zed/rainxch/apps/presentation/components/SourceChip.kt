package zed.rainxch.apps.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality

@Composable
fun SourceChip(
    host: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    val shape = LocalPersonality.current.shape
    val label =
        when {
            host.equals("codeberg.org", ignoreCase = true) -> "Codeberg"
            else -> host
        }
    KomiText(
        text = label,
        role = KomiTextRole.LabelSmall,
        color = colors.onPrimaryContainer,
        modifier =
            modifier
                .clip(RoundedCornerShape(shape.cornerSmall))
                .background(colors.primaryContainer)
                .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
