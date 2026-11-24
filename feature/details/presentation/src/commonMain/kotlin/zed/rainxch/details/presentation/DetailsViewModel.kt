package zed.rainxch.details.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.utils.toDiscoveryPlatform
import zed.rainxch.details.presentation.handlers.DetailsAppCoordinator
import zed.rainxch.details.presentation.handlers.DetailsPageCoordinator
import zed.rainxch.details.presentation.handlers.DetailsSession

class DetailsViewModel(
    args: DetailsArgs,
    platform: Platform,
    private val page: DetailsPageCoordinator,
    private val app: DetailsAppCoordinator,
) : ViewModel() {
    private val session =
        DetailsSession(
            args = args,
            platform = platform,
            scope = viewModelScope,
            initialState = RawDetailsState(devicePlatform = platform.toDiscoveryPlatform()),
        )

    val state: StateFlow<DetailsState> =
        session.state
            .onStart {
                if (!session.hasLoadedInitialData) {
                    page.start(session)
                    app.start(session)
                    session.hasLoadedInitialData = true
                }
            }.map { it.toView() }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                DetailsState(),
            )

    val events = session.events.receiveAsFlow()

    init {
        app.observe(session)
        page.observe(session)
    }

    fun onAction(action: DetailsAction) {
        when (action) {
            is DetailsAction.Load -> page.onLoadAction(session, action)

            is DetailsAction.Release -> page.picker.onAction(session, action)

            is DetailsAction.Content -> page.content.onAction(session, action)

            is DetailsAction.Install -> app.installs.onAction(session, action)

            is DetailsAction.Handoff -> app.handoffs.onAction(session, action)

            is DetailsAction.App -> app.app.onAction(session, action)

            is DetailsAction.Repo -> app.repo.onAction(session, action)

            // DetailsRoot answers these before they reach the ViewModel.
            is DetailsAction.Navigation -> Unit
        }
    }

    override fun onCleared() {
        app.onCleared(session)
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
