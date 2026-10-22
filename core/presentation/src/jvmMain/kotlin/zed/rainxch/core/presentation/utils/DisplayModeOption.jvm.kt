package zed.rainxch.core.presentation.utils

import androidx.compose.runtime.Composable

// Desktop has no per-window display mode to pick, so the settings row stays hidden there.
@Composable
actual fun rememberDisplayModeOptions(): List<DisplayModeOption> = emptyList()
