package zed.rainxch.repopages.presentation.components

import androidx.compose.runtime.Composable
import zed.rainxch.core.presentation.components.bars.KomiTopBar
import zed.rainxch.core.presentation.components.bars.KomiTopBarSize

@Composable
fun RepoPagesTopBar(
    title: String,
    onBack: () -> Unit,
) {
    KomiTopBar(
        title = title,
        size = KomiTopBarSize.Compact,
        onBack = onBack,
    )
}
