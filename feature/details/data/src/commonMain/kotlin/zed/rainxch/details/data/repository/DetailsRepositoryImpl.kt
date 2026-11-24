package zed.rainxch.details.data.repository

import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.details.domain.repository.DetailsRepository

internal class DetailsRepositoryImpl(
    repoInfo: RepoInfoReads,
    releases: ReleaseReads,
    content: RepoContentReads,
) : DetailsRepository,
    RepoInfoReads by repoInfo,
    ReleaseReads by releases,
    RepoContentReads by content {
    override suspend fun getAllReleases(
        owner: String,
        repo: String,
        defaultBranch: String,
        sourceHost: String?,
        bypassCache: Boolean,
        allowStale: Boolean,
        preferDirectSource: Boolean,
    ): List<GithubRelease> =
        getAllReleases(
            ReleaseRead(owner, repo, defaultBranch, sourceHost, bypassCache, allowStale, preferDirectSource),
        )
}
