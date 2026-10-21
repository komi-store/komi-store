package zed.rainxch.core.presentation.components.bars

import androidx.compose.runtime.Composable

// The app's window pans for the keyboard (adjustPan in the manifest), which keeps the other
// screens' fields in view but would push a tab screen's content off the top while the card's field
// has the keyboard. While a tab card is on screen the window resizes instead, and the card sits on
// the keyboard itself.
@Composable
internal expect fun ResizeWindowForKeyboard()
