package zed.rainxch.core.presentation.utils

import androidx.compose.runtime.Composable

data class DisplayModeOption(
    val id: Int,
    val widthPx: Int,
    val heightPx: Int,
    val refreshRate: Float,
)

// Empty when the platform reports nothing, which is also how the settings row hides itself.
@Composable
expect fun rememberDisplayModeOptions(): List<DisplayModeOption>
