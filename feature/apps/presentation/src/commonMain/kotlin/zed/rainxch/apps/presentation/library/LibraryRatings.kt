package zed.rainxch.apps.presentation.library

import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import zed.rainxch.apps.presentation.AppsStateHolder
import zed.rainxch.apps.presentation.model.AppItem
import zed.rainxch.apps.presentation.withFilteredApps
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.reviews.RepoRating
import zed.rainxch.core.domain.model.reviews.ReviewTarget
import zed.rainxch.core.domain.repository.ReviewsRepository

// One summaries call per change in the set of tracked repos; a failed call is tried again on a change after a few minutes.
internal class LibraryRatings(
    private val reviewsRepository: ReviewsRepository,
) {
    private var ratingsByRepo: Map<Long, RepoRating> = emptyMap()
    private var ratedTargets: Set<ReviewTarget> = emptySet()
    private var failedAt: Long? = null
    private var job: Job? = null

    fun withRating(item: AppItem): AppItem {
        val rating = ratingsByRepo[item.installedApp.repoId]
        return item.copy(ratingAverage = rating?.average, ratingCount = rating?.count ?: 0)
    }

    fun refresh(
        holder: AppsStateHolder,
        apps: List<InstalledApp>,
    ) {
        val targets = apps.associate { it.repoId to ReviewTarget(owner = it.repoOwner, name = it.repoName, host = it.sourceHost) }
        val wanted = targets.values.toSet()
        val retryDue = failedAt?.let { System.currentTimeMillis() - it > RETRY_MS } ?: false
        if (wanted == ratedTargets && !retryDue) return
        ratedTargets = wanted
        failedAt = null
        job?.cancel()
        job =
            holder.scope.launch {
                reviewsRepository
                    .getSummaries(wanted.toList())
                    .onSuccess { byTarget ->
                        ratingsByRepo = targets.mapNotNull { (repoId, target) -> byTarget[target]?.let { repoId to it } }.toMap()
                        holder.update { state ->
                            state.copy(apps = state.apps.map { withRating(it) }.toImmutableList()).withFilteredApps()
                        }
                    }.onFailure { failedAt = System.currentTimeMillis() }
            }
    }

    private companion object {
        const val RETRY_MS = 5 * 60 * 1000L
    }
}
