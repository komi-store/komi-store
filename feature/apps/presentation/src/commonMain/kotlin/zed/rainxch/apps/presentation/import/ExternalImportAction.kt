package zed.rainxch.apps.presentation.import

import zed.rainxch.apps.presentation.import.model.RepoSuggestionUi

sealed interface ExternalImportAction {
    sealed interface Scan : ExternalImportAction

    sealed interface Review : ExternalImportAction

    sealed interface Search : ExternalImportAction

    sealed interface Summary : ExternalImportAction

    data object OnStart : Scan

    data object OnRequestPermission : Scan

    data class OnPermissionGranted(
        val sdkInt: Int?,
    ) : Scan

    data class OnPermissionDenied(
        val sdkInt: Int?,
    ) : Scan

    data class OnSkipCard(
        val packageName: String,
    ) : Review

    data class OnSkipForever(
        val packageName: String,
    ) : Review

    data object OnSkipRemaining : Review

    data class OnPickSuggestion(
        val packageName: String,
        val suggestion: RepoSuggestionUi,
    ) : Review

    data class OnLinkCard(
        val packageName: String,
    ) : Review

    data class OnToggleCardExpanded(
        val packageName: String,
    ) : Review

    data class OnSearchOverrideChanged(
        val packageName: String,
        val query: String,
    ) : Search

    data class OnSearchOverrideSubmit(
        val packageName: String,
    ) : Search

    data object OnUndoLast : Review

    data object OnExit : ExternalImportAction

    data object OnDismissCompletionToast : Summary

    data object OnAutoSummaryContinue : Summary

    data object OnAutoSummaryUndoAll : Summary

    data object OnAddManually : ExternalImportAction

    data object OnSkipLongScan : Scan
}
