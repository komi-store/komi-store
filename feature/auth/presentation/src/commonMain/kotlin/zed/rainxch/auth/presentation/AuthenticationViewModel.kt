package zed.rainxch.auth.presentation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.auth.domain.repository.AuthenticationRepository
import zed.rainxch.auth.presentation.handlers.AuthSavedState
import zed.rainxch.auth.presentation.handlers.AuthStore
import zed.rainxch.auth.presentation.handlers.DeviceFlowHandler
import zed.rainxch.auth.presentation.handlers.PatHandler
import zed.rainxch.auth.presentation.handlers.WebAuthHandler
import zed.rainxch.auth.presentation.model.AuthLoginState
import zed.rainxch.core.domain.helpers.BrowserHelper
import zed.rainxch.core.domain.helpers.ClipboardHelper
import zed.rainxch.core.domain.logging.KomiStoreLogger

class AuthenticationViewModel(
    private val authenticationRepository: AuthenticationRepository,
    browserHelper: BrowserHelper,
    clipboardHelper: ClipboardHelper,
    private val scope: CoroutineScope,
    logger: KomiStoreLogger,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private var hasLoadedInitialData = false

    private val _state: MutableStateFlow<AuthenticationState> =
        MutableStateFlow(AuthenticationState())

    private val _events = Channel<AuthenticationEvents>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val store = AuthStore(_state, _events, viewModelScope)
    private val savedState = AuthSavedState(savedStateHandle)

    private val deviceFlow =
        DeviceFlowHandler(
            store = store,
            repository = authenticationRepository,
            browserHelper = browserHelper,
            clipboardHelper = clipboardHelper,
            savedState = savedState,
            logger = logger,
        )

    private val webAuth =
        WebAuthHandler(
            store = store,
            repository = authenticationRepository,
            browserHelper = browserHelper,
            savedState = savedState,
            logger = logger,
        )

    private val pat =
        PatHandler(
            store = store,
            repository = authenticationRepository,
            browserHelper = browserHelper,
            logger = logger,
        )

    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    observeAccessToken()
                    deviceFlow.restore()
                    webAuth.observeDeepLinks()
                    hasLoadedInitialData = true
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = AuthenticationState(),
            )

    fun onAction(action: AuthenticationAction) {
        when (action) {
            is AuthenticationAction.DeviceFlow -> {
                deviceFlow.onAction(action)
            }

            is AuthenticationAction.WebAuth -> {
                webAuth.onAction(action)
            }

            is AuthenticationAction.Pat -> {
                pat.onAction(action)
            }

            AuthenticationAction.MarkLoggedIn -> {
                _state.update { it.copy(loginState = AuthLoginState.LoggedIn) }
            }

            AuthenticationAction.MarkLoggedOut -> {
                _state.update { it.copy(loginState = AuthLoginState.LoggedOut) }
            }

            is AuthenticationAction.OnInfo -> {
                _state.update { it.copy(info = action.message) }
            }

            AuthenticationAction.SkipLogin -> {
                store.navigateToMain()
            }

            AuthenticationAction.OpenAdvancedAuth -> {
                _state.update { it.copy(isAdvancedAuthVisible = true) }
            }

            AuthenticationAction.DismissAdvancedAuth -> {
                _state.update { it.copy(isAdvancedAuthVisible = false) }
            }
        }
    }

    private fun observeAccessToken() {
        scope.launch {
            authenticationRepository.accessTokenFlow.collect { token ->
                _state.update {
                    it.copy(
                        loginState =
                            if (token.isNullOrEmpty()) {
                                AuthLoginState.LoggedOut
                            } else {
                                _events.trySend(AuthenticationEvents.OnNavigateToMain)
                                AuthLoginState.LoggedIn
                            },
                    )
                }
            }
        }
    }

    override fun onCleared() {
        deviceFlow.stop()
    }
}
