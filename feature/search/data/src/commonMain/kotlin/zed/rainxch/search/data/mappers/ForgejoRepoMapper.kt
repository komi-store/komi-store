package zed.rainxch.search.data.mappers

import zed.rainxch.core.data.dto.ForgejoRepoNetworkModel
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.account.github.GithubUser
import zed.rainxch.core.domain.utils.RepoIdCodec

internal fun ForgejoRepoNetworkModel.toSummary(host: String): GithubRepoSummary =
    GithubRepoSummary(
        id = RepoIdCodec.encode(host, id),
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
        defaultBranch = defaultBranch ?: "main",
        htmlUrl = htmlUrl,
        stargazersCount = starsCount,
        forksCount = forksCount,
        language = language,
        topics = null,
        releasesUrl = "$htmlUrl/releases",
        updatedAt = updatedAt ?: "",
        isFork = false,
        sourceHost = host,
    )
