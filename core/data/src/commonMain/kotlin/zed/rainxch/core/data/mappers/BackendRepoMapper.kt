package zed.rainxch.core.data.mappers

import zed.rainxch.core.data.dto.BackendPlatformRelease
import zed.rainxch.core.data.dto.BackendRepoResponse
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.account.github.GithubUser
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime

fun BackendRepoResponse.toSummary(platform: DiscoveryPlatform = DiscoveryPlatform.All): GithubRepoSummary =
    GithubRepoSummary(
        id = id,
        name = name,
        fullName = fullName,
        owner = GithubUser(
            id = 0,
            login = owner.login,
            avatarUrl = owner.avatarUrl ?: "",
            htmlUrl = "https://github.com/${owner.login}",
        ),
        description = description,
        defaultBranch = defaultBranch ?: "main",
        htmlUrl = htmlUrl,
        stargazersCount = stargazersCount,
        forksCount = forksCount,
        language = language,
        topics = topics.ifEmpty { null },
        topicCodes = topicCodes,
        releasesUrl = releasesUrl ?: "https://api.github.com/repos/$fullName/releases{/id}",
        updatedAt = updatedAt ?: latestReleaseDate ?: "",
        pushedAt = pushedAt,
        availablePlatforms = buildAvailablePlatforms(),
        downloadCount = downloadCount,
        latestReleaseDate = releaseDateFor(platform),
        latestReleaseTag = platformReleaseFor(platform)?.tag ?: latestReleaseTag,
        dailyStars = dailyStars,
    )

fun BackendRepoResponse.platformReleaseFor(platform: DiscoveryPlatform): BackendPlatformRelease? =
    platformReleases?.get(platform.name.lowercase())

fun BackendRepoResponse.releaseDateFor(platform: DiscoveryPlatform): String? =
    platformReleaseFor(platform)?.publishedAt?.takeIf { it.isNotBlank() }
        ?: latestReleaseDate?.takeIf { it.isNotBlank() }
        ?: releaseRecency?.takeIf { it >= 0 }?.let { releaseDateFromRecencyDays(it) }

@OptIn(ExperimentalTime::class)
fun releaseDateFromRecencyDays(days: Int): String =
    (Clock.System.now() - days.days).toString()

private fun BackendRepoResponse.buildAvailablePlatforms(): List<DiscoveryPlatform> =
    buildList {
        if (hasInstallersAndroid) add(DiscoveryPlatform.Android)
        if (hasInstallersWindows) add(DiscoveryPlatform.Windows)
        if (hasInstallersMacos) add(DiscoveryPlatform.Macos)
        if (hasInstallersLinux) add(DiscoveryPlatform.Linux)
    }
