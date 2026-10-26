package zed.rainxch.core.presentation.components.bars

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

// Two tab cards show while the tabs slide: the first to appear sets the mode, the last to leave restores it.
private var cardsShown = 0
private var previousMode = 0

@Composable
internal actual fun ResizeWindowForKeyboard() {
    val window = LocalContext.current.findActivity()?.window ?: return
    DisposableEffect(window) {
        if (cardsShown++ == 0) {
            previousMode = window.attributes.softInputMode
            // Deprecated for windows that fit system windows; edge to edge it only stops the pan.
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
