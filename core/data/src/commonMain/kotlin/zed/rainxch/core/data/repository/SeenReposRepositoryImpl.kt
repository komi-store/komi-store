package zed.rainxch.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import zed.rainxch.core.data.local.db.dao.SeenRepoDao
import zed.rainxch.core.data.local.db.entities.SeenRepoEntity
import zed.rainxch.core.data.mappers.toPlatforms
import zed.rainxch.core.data.mappers.toPlatformsColumn
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.repository.SeenRepo
import zed.rainxch.core.domain.repository.SeenReposRepository

class SeenReposRepositoryImpl(
    private val seenRepoDao: SeenRepoDao,
) : SeenReposRepository {
    override fun getAllSeenRepoIds(): Flow<Set<Long>> = seenRepoDao.getAllSeenRepoIds().map { it.toSet() }

    override fun getAllSeenRepos(): Flow<List<SeenRepo>> =
        seenRepoDao.getAllSeenRepos().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun markAsSeen(repo: GithubRepoSummary) {
        markAsSeen(
            repoId = repo.id,
            repoName = repo.name,
            repoOwner = repo.owner.login,
            repoOwnerAvatarUrl = repo.owner.avatarUrl,
            repoDescription = repo.description,
            primaryLanguage = repo.language,
            repoUrl = repo.htmlUrl,
            stargazersCount = repo.stargazersCount,
            platforms = repo.availablePlatforms,
            latestVersion = repo.latestReleaseTag,
            sourceHost = repo.sourceHost,
        )
    }

    override suspend fun markAsSeen(
        repoId: Long,
        repoName: String,
        repoOwner: String,
        repoOwnerAvatarUrl: String,
        repoDescription: String?,
        primaryLanguage: String?,
        repoUrl: String,
        stargazersCount: Int?,
        platforms: List<DiscoveryPlatform>,
        latestVersion: String?,
        sourceHost: String?,
    ) {
        seenRepoDao.insert(
            SeenRepoEntity(
                repoId = repoId,
                repoName = repoName,
                repoOwner = repoOwner,
                repoOwnerAvatarUrl = repoOwnerAvatarUrl,
                repoDescription = repoDescription,
                primaryLanguage = primaryLanguage,
                repoUrl = repoUrl,
                seenAt = System.currentTimeMillis(),
                stargazersCount = stargazersCount,
                platforms = platforms.toPlatformsColumn(),
                latestVersion = latestVersion,
                sourceHost = sourceHost,
            ),
        )
    }

    override suspend fun removeFromHistory(repoId: Long) {
        seenRepoDao.deleteById(repoId)
    }

    override suspend fun clearAll() {
        seenRepoDao.clearAll()
    }

    private fun SeenRepoEntity.toDomain(): SeenRepo =
        SeenRepo(
            repoId = repoId,
            repoName = repoName,
            repoOwner = repoOwner,
            repoOwnerAvatarUrl = repoOwnerAvatarUrl,
            repoDescription = repoDescription,
            primaryLanguage = primaryLanguage,
            repoUrl = repoUrl,
            seenAt = seenAt,
            stargazersCount = stargazersCount,
            platforms = platforms.toPlatforms(),
            latestVersion = latestVersion,
            sourceHost = sourceHost,
        )
}
