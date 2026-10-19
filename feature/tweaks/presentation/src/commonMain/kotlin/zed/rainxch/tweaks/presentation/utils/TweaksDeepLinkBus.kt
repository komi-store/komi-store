package zed.rainxch.tweaks.presentation.utils

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object TweaksDeepLinkBus {
    private val _openFeedbackRequests =
        MutableSharedFlow<Unit>(
            replay = 1,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    val openFeedbackRequests: SharedFlow<Unit> = _openFeedbackRequests.asSharedFlow()

    fun requestOpenFeedback() {
        _openFeedbackRequests.tryEmit(Unit)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consume() {
        _openFeedbackRequests.resetReplayCache()
    }
}
