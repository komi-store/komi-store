package zed.rainxch.tweaks.presentation.mirror

import zed.rainxch.core.domain.model.mirror.MirrorConfig

sealed interface MirrorPickerAction {
    data object OnNavigateBack : MirrorPickerAction

    data class OnSelectMirror(val mirror: MirrorConfig) : MirrorPickerAction

    data object OnCustomMirrorClicked : MirrorPickerAction

    data class OnCustomDraftChanged(val value: String) : MirrorPickerAction

    data object OnCustomMirrorConfirm : MirrorPickerAction

    data object OnCustomMirrorDismiss : MirrorPickerAction

    data object OnMeasureLatencies : MirrorPickerAction

    data object OnAutoPickFastest : MirrorPickerAction

    data object OnTestConnection : MirrorPickerAction

    data object OnRefreshCatalog : MirrorPickerAction

    data object OnDeployYourOwnClicked : MirrorPickerAction
}
