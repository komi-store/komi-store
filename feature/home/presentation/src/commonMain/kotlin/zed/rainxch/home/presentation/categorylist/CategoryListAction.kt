package zed.rainxch.home.presentation.categorylist

sealed interface CategoryListAction {
    data object OnNavigateBack : CategoryListAction

    data object OnLoadMore : CategoryListAction

    data object OnRefresh : CategoryListAction

    data class OnRepoClick(
        val repoId: Long,
    ) : CategoryListAction
}
