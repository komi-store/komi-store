package zed.rainxch.devprofile.domain.model

data class ContributionCalendar(
    val totalLastYear: Int,
    val days: List<ContributionDay>,
)
