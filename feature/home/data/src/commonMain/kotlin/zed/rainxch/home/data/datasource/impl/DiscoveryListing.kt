package zed.rainxch.home.data.datasource.impl

import zed.rainxch.core.data.dto.BackendRepoResponse
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.getCategory
import zed.rainxch.core.data.network.getTopic
import zed.rainxch.home.data.dto.CachedGithubRepoSummary
import zed.rainxch.home.data.dto.CachedRepoResponse
import zed.rainxch.home.domain.model.HomeCategory
import zed.rainxch.home.domain.model.TopicCategory

internal sealed interface DiscoveryListing {
    val logName: String
    val mirrorFolder: String
    val responseCategory: String

    fun mirrorCategory(first: CachedRepoResponse): String?

    fun merge(repos: List<CachedGithubRepoSummary>): List<CachedGithubRepoSummary>

    suspend fun fetch(
        client: BackendApiClient,
        platformSlug: String,
    ): Result<List<BackendRepoResponse>>

    data class Chart(
        val category: HomeCategory,
    ) : DiscoveryListing {
        private val slug =
            when (category) {
                HomeCategory.TRENDING -> "trending"
                HomeCategory.HOT_RELEASE -> "new-releases"
                HomeCategory.MOST_POPULAR -> "most-popular"
            }

        override val logName: String get() = slug
        override val mirrorFolder: String get() = slug
        override val responseCategory: String get() = slug

        override fun mirrorCategory(first: CachedRepoResponse): String? = first.category

        override fun merge(repos: List<CachedGithubRepoSummary>): List<CachedGithubRepoSummary> =
            repos
                .mergeById { acc, repo ->
                    acc.copy(
                        availablePlatforms = (acc.availablePlatforms + repo.availablePlatforms).distinct(),
                        trendingScore = listOfNotNull(acc.trendingScore, repo.trendingScore).maxOrNull(),
                        popularityScore = listOfNotNull(acc.popularityScore, repo.popularityScore).maxOrNull(),
                        latestReleaseDate = listOfNotNull(acc.latestReleaseDate, repo.latestReleaseDate).maxOrNull(),
                    )
                }.sortedWith(
                    compareByDescending<CachedGithubRepoSummary> { it.trendingScore }
                        .thenByDescending { it.popularityScore }
                        .thenByDescending { it.latestReleaseDate },
                )

        override suspend fun fetch(
            client: BackendApiClient,
            platformSlug: String,
        ): Result<List<BackendRepoResponse>> = client.getCategory(slug, platformSlug)
    }

    data class Topic(
        val topic: TopicCategory,
    ) : DiscoveryListing {
        private val slug =
            when (topic) {
                TopicCategory.PRIVACY -> "privacy"
                TopicCategory.MEDIA -> "media"
                TopicCategory.PRODUCTIVITY -> "productivity"
                TopicCategory.NETWORKING -> "networking"
                TopicCategory.DEV_TOOLS -> "dev-tools"
            }

        override val logName: String get() = "topic $slug"
        override val mirrorFolder: String get() = "topics/$slug"
        override val responseCategory: String get() = "topic"

        override fun mirrorCategory(first: CachedRepoResponse): String = responseCategory

        override fun merge(repos: List<CachedGithubRepoSummary>): List<CachedGithubRepoSummary> =
            repos
                .mergeById { acc, repo ->
                    acc.copy(
                        availablePlatforms = (acc.availablePlatforms + repo.availablePlatforms).distinct(),
                        latestReleaseDate = listOfNotNull(acc.latestReleaseDate, repo.latestReleaseDate).maxOrNull(),
                    )
                }.sortedByDescending { it.stargazersCount }

        override suspend fun fetch(
            client: BackendApiClient,
            platformSlug: String,
        ): Result<List<BackendRepoResponse>> = client.getTopic(slug, platformSlug)
    }
}

private fun List<CachedGithubRepoSummary>.mergeById(
    combine: (CachedGithubRepoSummary, CachedGithubRepoSummary) -> CachedGithubRepoSummary,
): List<CachedGithubRepoSummary> = groupBy { it.id }.values.map { it.reduce(combine) }
