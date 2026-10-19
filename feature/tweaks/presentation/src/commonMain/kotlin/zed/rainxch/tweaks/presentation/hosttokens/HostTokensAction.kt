package zed.rainxch.tweaks.presentation.hosttokens

import zed.rainxch.core.domain.model.account.ForgeKind
import zed.rainxch.core.domain.model.account.HostToken

sealed interface HostTokensAction {
    data object OnNavigateBack : HostTokensAction

    data object OnAddClicked : HostTokensAction

    data object OnAddDismiss : HostTokensAction

    data class OnPickPresetForge(
        val kind: ForgeKind,
    ) : HostTokensAction

    data object OnPickOtherForge : HostTokensAction

    data class OnOpenTokenCreationPage(
        val kind: ForgeKind,
    ) : HostTokensAction

    data class OnReplaceToken(
        val existing: HostToken,
    ) : HostTokensAction

    data class OnEditLabel(
        val existing: HostToken,
    ) : HostTokensAction

    data class OnDraftHostChanged(
        val value: String,
    ) : HostTokensAction

    data class OnDraftTokenChanged(
        val value: String,
    ) : HostTokensAction

    data class OnDraftDisplayNameChanged(
        val value: String,
    ) : HostTokensAction

    data object OnAddConfirm : HostTokensAction

    data class OnValidate(
        val host: String,
    ) : HostTokensAction

    data class OnDelete(
        val host: String,
    ) : HostTokensAction

    data object OnUndoDelete : HostTokensAction

    data object OnDismissUndoDelete : HostTokensAction
}
