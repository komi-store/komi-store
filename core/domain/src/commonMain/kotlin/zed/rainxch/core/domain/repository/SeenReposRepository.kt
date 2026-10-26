package zed.rainxch.core.domain.repository

import kotlinx.coroutines.flow.Flow
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.repository.SeenRepo
import zed.rainxch.core.domain.model.repository.SeenRepoEntry

interface SeenReposRepository {
    fun getAllSeenRepoIds(): Flow<Set<Long>>

    fun getAllSeenRepos(): Flow<List<SeenRepo>>

    suspend fun markAsSeen(repo: GithubRepoSummary)

    suspend fun markAsSeen(entry: SeenRepoEntry)

    suspend fun removeFromHistory(repoId: Long)

    suspend fun clearAll()
}
