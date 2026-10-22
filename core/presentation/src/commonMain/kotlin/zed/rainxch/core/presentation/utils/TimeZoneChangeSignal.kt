package zed.rainxch.core.presentation.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object TimeZoneChangeSignal {
    private val _revision = MutableStateFlow(0)

    val revision: StateFlow<Int> = _revision.asStateFlow()

    internal fun onTimeZoneChanged() {
        _revision.update { it + 1 }
    }
}

@Composable
expect fun ObserveTimeZoneChanges()

@Composable
fun rememberTimeZoneRevision(): Int {
    val revision by TimeZoneChangeSignal.revision.collectAsState()
    return revision
}
