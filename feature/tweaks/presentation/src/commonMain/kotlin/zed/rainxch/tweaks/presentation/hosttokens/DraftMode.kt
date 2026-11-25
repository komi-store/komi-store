package zed.rainxch.tweaks.presentation.hosttokens

sealed interface DraftMode {
    data object Closed : DraftMode

    data object Picker : DraftMode

    data class Compose(
        val replacingExisting: HostTokenUi? = null,
    ) : DraftMode
}
