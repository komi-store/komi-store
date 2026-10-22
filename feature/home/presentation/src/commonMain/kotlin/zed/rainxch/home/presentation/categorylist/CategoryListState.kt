package zed.rainxch.home.presentation.categorylist

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.home.domain.model.HomeCategory
import zed.rainxch.home.presentation.model.HomeRepoCardUi

data class CategoryListState(
    val category: HomeCategory = HomeCategory.HOT_RELEASE,
    val cards: ImmutableList<HomeRepoCardUi> = persistentListOf(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val errorMessage: String? = null,
    val isGridLayout: Boolean = false,
)
