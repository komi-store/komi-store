package zed.rainxch.details.presentation.handlers

import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.reviews.ReviewTarget
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.core.domain.repository.ReviewsRepository
import zed.rainxch.core.domain.repository.SeenReposRepository
import zed.rainxch.core.domain.repository.StarredRepository

// What the page shows beside the releases: the favourite and star flags, the seen mark and the reviews preview.
internal class DetailsRepoExtras(
    private val favouritesRepository: FavouritesRepository,
    private val starredRepository: StarredRepository,
    private val seenReposRepository: SeenReposRepository,
    private val reviewsRepository: ReviewsRepository,
    private val logger: KomiStoreLogger,
) {
    internal suspend fun markSeen(repo: GithubRepoSummary) {
        try {
            seenReposRepository.markAsSeen(repo)
        } catch (e: LocalStoreException) {
            logger.error("Failed to mark repo as seen: ${e.message}")
        }
    }

    internal suspend fun isFavourite(repoId: Long): Boolean =
        try {
            favouritesRepository.isFavoriteSync(repoId)
        } catch (e: LocalStoreException) {
            logger.error("Failed to load if repo is favourite: ${e.message}")
            false
        }

    internal suspend fun isStarred(repoId: Long): Boolean =
        try {
            starredRepository.isStarred(repoId)
        } catch (e: LocalStoreException) {
            logger.error("Failed to load if repo is starred: ${e.message}")
            false
        }

    // The section stays hidden while the backend has reviews switched off or can't be reached.
    internal fun loadReviewsPreview(
        session: DetailsSession,
        repo: GithubRepoSummary,
    ) {
        session.reviewsJob?.cancel()
        session.reviewsJob =
            session.launch {
                reviewsRepository.getReviews(repo.reviewTarget()).onSuccess { page ->
                    session.update {
                        it.copy(reviewSummary = page.summary, topReviews = page.reviews.take(PREVIEW_REVIEW_COUNT))
                    }
                }
            }
    }

    internal fun observeReviewChanges(session: DetailsSession) {
        session.launch {
            reviewsRepository.changes.collect { target ->
                val repo = session.current.repository ?: return@collect
                if (target == repo.reviewTarget()) loadReviewsPreview(session, repo)
            }
        }
    }

    private fun GithubRepoSummary.reviewTarget() = ReviewTarget(owner = owner.login, name = name, host = sourceHost)

    private companion object {
        const val PREVIEW_REVIEW_COUNT = 3
    }
}
