package zed.rainxch.tweaks.presentation.mirror

sealed interface MirrorPickerEvent {
    data class MirrorRemovedNotice(val displayName: String) : MirrorPickerEvent

    data class FastestMirrorChosen(val displayName: String, val latencyMs: Int) : MirrorPickerEvent

    data object NoMirrorResponded : MirrorPickerEvent

    data class OpenUrl(val url: String) : MirrorPickerEvent
}
