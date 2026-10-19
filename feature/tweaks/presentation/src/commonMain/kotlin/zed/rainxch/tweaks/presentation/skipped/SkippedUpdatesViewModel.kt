package zed.rainxch.tweaks.presentation.skipped

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.repository.InstalledAppsRepository

class SkippedUpdatesViewModel(
    private val installedAppsRepository: InstalledAppsRepository,
    private val logger: KomiStoreLogger,
) : ViewModel() {
    private val _state = MutableStateFlow(SkippedUpdatesState())
    val state = _state.asStateFlow()

    private val _events = Channel<SkippedUpdatesEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        installedAppsRepository
            .getAppsWithSkippedReleaseTag()
            .onEach { apps ->
                val items =
                    apps.mapNotNull { app ->
                        val tag = app.skippedReleaseTag ?: return@mapNotNull null
                        SkippedAppUi(
                            packageName = app.packageName,
                            appName = app.appName,
                            skippedTag = tag,
                            installedVersion = app.installedVersion,
                        )
                    }
                _state.value =
                    SkippedUpdatesState(
                        isLoading = false,
                        items = items.toImmutableList(),
                    )
            }.launchIn(viewModelScope)
    }

    fun onAction(action: SkippedUpdatesAction) {
        when (action) {
            is SkippedUpdatesAction.OnUnskip -> unskip(action.packageName)
        }
    }

    private fun unskip(packageName: String) {
        val appName =
            _state.value.items
                .firstOrNull { it.packageName == packageName }
                ?.appName
        viewModelScope.launch {
            try {
                installedAppsRepository.setSkippedReleaseTag(packageName, null)
                installedAppsRepository.checkForUpdates(packageName)
                _events.send(SkippedUpdatesEvent.Unskipped(appName.orEmpty()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("SkippedUpdatesViewModel: failed to unskip $packageName", e)
                _events.send(SkippedUpdatesEvent.Failure)
            }
        }
    }
}
