package zed.rainxch.tweaks.presentation.mirror

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.getString
import zed.rainxch.core.data.network.MirrorRewriter
import zed.rainxch.core.domain.model.mirror.MirrorConfig
import zed.rainxch.core.domain.model.mirror.MirrorPreference
import zed.rainxch.core.domain.model.mirror.TrafficKind
import zed.rainxch.core.domain.repository.MirrorRepository
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.error_unknown
import zed.rainxch.githubstore.core.presentation.res.mirror_custom_validation_https
import zed.rainxch.githubstore.core.presentation.res.mirror_custom_validation_template
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class MirrorPickerViewModel(
    private val mirrorRepository: MirrorRepository,
    private val testHttpClient: HttpClient,
) : ViewModel() {
    private val _state = MutableStateFlow(MirrorPickerState())
    val state = _state.asStateFlow()

    private val _events = Channel<MirrorPickerEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            combine(
                mirrorRepository.observeCatalog(),
                mirrorRepository.observePreference(),
                mirrorRepository.observeMeasuredLatencies(),
            ) { catalog, pref, latencies ->
                Triple(catalog, pref, latencies)
            }.collect { (catalog, pref, latencies) ->
                _state.update {
                    it.copy(mirrors = catalog, preference = pref, measuredLatencies = latencies)
                }
            }
        }
        viewModelScope.launch {
            mirrorRepository.observeRemovedNotices().collect { notice ->
                _events.send(MirrorPickerEvent.MirrorRemovedNotice(notice.displayName))
            }
        }
        measureLatencies()
    }

    fun onAction(action: MirrorPickerAction) {
        when (action) {
            MirrorPickerAction.OnNavigateBack -> {}
            is MirrorPickerAction.OnSelectMirror -> selectMirror(action.mirror)
            MirrorPickerAction.OnCustomMirrorClicked ->
                _state.update {
                    it.copy(
                        isCustomDialogVisible = true,
                        customDraft = "",
                        customDraftError = null
                    )
                }

            is MirrorPickerAction.OnCustomDraftChanged -> updateDraft(action.value)
            MirrorPickerAction.OnCustomMirrorConfirm -> confirmCustom()
            MirrorPickerAction.OnCustomMirrorDismiss ->
                _state.update { it.copy(isCustomDialogVisible = false) }

            MirrorPickerAction.OnMeasureLatencies -> measureLatencies(force = true)
            MirrorPickerAction.OnAutoPickFastest -> pickFastest()
            MirrorPickerAction.OnTestConnection -> runTest()
            MirrorPickerAction.OnRefreshCatalog -> refresh()
            MirrorPickerAction.OnDeployYourOwnClicked ->
                viewModelScope.launch {
                    _events.send(MirrorPickerEvent.OpenUrl("https://github.com/hunshcn/gh-proxy"))
                }
        }
    }

    private fun selectMirror(mirror: MirrorConfig) {
        viewModelScope.launch {
            val pref = if (mirror.id == "direct") {
                MirrorPreference.Direct
            } else MirrorPreference.Selected(mirror.id)
            mirrorRepository.setPreference(pref)
        }
    }

    private fun updateDraft(value: String) {
        val error =
            when {
                value.isBlank() -> null
                !value.startsWith("https://") -> Res.string.mirror_custom_validation_https
                value.split("{url}").size - 1 != 1 -> Res.string.mirror_custom_validation_template
                else -> null
            }
        _state.update { it.copy(customDraft = value, customDraftError = error) }
    }

    private fun confirmCustom() {
        val draft = state.value.customDraft
        val error = state.value.customDraftError
        if (draft.isBlank() || error != null) return
        viewModelScope.launch {
            mirrorRepository.setPreference(MirrorPreference.Custom(draft))
            _state.update {
                it.copy(
                    isCustomDialogVisible = false,
                    customDraft = "",
                    customDraftError = null
                )
            }
        }
    }

    private fun measureLatencies(force: Boolean = false) {
        viewModelScope.launch {
            if (!force && state.value.measuredLatencies.isNotEmpty()) return@launch
            _state.update { it.copy(isMeasuringLatencies = true) }
            mirrorRepository.measureLatencies()
            _state.update { it.copy(isMeasuringLatencies = false) }
        }
    }

    private fun pickFastest() {
        viewModelScope.launch {
            _state.update { it.copy(isMeasuringLatencies = true) }
            val measured = mirrorRepository.measureLatencies().getOrDefault(emptyMap())
            val fastest =
                state.value.mirrors
                    .filter { TrafficKind.RELEASE_ASSET in it.trafficKinds }
                    .mapNotNull { mirror -> measured[mirror.id]?.let { mirror to it } }
                    .minByOrNull { it.second }
            _state.update { it.copy(isMeasuringLatencies = false) }
            if (fastest == null) {
                _events.send(MirrorPickerEvent.NoMirrorResponded)
                return@launch
            }
            mirrorRepository.setPreference(MirrorPreference.Selected(fastest.first.id))
            _events.send(
                MirrorPickerEvent.FastestMirrorChosen(
                    displayName = fastest.first.name,
                    latencyMs = fastest.second,
                )
            )
        }
    }

    private fun runTest() {
        viewModelScope.launch {
            _state.update { it.copy(isTesting = true, testResult = null) }
            val template =
                when (val pref = state.value.preference) {
                    MirrorPreference.Direct -> null
                    is MirrorPreference.Custom -> pref.template
                    is MirrorPreference.Selected ->
                        state.value.mirrors.firstOrNull { it.id == pref.id }?.urlTemplate
                }
            val targetUrl = MirrorRewriter.probeUrl(template)

            val result = withTimeoutOrNull(5_000L.milliseconds) {
                runCatching {
                    val mark = TimeSource.Monotonic.markNow()
                    val response = testHttpClient.get(targetUrl)
                    val elapsedMs = mark.elapsedNow().inWholeMilliseconds
                    response.status.value to elapsedMs
                }
            }

            val testResult: TestResult = when {
                result == null -> TestResult.Timeout

                result.isSuccess -> {
                    val (status, ms) = result.getOrThrow()
                    if (status in 200..299) {
                        TestResult.Success(ms)
                    } else TestResult.HttpError(status)
                }

                result.exceptionOrNull() is UnresolvedAddressException -> TestResult.DnsFailure

                else -> TestResult.Other(
                    result.exceptionOrNull()?.message ?: getString(Res.string.error_unknown)
                )
            }

            _state.update { it.copy(isTesting = false, testResult = testResult) }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            mirrorRepository.refreshCatalog()
            _state.update { it.copy(isRefreshing = false) }
        }
    }
}
