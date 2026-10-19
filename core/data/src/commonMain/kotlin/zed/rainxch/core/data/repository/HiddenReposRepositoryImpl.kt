package zed.rainxch.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import zed.rainxch.core.data.local.db.dao.HiddenRepoDao
import zed.rainxch.core.data.local.db.entities.HiddenRepoEntity
import zed.rainxch.core.domain.model.repository.HiddenRepo
import zed.rainxch.core.domain.repository.HiddenReposRepository

class HiddenReposRepositoryImpl(
    private val hiddenRepoDao: HiddenRepoDao,
) : HiddenReposRepository {
    override fun getAllHiddenRepoIds(): Flow<Set<Long>> = hiddenRepoDao.getAllHiddenRepoIds().map { it.toSet() }

    override fun getAllHiddenRepos(): Flow<List<HiddenRepo>> =
        hiddenRepoDao.getAllHiddenRepos().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun hide(
        repoId: Long,
        repoName: String,
        repoOwner: String,
        repoOwnerAvatarUrl: String,
    ) {
        hiddenRepoDao.insert(
            HiddenRepoEntity(
                repoId = repoId,
                repoName = repoName,
                repoOwner = repoOwner,
                repoOwnerAvatarUrl = repoOwnerAvatarUrl,
                hiddenAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun unhide(repoId: Long) {
        hiddenRepoDao.deleteById(repoId)
    }

    override suspend fun clearAll() {
        hiddenRepoDao.clearAll()
    }

    private fun HiddenRepoEntity.toDomain(): HiddenRepo =
        HiddenRepo(
            repoId = repoId,
            repoName = repoName,
            repoOwner = repoOwner,
            repoOwnerAvatarUrl = repoOwnerAvatarUrl,
            hiddenAt = hiddenAt,
        )
}
