package zed.rainxch.apps.presentation.starred

sealed interface StarredPickerAction {
    data object OnNavigateBack : StarredPickerAction

    data object OnRetry : StarredPickerAction

    data object OnResume : StarredPickerAction

    data class OnSearchChange(
        val query: String,
    ) : StarredPickerAction

    data class OnSortRuleSelected(
        val rule: StarredPickerSortRule,
    ) : StarredPickerAction

    data class OnToggleWithoutApk(
        val show: Boolean,
    ) : StarredPickerAction

    data class OnCandidateClick(
        val candidate: StarredCandidateUi,
    ) : StarredPickerAction
}
