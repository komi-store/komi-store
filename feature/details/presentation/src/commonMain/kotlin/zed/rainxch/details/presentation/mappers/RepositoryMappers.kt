package zed.rainxch.details.presentation.mappers

import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.account.github.GithubUserProfile
import zed.rainxch.details.domain.model.RepoStats
import zed.rainxch.details.presentation.model.OwnerProfileUi
import zed.rainxch.details.presentation.model.RepoStatsUi
import zed.rainxch.details.presentation.model.RepositoryUi

internal fun GithubRepoSummary.toUi(): RepositoryUi =
    RepositoryUi(
        id = id,
        name = name,
        ownerLogin = owner.login,
        ownerAvatarUrl = owner.avatarUrl,
        description = description,
        htmlUrl = htmlUrl,
        isFork = isFork,
        lastPushAt = pushedAt?.takeIf { it.isNotBlank() } ?: updatedAt,
        sourceHost = sourceHost,
    )

internal fun GithubUserProfile.toUi(): OwnerProfileUi = OwnerProfileUi(login = login, avatarUrl = avatarUrl)

internal fun RepoStats.toUi(): RepoStatsUi =
    RepoStatsUi(
        stars = stars,
        forks = forks,
        openIssues = openIssues,
        license = license,
        totalDownloads = totalDownloads,
    )
