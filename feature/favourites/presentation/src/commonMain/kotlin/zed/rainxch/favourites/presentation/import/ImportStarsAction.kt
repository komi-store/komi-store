package zed.rainxch.favourites.presentation.import

sealed interface ImportStarsAction {
    data object OnNavigateBack : ImportStarsAction

    data class OnUsernameQueryChange(
        val query: String,
    ) : ImportStarsAction

    data object OnImportClick : ImportStarsAction

    data object OnResetImport : ImportStarsAction

    data class OnSearchChange(
        val query: String,
    ) : ImportStarsAction

    data object OnClearSearch : ImportStarsAction

    data class OnToggleFavourite(
        val candidate: ImportCandidateUi,
    ) : ImportStarsAction

    data object OnAddAll : ImportStarsAction

    data class OnCandidateClick(
        val candidate: ImportCandidateUi,
    ) : ImportStarsAction
}
