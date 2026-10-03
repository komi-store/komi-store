package zed.rainxch.home.data.mappers

import zed.rainxch.core.data.dto.BackendRepoResponse
import zed.rainxch.core.data.mappers.releaseDateFor
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.home.data.dto.CachedGithubOwner
import zed.rainxch.home.data.dto.CachedGithubRepoSummary

fun BackendRepoResponse.toCachedGithubRepoSummary(
    platform: DiscoveryPlatform = DiscoveryPlatform.All,
): CachedGithubRepoSummary =
    CachedGithubRepoSummary(
        id = id,
        name = name,
        fullName = fullName,
        owner = CachedGithubOwner(
            login = owner.login,
            avatarUrl = owner.avatarUrl ?: "",
        ),
        description = description,
        defaultBranch = defaultBranch ?: "main",
        htmlUrl = htmlUrl,
        stargazersCount = stargazersCount,
        forksCount = forksCount,
        language = language,
        topics = topics.ifEmpty { null },
        releasesUrl = releasesUrl ?: "https://api.github.com/repos/$fullName/releases{/id}",
        updatedAt = updatedAt ?: "",
        latestReleaseDate = releaseDateFor(platform),
        trendingScore = trendingScore,
        popularityScore = popularityScore?.toInt(),
        dailyStars = dailyStars,
        availablePlatforms = buildList {
            if (hasInstallersAndroid) add(DiscoveryPlatform.Android)
            if (hasInstallersWindows) add(DiscoveryPlatform.Windows)
            if (hasInstallersMacos) add(DiscoveryPlatform.Macos)
            if (hasInstallersLinux) add(DiscoveryPlatform.Linux)
        },
        downloadCount = downloadCount,
    )
