package zed.rainxch.tweaks.presentation.mirror

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.model.mirror.MirrorConfig
import zed.rainxch.core.domain.model.mirror.MirrorPreference
import zed.rainxch.core.domain.model.mirror.fastestReleaseMirror
import zed.rainxch.core.domain.network.MirrorConnectionTester
import zed.rainxch.core.domain.network.MirrorTestOutcome
import zed.rainxch.core.domain.repository.MirrorRepository
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.mirror_custom_validation_https
import zed.rainxch.githubstore.core.presentation.res.mirror_custom_validation_template

class MirrorPickerViewModel(
    private val mirrorRepository: MirrorRepository,
    private val connectionTester: MirrorConnectionTester,
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
                _state.update { it.withCatalog(catalog, pref, latencies) }
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
            is MirrorPickerAction.OnSelectMirror -> {
                selectMirror(action.mirrorId)
            }

            MirrorPickerAction.OnCustomMirrorClicked -> {
                _state.update {
                    it.copy(
                        isCustomDialogVisible = true,
                        customDraft = "",
                        customDraftError = null,
                    )
                }
            }

            is MirrorPickerAction.OnCustomDraftChanged -> {
                updateDraft(action.value)
            }

            MirrorPickerAction.OnCustomMirrorConfirm -> {
                confirmCustom()
            }

            MirrorPickerAction.OnCustomMirrorDismiss -> {
                _state.update { it.copy(isCustomDialogVisible = false) }
            }

            MirrorPickerAction.OnMeasureLatencies -> {
                measureLatencies(force = true)
            }

            MirrorPickerAction.OnAutoPickFastest -> {
                pickFastest()
            }

            MirrorPickerAction.OnTestConnection -> {
                runTest()
            }

            MirrorPickerAction.OnRefreshCatalog -> {
                refresh()
            }

            MirrorPickerAction.OnDeployYourOwnClicked -> {
                viewModelScope.launch {
                    _events.send(MirrorPickerEvent.OpenUrl("https://github.com/hunshcn/gh-proxy"))
                }
            }
        }
    }

    private fun selectMirror(mirrorId: String) {
        viewModelScope.launch {
            mirrorRepository.setPreference(preferenceFor(mirrorId))
        }
    }

    private fun preferenceFor(mirrorId: String): MirrorPreference =
        if (mirrorId == DIRECT_MIRROR_ID) {
            MirrorPreference.Direct
        } else {
            MirrorPreference.Selected(mirrorId)
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
                    customDraftError = null,
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
            val fastest = fastestReleaseMirror(mirrorRepository.observeCatalog().first(), measured)
            _state.update { it.copy(isMeasuringLatencies = false) }
            if (fastest == null) {
                _events.send(MirrorPickerEvent.NoMirrorResponded)
                return@launch
            }
            mirrorRepository.setPreference(preferenceFor(fastest.first.id))
            _events.send(
                MirrorPickerEvent.FastestMirrorChosen(
                    displayName = fastest.first.name,
                    latencyMs = fastest.second,
                ),
            )
        }
    }

    private fun runTest() {
        viewModelScope.launch {
            _state.update { it.copy(isTesting = true, testResult = null) }
            val result = connectionTester.test(state.value.selectedTemplate()).toTestResult()
            _state.update { it.copy(isTesting = false, testResult = result) }
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            mirrorRepository.refreshCatalog()
            _state.update { it.copy(isRefreshing = false) }
        }
    }

    private fun MirrorPickerState.withCatalog(
        catalog: List<MirrorConfig>,
        pref: MirrorPreference,
        latencies: Map<String, Int>,
    ): MirrorPickerState =
        copy(
            mirrors = catalog.map { it.toUi() }.toImmutableList(),
            measuredLatencies = latencies.toImmutableMap(),
            fastestMirrorId = fastestReleaseMirror(catalog, latencies)?.first?.id,
            selectedMirrorId =
                when (pref) {
                    MirrorPreference.Direct -> DIRECT_MIRROR_ID
                    is MirrorPreference.Selected -> pref.id
                    is MirrorPreference.Custom -> null
                },
            customTemplate = (pref as? MirrorPreference.Custom)?.template,
        )

    private fun MirrorPickerState.selectedTemplate(): String? =
        when {
            customTemplate != null -> customTemplate
            selectedMirrorId == DIRECT_MIRROR_ID -> null
            else -> mirrors.firstOrNull { it.id == selectedMirrorId }?.urlTemplate
        }

    private fun MirrorTestOutcome.toTestResult(): TestResult =
        when (this) {
            is MirrorTestOutcome.Success -> TestResult.Success(latencyMs)
            is MirrorTestOutcome.HttpError -> TestResult.HttpError(code)
            MirrorTestOutcome.Timeout -> TestResult.Timeout
            MirrorTestOutcome.DnsFailure -> TestResult.DnsFailure
            MirrorTestOutcome.Failed -> TestResult.Other
        }
}
