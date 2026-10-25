package zed.rainxch.home.data.dto

import kotlinx.serialization.Serializable
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform

@Serializable
data class CachedGithubRepoSummary(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: CachedGithubOwner,
    val description: String?,
    val defaultBranch: String,
    val htmlUrl: String,
    val stargazersCount: Int,
    val forksCount: Int,
    val language: String?,
    val topics: List<String>?,
    val releasesUrl: String,
    val updatedAt: String,
    val latestReleaseDate: String? = null,
    val trendingScore: Double? = null,
    val popularityScore: Int? = null,
    val dailyStars: Int? = null,
    val availablePlatforms: List<DiscoveryPlatform> = emptyList(),
    val downloadCount: Long = 0,
    val ratingAverage: Double? = null,
    val ratingCount: Int = 0,
)
