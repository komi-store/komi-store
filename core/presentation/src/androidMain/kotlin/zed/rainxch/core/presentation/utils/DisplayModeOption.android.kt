package zed.rainxch.core.presentation.utils

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

fun displayModeOptions(context: Context): List<DisplayModeOption> {
    val display =
        context
            .getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY)
            ?: return emptyList()
    return display.supportedModes.map {
        DisplayModeOption(it.modeId, it.physicalWidth, it.physicalHeight, it.refreshRate)
    }
}

@Composable
actual fun rememberDisplayModeOptions(): List<DisplayModeOption> {
    val context = LocalContext.current
    return remember(context) { displayModeOptions(context) }
}
