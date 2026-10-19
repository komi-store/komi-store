package zed.rainxch.repopages.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import zed.rainxch.core.presentation.components.text.KomiText
import zed.rainxch.core.presentation.components.text.KomiTextRole
import zed.rainxch.core.presentation.locals.LocalPersonality

@Composable
fun RepoPagesEmpty(
    message: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalPersonality.current.colors
    Box(
        modifier = modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        KomiText(
            text = message,
            role = KomiTextRole.Body,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
