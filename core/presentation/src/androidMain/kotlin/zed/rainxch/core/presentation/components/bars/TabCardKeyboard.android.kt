package zed.rainxch.core.presentation.components.bars

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

// Two tab cards are on screen while the tabs slide, so the mode changes with the first card and
// goes back with the last.
private var cardsShown = 0
private var previousMode = 0

@Composable
internal actual fun ResizeWindowForKeyboard() {
    val window = LocalContext.current.findActivity()?.window ?: return
    DisposableEffect(window) {
        if (cardsShown++ == 0) {
            previousMode = window.attributes.softInputMode
            // Deprecated for windows that fit system windows; this one draws edge to edge, where
            // it only stops the pan and leaves the keyboard to the insets.
            @Suppress("DEPRECATION")
            window.setSoftInputMode(
                previousMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv() or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE,
            )
        }
        onDispose {
            if (--cardsShown == 0) window.setSoftInputMode(previousMode)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
