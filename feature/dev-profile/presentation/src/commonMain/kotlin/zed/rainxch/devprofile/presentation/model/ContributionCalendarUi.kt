package zed.rainxch.devprofile.presentation.model

import kotlinx.collections.immutable.ImmutableList

// One activity level per day; firstDayOfWeek is the first day's row in its week column, 0 for Sunday.
data class ContributionCalendarUi(
    val totalLastYear: Int,
    val firstDayOfWeek: Int,
    val levels: ImmutableList<Int>,
)
