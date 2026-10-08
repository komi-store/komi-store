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

    // bypassCache skips the stored copy on the way in; allowStale=false is for a read the user
    // explicitly asked for — on failure it throws instead of quietly serving that stored copy,
    // so the caller can say the read did not happen. preferDirectSource puts the repository
    // host ahead of the backend for that same read: the backend answers from a copy it keeps
    // for an hour and offers nothing to force it, so only the host can actually be current.
    // The backend stays as the fallback.
    suspend fun getAllReleases(
        owner: String,
        repo: String,
        defaultBranch: String,
        sourceHost: String? = null,
        bypassCache: Boolean = false,
        allowStale: Boolean = true,
        preferDirectSource: Boolean = false,
    ): List<GithubRelease>

    // The copy the details page can paint before any network read, with the moment it was
    // stored; null when only a read can tell what the list currently is.
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
