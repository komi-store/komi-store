package zed.rainxch.home.presentation.marks

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.repository.SeenRepoEntry
import zed.rainxch.core.domain.repository.HiddenReposRepository
import zed.rainxch.core.domain.repository.SeenReposRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.presentation.model.GithubRepoSummaryUi

class HomeViewingMarksStore(
    private val seenReposRepository: SeenReposRepository,
    private val hiddenReposRepository: HiddenReposRepository,
    private val tweaksRepository: TweaksRepository,
    private val logger: KomiStoreLogger,
) {
    fun observe(): Flow<ViewingMarks> =
        combine(
            seenReposRepository.getAllSeenRepoIds().onStart { emit(emptySet()) },
            hiddenReposRepository.getAllHiddenRepoIds().onStart { emit(emptySet()) },
            tweaksRepository.getHideSeenEnabled().onStart { emit(false) },
            ::ViewingMarks,
        )

    fun gridLayout(): Flow<Boolean> = tweaksRepository.getRepoGridLayout()

    suspend fun hide(repo: GithubRepoSummaryUi) {
        try {
            hiddenReposRepository.hide(
                repoId = repo.id,
                repoName = repo.name,
                repoOwner = repo.owner.login,
                repoOwnerAvatarUrl = repo.owner.avatarUrl,
            )
        } catch (e: LocalStoreException) {
            logger.warn("Hide repository failed for ${repo.id}: ${e.message}")
        }
    }

    suspend fun markSeen(repo: GithubRepoSummaryUi) {
        try {
            seenReposRepository.markAsSeen(
                SeenRepoEntry(
                    repoId = repo.id,
                    repoName = repo.name,
                    repoOwner = repo.owner.login,
                    repoOwnerAvatarUrl = repo.owner.avatarUrl,
                    repoDescription = repo.description,
                    primaryLanguage = repo.language,
                    repoUrl = repo.htmlUrl,
                ),
            )
        } catch (e: LocalStoreException) {
            logger.warn("Mark as seen failed for ${repo.id}: ${e.message}")
        }
    }

    suspend fun markUnseen(repoId: Long) {
        try {
            seenReposRepository.removeFromHistory(repoId)
        } catch (e: LocalStoreException) {
            logger.warn("Mark as unseen failed for $repoId: ${e.message}")
        }
    }
}
