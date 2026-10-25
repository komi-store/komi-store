package zed.rainxch.core.presentation.utils

import kotlinx.collections.immutable.toImmutableList
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.presentation.model.GithubRepoSummaryUi

fun GithubRepoSummary.toUi(): GithubRepoSummaryUi =
    GithubRepoSummaryUi(
        id = id,
        name = name,
        fullName = fullName,
        owner = owner.toUi(),
        description = description,
        defaultBranch = defaultBranch,
        htmlUrl = htmlUrl,
        stargazersCount = stargazersCount,
        forksCount = forksCount,
        language = language,
        topics = topics?.toImmutableList(),
        releasesUrl = releasesUrl,
        updatedAt = updatedAt,
        isFork = isFork,
        availablePlatforms = availablePlatforms.toImmutableList(),
        downloadCount = downloadCount,
        latestReleaseDate = latestReleaseDate,
        latestReleaseTag = latestReleaseTag,
        sourceHost = sourceHost,
        dailyStars = dailyStars,
        ratingAverage = rating?.average,
        ratingCount = rating?.count ?: 0,
    )
