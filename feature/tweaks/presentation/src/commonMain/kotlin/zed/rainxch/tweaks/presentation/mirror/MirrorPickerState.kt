package zed.rainxch.tweaks.presentation.mirror

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.jetbrains.compose.resources.StringResource

data class MirrorPickerState(
    val mirrors: ImmutableList<MirrorUi> = persistentListOf(),
    val measuredLatencies: ImmutableMap<String, Int> = persistentMapOf(),
    val fastestMirrorId: String? = null,
    val isMeasuringLatencies: Boolean = false,
    val selectedMirrorId: String? = DIRECT_MIRROR_ID,
    val customTemplate: String? = null,
    val isCustomDialogVisible: Boolean = false,
    val customDraft: String = "",
    val customDraftError: StringResource? = null,
    val isTesting: Boolean = false,
    val testResult: TestResult? = null,
    val isRefreshing: Boolean = false,
)

internal const val DIRECT_MIRROR_ID = "direct"
