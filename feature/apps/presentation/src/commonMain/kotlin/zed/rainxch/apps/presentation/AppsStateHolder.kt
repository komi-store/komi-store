package zed.rainxch.apps.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update

// The Library screen's one state and event channel, which AppsViewModel shares with the collaborators it hands actions to.
class AppsStateHolder internal constructor(
    val scope: CoroutineScope,
) {
    @PublishedApi
    internal val mutableState = MutableStateFlow(AppsState())
    private val eventChannel = Channel<AppsEvent>(capacity = Channel.BUFFERED)

    val state: StateFlow<AppsState> = mutableState
    val events: Flow<AppsEvent> = eventChannel.receiveAsFlow()

    val current: AppsState get() = mutableState.value

    inline fun update(transform: (AppsState) -> AppsState) {
        mutableState.update(transform)
    }

    suspend fun send(event: AppsEvent) {
        eventChannel.send(event)
    }
}
