package zed.rainxch.tweaks.presentation.hosttokens

import zed.rainxch.core.domain.model.account.HostToken

sealed interface DraftMode {
    data object Closed : DraftMode

    data object Picker : DraftMode

    data class Compose(
        val replacingExisting: HostToken? = null,
    ) : DraftMode
}
