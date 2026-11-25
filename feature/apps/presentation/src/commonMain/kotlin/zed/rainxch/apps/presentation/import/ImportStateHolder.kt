package zed.rainxch.apps.presentation.import

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

// The import screen's one state and event channel, shared by the ViewModel and the steps it hands actions to.
internal class ImportStateHolder(
    val scope: CoroutineScope,
) {
    val mutableState = MutableStateFlow(ExternalImportState())
    private val eventChannel = Channel<ExternalImportEvent>(capacity = Channel.BUFFERED)

    val state: StateFlow<ExternalImportState> = mutableState
    val events: Flow<ExternalImportEvent> = eventChannel.receiveAsFlow()

    val current: ExternalImportState get() = mutableState.value

    inline fun update(transform: (ExternalImportState) -> ExternalImportState) {
        mutableState.update(transform)
    }

    suspend fun send(event: ExternalImportEvent) {
        eventChannel.send(event)
    }
}
