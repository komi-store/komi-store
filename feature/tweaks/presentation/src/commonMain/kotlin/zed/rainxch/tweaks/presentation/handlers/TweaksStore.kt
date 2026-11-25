package zed.rainxch.tweaks.presentation.handlers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.tweaks.presentation.TweaksEvent
import zed.rainxch.tweaks.presentation.TweaksState

// The ViewModel's state, events and scope, handed to the handlers that split up its work.
internal class TweaksStore(
    private val state: MutableStateFlow<TweaksState>,
    private val events: SendChannel<TweaksEvent>,
    private val scope: CoroutineScope,
) {
    val current: TweaksState get() = state.value

    fun update(transform: (TweaksState) -> TweaksState) = state.update(transform)

    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

    suspend fun send(event: TweaksEvent) = events.send(event)

    fun <T> observe(
        flow: Flow<T>,
        reduce: TweaksState.(T) -> TweaksState,
    ): Job = launch { flow.collect { value -> state.update { it.reduce(value) } } }
}
