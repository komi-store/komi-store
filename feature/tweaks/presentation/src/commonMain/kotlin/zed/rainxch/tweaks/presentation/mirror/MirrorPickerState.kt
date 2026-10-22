package zed.rainxch.tweaks.presentation.mirror

import org.jetbrains.compose.resources.StringResource
import zed.rainxch.core.domain.model.mirror.MirrorConfig
import zed.rainxch.core.domain.model.mirror.MirrorPreference

data class MirrorPickerState(
    val mirrors: List<MirrorConfig> = emptyList(),
    val measuredLatencies: Map<String, Int> = emptyMap(),
    val isMeasuringLatencies: Boolean = false,
    val preference: MirrorPreference = MirrorPreference.Direct,
    val isCustomDialogVisible: Boolean = false,
    val customDraft: String = "",
    val customDraftError: StringResource? = null,
    val isTesting: Boolean = false,
    val testResult: TestResult? = null,
    val isRefreshing: Boolean = false,
)
