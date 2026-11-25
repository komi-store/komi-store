package zed.rainxch.devprofile.presentation.mappers

import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import zed.rainxch.devprofile.domain.model.ContributionCalendar
import zed.rainxch.devprofile.domain.model.DeveloperProfile
import zed.rainxch.devprofile.domain.model.DeveloperRepository
import zed.rainxch.devprofile.presentation.model.ContributionCalendarUi
import zed.rainxch.devprofile.presentation.model.DeveloperProfileUi
import zed.rainxch.devprofile.presentation.model.DeveloperRepositoryUi

private const val DAYS_PER_WEEK = 7

internal fun DeveloperProfile.toUi(): DeveloperProfileUi =
    DeveloperProfileUi(
        login = login,
        name = name,
        avatarUrl = avatarUrl,
        bio = bio,
        company = company,
        location = location,
        blog = blog,
        twitterUsername = twitterUsername,
        publicRepos = publicRepos,
        followers = followers,
        following = following,
        htmlUrl = htmlUrl,
        isOrganization = isOrganization,
    )

internal fun DeveloperRepository.toUi(): DeveloperRepositoryUi =
    DeveloperRepositoryUi(
        id = id,
        name = name,
        fullName = fullName,
        description = description,
        htmlUrl = htmlUrl,
        stargazersCount = stargazersCount,
        forksCount = forksCount,
        openIssuesCount = openIssuesCount,
        language = language,
        hasReleases = hasReleases,
        hasInstallableAssets = hasInstallableAssets,
        isInstalled = isInstalled,
        isFavorite = isFavorite,
        latestVersion = latestVersion,
        latestReleaseAt = latestReleaseAt,
        updatedAt = updatedAt,
    )

internal fun ContributionCalendar.toUi(): ContributionCalendarUi =
    ContributionCalendarUi(
        totalLastYear = totalLastYear,
        firstDayOfWeek = days.firstOrNull()?.let { sundayFirstIndex(it.date) } ?: 0,
        levels = days.map { it.level }.toImmutableList(),
    )

// The calendar's rows run Sunday to Saturday; a date that won't parse starts on Sunday.
private fun sundayFirstIndex(isoDate: String): Int =
    LocalDate.Formats.ISO
        .parseOrNull(isoDate)
        ?.dayOfWeek
        ?.isoDayNumber
        ?.rem(DAYS_PER_WEEK) ?: 0
