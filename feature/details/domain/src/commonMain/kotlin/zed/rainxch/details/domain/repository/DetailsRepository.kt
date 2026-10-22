package zed.rainxch.details.domain.repository

import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.account.github.GithubUserProfile
import zed.rainxch.details.domain.model.CachedReleases
import zed.rainxch.details.domain.model.RepoStats

typealias ReadmeContent = String
typealias ReadmePath = String
typealias LanguageCode = String

interface DetailsRepository {
    suspend fun getRepositoryById(id: Long): GithubRepoSummary

    suspend fun getRepositoryByOwnerAndName(
        owner: String,
        name: String,
        sourceHost: String? = null,
    ): GithubRepoSummary

    suspend fun refreshRepository(
        owner: String,
        name: String,
    ): GithubRepoSummary

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

    suspend fun getReadme(
        owner: String,
        repo: String,
        defaultBranch: String,
        sourceHost: String? = null,
    ): Triple<ReadmeContent, LanguageCode?, ReadmePath>?

    suspend fun getRepoStats(
        owner: String,
        repo: String,
        sourceHost: String? = null,
    ): RepoStats

    suspend fun getUserProfile(username: String): GithubUserProfile

    suspend fun checkAttestations(
        owner: String,
        repo: String,
        sha256Digest: String,
    ): Boolean

    suspend fun fetchRawMarkdown(url: String): String?
}
