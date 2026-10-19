package zed.rainxch.apps.presentation.import

sealed interface ExternalImportEvent {
    data class ShowError(
        val message: String,
    ) : ExternalImportEvent

    data class NavigateToDetails(
        val repoId: Long,
    ) : ExternalImportEvent

    data object NavigateBack : ExternalImportEvent

    data class ShowUndoSnackbar(
        val message: String,
    ) : ExternalImportEvent

    data object NavigateBackAndOpenManualLink : ExternalImportEvent
}
