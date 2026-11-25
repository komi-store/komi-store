package zed.rainxch.details.domain.repository

import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.account.github.GithubUserProfile
import zed.rainxch.details.domain.model.RepoStats

interface RepoSummaryReader {
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

    suspend fun getRepoStats(
        owner: String,
        repo: String,
        sourceHost: String? = null,
    ): RepoStats

    suspend fun getUserProfile(username: String): GithubUserProfile
}
