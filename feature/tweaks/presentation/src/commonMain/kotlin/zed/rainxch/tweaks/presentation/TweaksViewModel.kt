package zed.rainxch.tweaks.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.tweaks.presentation.handlers.ConnectionHandler
import zed.rainxch.tweaks.presentation.handlers.DiagnosticsHandler
import zed.rainxch.tweaks.presentation.handlers.InstallsHandler
import zed.rainxch.tweaks.presentation.handlers.LookAndFeelHandler
import zed.rainxch.tweaks.presentation.handlers.PrivacyHandler
import zed.rainxch.tweaks.presentation.handlers.TranslationHandler
import zed.rainxch.tweaks.presentation.handlers.TweaksStore
import zed.rainxch.tweaks.presentation.utils.TweaksDeepLinkBus

class TweaksViewModel(
    private val lookAndFeel: LookAndFeelHandler,
    private val connection: ConnectionHandler,
    private val installs: InstallsHandler,
    private val translation: TranslationHandler,
    private val privacy: PrivacyHandler,
    private val diagnostics: DiagnosticsHandler,
) : ViewModel() {
    private var hasLoadedInitialData = false

    private val _state = MutableStateFlow(TweaksState())
    private val _events = Channel<TweaksEvent>(capacity = Channel.BUFFERED)
    private val store = TweaksStore(_state, _events, viewModelScope)

    val state =
        _state
            .onStart {
                if (!hasLoadedInitialData) {
                    lookAndFeel.observe(store)
                    connection.observe(store)
                    installs.observe(store)
                    translation.observe(store)
                    privacy.observe(store)
                    observeFeedbackDeepLink()
                    hasLoadedInitialData = true
                }
                privacy.refreshCacheSize(store)
                installs.evaluateBatteryOptimizationCard(store)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000L),
                initialValue = TweaksState(),
            )

    val events = _events.receiveAsFlow()

    fun onAction(action: TweaksAction) {
        when (action) {
            is TweaksAction.LookAndFeel -> lookAndFeel.onAction(store, action)
            is TweaksAction.Connection -> connection.onAction(store, action)
            is TweaksAction.Installs -> installs.onAction(store, action)
            is TweaksAction.Translation -> translation.onAction(store, action)
            is TweaksAction.Privacy -> privacy.onAction(store, action)
            TweaksAction.OnFeedbackClick -> _state.update { it.copy(isFeedbackSheetVisible = true) }
            TweaksAction.OnFeedbackDismiss -> _state.update { it.copy(isFeedbackSheetVisible = false) }
            is TweaksAction.OnDesktopSectionSelected -> _state.update { it.copy(desktopSection = action.section) }
            TweaksAction.OnShareDiagnosticLogs -> diagnostics.shareLogs(store)
        }
    }

    private fun observeFeedbackDeepLink() {
        viewModelScope.launch {
            TweaksDeepLinkBus.openFeedbackRequests.collect {
                _state.update { it.copy(isFeedbackSheetVisible = true) }
                TweaksDeepLinkBus.consume()
            }
        }
    }
}
