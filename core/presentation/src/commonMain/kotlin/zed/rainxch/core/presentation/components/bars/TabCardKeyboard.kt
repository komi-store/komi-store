package zed.rainxch.core.presentation.components.bars

import androidx.compose.runtime.Composable

// The window pans for the keyboard, which would push a tab screen off the top, so it resizes while a tab card shows.
@Composable
internal expect fun ResizeWindowForKeyboard()
