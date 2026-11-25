package zed.rainxch.auth.presentation.handlers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.auth.presentation.AuthenticationEvents
import zed.rainxch.auth.presentation.AuthenticationState

// The ViewModel's state, events and scope, handed to the handlers that split up its work.
internal class AuthStore(
    private val state: MutableStateFlow<AuthenticationState>,
    private val events: SendChannel<AuthenticationEvents>,
    private val scope: CoroutineScope,
) {
    val current: AuthenticationState get() = state.value

    fun update(transform: (AuthenticationState) -> AuthenticationState) = state.update(transform)

    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

    fun navigateToMain() {
        events.trySend(AuthenticationEvents.OnNavigateToMain)
    }
}
