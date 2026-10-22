package zed.rainxch.core.presentation.components.refresh

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import zed.rainxch.core.presentation.utils.isPullToRefreshSupported

@Composable
fun Modifier.drivesPullToRefresh(): Modifier {
    val noOpScroll = rememberScrollableState { 0f }
    return if (isPullToRefreshSupported()) {
        scrollable(state = noOpScroll, orientation = Orientation.Vertical)
    } else {
        this
    }
}
