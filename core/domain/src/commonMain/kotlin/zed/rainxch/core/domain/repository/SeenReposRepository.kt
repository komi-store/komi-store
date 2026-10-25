package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.repository.SeenRepo

interface SeenReposRepository {
    fun getAllSeenRepoIds(): Flow<Set<Long>>

    fun getAllSeenRepos(): Flow<List<SeenRepo>>

    suspend fun markAsSeen(repo: GithubRepoSummary)

    suspend fun markAsSeen(
        repoId: Long,
        repoName: String,
        repoOwner: String,
        repoOwnerAvatarUrl: String,
        repoDescription: String?,
        primaryLanguage: String?,
        repoUrl: String,
        stargazersCount: Int? = null,
        platforms: List<DiscoveryPlatform> = emptyList(),
        latestVersion: String? = null,
        sourceHost: String? = null,
    )

    suspend fun removeFromHistory(repoId: Long)

    suspend fun clearAll()
}
