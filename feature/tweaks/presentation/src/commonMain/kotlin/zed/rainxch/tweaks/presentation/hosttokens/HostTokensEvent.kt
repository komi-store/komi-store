package zed.rainxch.tweaks.presentation.hosttokens

sealed interface HostTokensEvent {
    data class Message(
        val text: String,
    ) : HostTokensEvent

    data class TokenDeletedWithUndo(
        val deleted: HostTokenUi,
    ) : HostTokensEvent

    data class OpenUrl(
        val url: String,
    ) : HostTokensEvent
}
