package zed.rainxch.home.presentation.categorylist

sealed interface CategoryListEvent {
    data class NavigateToDetails(
        val repoId: Long,
    ) : CategoryListEvent
}
