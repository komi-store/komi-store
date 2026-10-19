package zed.rainxch.apps.presentation.import.util

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

@Composable
actual fun rememberSystemReducedMotion(): Boolean {
    val context = LocalContext.current

    return remember(context) {
        val scale =
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        scale == 0f
    }
}
