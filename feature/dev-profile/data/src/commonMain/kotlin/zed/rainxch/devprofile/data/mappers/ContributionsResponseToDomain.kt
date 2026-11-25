package zed.rainxch.devprofile.data.mappers

import zed.rainxch.devprofile.data.dto.ContributionsResponse
import zed.rainxch.devprofile.domain.model.ContributionCalendar
import zed.rainxch.devprofile.domain.model.ContributionDay

fun ContributionsResponse.toDomain(): ContributionCalendar =
    ContributionCalendar(
        totalLastYear = total["lastYear"] ?: total.values.firstOrNull() ?: 0,
        days = contributions.map { ContributionDay(date = it.date, count = it.count, level = it.level) },
    )
