package zed.rainxch.tweaks.presentation.feedback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.getOsVersion
import zed.rainxch.core.domain.getPlatform
import zed.rainxch.core.domain.getSystemLocaleTag
import zed.rainxch.core.domain.helpers.BrowserHelper
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.installation.InstallerType
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.repository.UserSessionRepository
import zed.rainxch.core.domain.system.AppVersionInfo
import zed.rainxch.tweaks.presentation.feedback.model.DiagnosticsInfo
import zed.rainxch.tweaks.presentation.feedback.model.FeedbackChannel
import zed.rainxch.tweaks.presentation.feedback.util.FeedbackComposer

class FeedbackViewModel(
    private val browserHelper: BrowserHelper,
    private val tweaksRepository: TweaksRepository,
    private val userSessionRepository: UserSessionRepository,
    private val appVersionInfo: AppVersionInfo,
    private val logger: KomiStoreLogger,
) : ViewModel() {
    private val _state = MutableStateFlow(FeedbackState())
    val state = _state.asStateFlow()

    private val _events = Channel<FeedbackEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            _state.update { it.copy(diagnostics = collectDiagnostics()) }
        }
    }

    fun onAction(action: FeedbackAction) {
        when (action) {
            is FeedbackAction.OnCategoryChange -> {
                _state.update { it.copy(category = action.category) }
            }

            is FeedbackAction.OnTopicChange -> {
                _state.update { it.copy(topic = action.topic) }
            }

            is FeedbackAction.FieldEdit -> {
                _state.update { it.withField(action) }
            }

            FeedbackAction.OnAttachDiagnosticsToggle -> {
                _state.update { it.copy(attachDiagnostics = !it.attachDiagnostics) }
            }

            FeedbackAction.OnSendViaEmail -> {
                send(FeedbackChannel.EMAIL)
            }

            FeedbackAction.OnSendViaGithub -> {
                send(FeedbackChannel.GITHUB)
            }

            FeedbackAction.OnDismiss -> {
                resetForm()
            }
        }
    }

    private fun send(channel: FeedbackChannel) {
        val current = _state.value
        if (!current.canSend) return
        _state.update { it.copy(isSending = true) }
        viewModelScope.launch {
            var failed = false
            val url = FeedbackComposer.composeUrl(current, channel)
            browserHelper.openUrl(url) { error ->
                failed = true
                logger.warn("FeedbackViewModel: couldn't open the $channel channel: $error")
                viewModelScope.launch {
                    _events.send(FeedbackEvent.OnSendError)
                }
            }

            delay(OPEN_FAILURE_GRACE_MS)
            _state.update { it.copy(isSending = false) }
            if (!failed) {
                _events.send(FeedbackEvent.OnSent(channel))
                resetForm()
            }
        }
    }

    private fun FeedbackState.withField(edit: FeedbackAction.FieldEdit): FeedbackState =
        when (edit) {
            is FeedbackAction.OnTitleChange -> copy(title = edit.value)
            is FeedbackAction.OnDescriptionChange -> copy(description = edit.value)
            is FeedbackAction.OnStepsToReproduceChange -> copy(stepsToReproduce = edit.value)
            is FeedbackAction.OnExpectedActualChange -> copy(expectedActual = edit.value)
            is FeedbackAction.OnUseCaseChange -> copy(useCase = edit.value)
            is FeedbackAction.OnProposedSolutionChange -> copy(proposedSolution = edit.value)
            is FeedbackAction.OnCurrentBehaviourChange -> copy(currentBehaviour = edit.value)
            is FeedbackAction.OnDesiredBehaviourChange -> copy(desiredBehaviour = edit.value)
        }

    private fun resetForm() {
        _state.update { previous ->
            FeedbackState(diagnostics = previous.diagnostics)
        }
    }

    private fun installerLabel(type: InstallerType): String =
        when (type) {
            InstallerType.DEFAULT -> "Default"
            InstallerType.SHIZUKU -> "Shizuku"
            InstallerType.ROOT -> "Root"
            InstallerType.DHIZUKU -> "Dhizuku"
            InstallerType.THIRD_PARTY -> "Third-party"
        }

    private suspend fun collectDiagnostics(): DiagnosticsInfo {
        val installerType = tweaksRepository.getInstallerType().first()
        val platform = getPlatform()
        val installerString =
            if (platform == Platform.ANDROID) installerLabel(installerType) else null
        val user = userSessionRepository.getUser().firstOrNull()
        val appLanguage = tweaksRepository.getAppLanguage().firstOrNull()
        val palette = tweaksRepository.getThemeColor().firstOrNull()
        val isDark = tweaksRepository.getIsDarkTheme().firstOrNull()
        val amoled = tweaksRepository.getAmoledTheme().firstOrNull() ?: false
        val themeMode =
            when {
                isDark == null -> "System"
                isDark && amoled -> "AMOLED"
                isDark -> "Dark"
                else -> "Light"
            }
        return DiagnosticsInfo(
            appVersion = appVersionInfo.versionName,
            platform = platform.displayName(),
            osVersion = getOsVersion(),
            locale = appLanguage ?: getSystemLocaleTag(),
            installerType = installerString,
            githubUsername = user?.username,
            themePalette = palette?.name ?: "Nord",
            themeMode = themeMode,
        )
    }

    private fun Platform.displayName(): String =
        when (this) {
            Platform.ANDROID -> "Android"
            Platform.WINDOWS -> "Windows"
            Platform.MACOS -> "macOS"
            Platform.LINUX -> "Linux"
        }

    private companion object {
        // The browser helper reports a failed open through its callback, so it gets a moment before the send counts.
        const val OPEN_FAILURE_GRACE_MS = 250L
    }
}
