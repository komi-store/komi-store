package zed.rainxch.details.data.mappers

import zed.rainxch.core.data.dto.BackendRepoResponse
import zed.rainxch.core.data.dto.ForgejoRepoNetworkModel
import zed.rainxch.core.data.dto.RepoByIdNetwork
import zed.rainxch.core.data.dto.RepoInfoNetwork
import zed.rainxch.core.data.dto.UserProfileNetwork
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.account.github.GithubUser
import zed.rainxch.core.domain.model.account.github.GithubUserProfile
import zed.rainxch.core.domain.utils.RepoIdCodec
import zed.rainxch.details.domain.model.RepoStats

internal fun RepoByIdNetwork.toGithubRepoSummary(): GithubRepoSummary =
    GithubRepoSummary(
        id = id,
        name = name,
        fullName = fullName,
        owner =
            GithubUser(
                id = owner.id,
                login = owner.login,
                avatarUrl = owner.avatarUrl,
                htmlUrl = owner.htmlUrl,
            ),
        description = description,
        htmlUrl = htmlUrl,
        stargazersCount = stars,
        forksCount = forks,
        language = language,
        topics = topics,
        releasesUrl = "https://api.github.com/repos/${owner.login}/$name/releases{/id}",
        updatedAt = updatedAt,
        pushedAt = pushedAt,
        defaultBranch = defaultBranch,
    )

internal fun ForgejoRepoNetworkModel.toForgejoSummary(sourceHost: String): GithubRepoSummary =
    GithubRepoSummary(
        id = RepoIdCodec.encode(sourceHost, id),
        name = name,
        fullName = fullName ?: "${owner.login}/$name",
        owner =
            GithubUser(
                id = owner.id,
                login = owner.login,
                avatarUrl = owner.avatarUrl,
                htmlUrl = owner.htmlUrl,
            ),
        description = description,
        htmlUrl = htmlUrl,
        stargazersCount = starsCount,
        forksCount = forksCount,
        language = language,
        topics = null,
        releasesUrl = "$htmlUrl/releases",
        updatedAt = updatedAt ?: "",
        pushedAt = pushedAt,
        defaultBranch = defaultBranch ?: "main",
        sourceHost = sourceHost,
    )

internal fun BackendRepoResponse.toRepoStats(): RepoStats =
    RepoStats(
        stars = stargazersCount,
        forks = forksCount,
        openIssues = openIssuesCount,
        license = license?.spdxId ?: license?.name,
        totalDownloads = downloadCount,
    )

internal fun RepoInfoNetwork.toRepoStats(): RepoStats =
    RepoStats(
        stars = stars,
        forks = forks,
        openIssues = openIssues,
        license = license?.spdxId ?: license?.name,
        totalDownloads = 0,
    )

internal fun UserProfileNetwork.toDomainProfile(): GithubUserProfile =
    GithubUserProfile(
        id = id,
        login = login,
        name = name,
        bio = bio,
        avatarUrl = avatarUrl,
        htmlUrl = htmlUrl,
        followers = followers,
        following = following,
        publicRepos = publicRepos,
        location = location,
        company = company,
        blog = blog,
        twitterUsername = twitterUsername,
    )
