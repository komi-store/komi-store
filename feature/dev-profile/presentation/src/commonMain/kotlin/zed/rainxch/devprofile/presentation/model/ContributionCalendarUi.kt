package zed.rainxch.devprofile.presentation.model

import kotlinx.collections.immutable.ImmutableList

// One activity level per day; firstDayOfWeek places the first day in the Sunday-first week column.
data class ContributionCalendarUi(
    val totalLastYear: Int,
    val firstDayOfWeek: Int,
    val levels: ImmutableList<Int>,
)
