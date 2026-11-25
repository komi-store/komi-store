package zed.rainxch.details.presentation.handlers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.details.presentation.DetailsArgs
import zed.rainxch.details.presentation.DetailsEvent
import zed.rainxch.details.presentation.RawDetailsState
import java.util.concurrent.atomic.AtomicBoolean

// One open Details page: its state, events and the jobs its handlers share, scoped to the ViewModel.
internal class DetailsSession(
    val args: DetailsArgs,
    val platform: Platform,
    val scope: CoroutineScope,
    initialState: RawDetailsState,
) {
    val state = MutableStateFlow(initialState)
    val events = Channel<DetailsEvent>(capacity = Channel.BUFFERED)

    val current: RawDetailsState get() = state.value

    var hasLoadedInitialData = false

    // Set once the user picks a release, asset, channel or installed app; an update check then leaves the selection alone.
    var hasUserPickedRelease = false

    var currentAssetName: String? = null
    var aboutTranslationJob: Job? = null
    var whatsNewTranslationJob: Job? = null
    var reviewsJob: Job? = null
    val rateLimited = AtomicBoolean(false)

    val localDownloadJob = MutableStateFlow<Job?>(null)

    var currentDownloadJob: Job?
        get() = localDownloadJob.value
        set(value) {
            localDownloadJob.value = value
            value?.invokeOnCompletion { localDownloadJob.compareAndSet(value, null) }
        }

    fun update(transform: (RawDetailsState) -> RawDetailsState) = state.update(transform)

    fun launch(block: suspend CoroutineScope.() -> Unit): Job = scope.launch(block = block)

    suspend fun send(event: DetailsEvent) = events.send(event)

    suspend fun message(text: String) = events.send(DetailsEvent.OnMessage(text))
}
