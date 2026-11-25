package zed.rainxch.apps.domain.repository

import zed.rainxch.apps.domain.model.GithubRepoInfo
import zed.rainxch.core.domain.model.account.github.GithubRelease

interface RepoLookup {
    suspend fun getLatestRelease(
        owner: String,
        repo: String,
        includePreReleases: Boolean = false,
        sourceHost: String? = null,
    ): GithubRelease?

    suspend fun fetchRepoInfo(
        owner: String,
        repo: String,
        sourceHost: String? = null,
    ): GithubRepoInfo?
}
