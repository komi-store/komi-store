package zed.rainxch.recentlyviewed.presentation

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.recentlyviewed.presentation.model.HistorySection

data class RecentlyViewedState(
    val sections: ImmutableList<HistorySection> = persistentListOf(),
    val isLoading: Boolean = false,
)
