package zed.rainxch.details.domain.repository

import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.details.domain.model.CachedReleases

interface ReleaseReader {
    suspend fun getLatestPublishedRelease(
        owner: String,
        repo: String,
        defaultBranch: String,
        sourceHost: String? = null,
    ): GithubRelease?

    // allowStale = false throws instead of serving the stored copy, so a user-asked read can report that it failed.
    suspend fun getAllReleases(
        owner: String,
        repo: String,
        defaultBranch: String,
        sourceHost: String? = null,
        bypassCache: Boolean = false,
        allowStale: Boolean = true,
        preferDirectSource: Boolean = false,
    ): List<GithubRelease>

    suspend fun getCachedReleases(
        owner: String,
        repo: String,
        sourceHost: String? = null,
    ): CachedReleases?
}
