package zed.rainxch.recentlyviewed.presentation.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

enum class HistoryDay {
    Today,
    Yesterday,
    ThisWeek,
    Earlier,
}

@Immutable
data class HistorySection(
    val day: HistoryDay,
    val repos: ImmutableList<RecentlyViewedRepo>,
)
