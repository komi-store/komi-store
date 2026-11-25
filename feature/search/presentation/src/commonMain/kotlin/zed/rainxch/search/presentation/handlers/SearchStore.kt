package zed.rainxch.search.presentation.handlers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.search.presentation.SearchEvent
import zed.rainxch.search.presentation.SearchState

// The ViewModel's state, events and scope, handed to the handlers that split up its work.
internal class SearchStore(
    private val state: MutableStateFlow<SearchState>,
    private val events: SendChannel<SearchEvent>,
    private val scope: CoroutineScope,
) {
    val current: SearchState get() = state.value

    fun update(transform: (SearchState) -> SearchState) = state.update(transform)

    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

    suspend fun send(event: SearchEvent) = events.send(event)

    fun <T> observe(
        flow: Flow<T>,
        reduce: SearchState.(T) -> SearchState,
    ): Job = launch { flow.collect { value -> state.update { it.reduce(value) } } }
}
