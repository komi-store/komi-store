package zed.rainxch.core.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Stable
class TapCooldown internal constructor(
    private val scope: CoroutineScope,
    private val duration: Duration,
) {
    var active by mutableStateOf(false)
        private set

    fun run(action: () -> Unit) {
        if (active) return
        active = true
        action()
        scope.launch {
            delay(duration)
            active = false
        }
    }
}

@Composable
fun rememberTapCooldown(duration: Duration = 1.seconds): TapCooldown {
    val scope = rememberCoroutineScope()
    return remember(scope, duration) { TapCooldown(scope, duration) }
}
