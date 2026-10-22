package zed.rainxch.tweaks.presentation.hosttokens

import zed.rainxch.core.domain.model.account.HostToken

sealed interface HostTokensEvent {
    data class Message(
        val text: String,
    ) : HostTokensEvent

    data class TokenDeletedWithUndo(
        val deleted: HostToken,
    ) : HostTokensEvent

    data class OpenUrl(
        val url: String,
    ) : HostTokensEvent
}
