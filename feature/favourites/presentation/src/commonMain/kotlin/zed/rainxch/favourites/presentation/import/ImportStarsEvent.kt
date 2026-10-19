package zed.rainxch.favourites.presentation.import

sealed interface ImportStarsEvent {
    data object NavigateBack : ImportStarsEvent

    data class NavigateToDetails(
        val repoId: Long,
        val owner: String,
        val repo: String,
    ) : ImportStarsEvent
}
