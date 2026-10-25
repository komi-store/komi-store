package zed.rainxch.core.domain.model.account.github

import kotlinx.serialization.Serializable
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.reviews.RepoRating

@Serializable
data class GithubRepoSummary(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: GithubUser,
    val description: String?,
    val defaultBranch: String,
    val htmlUrl: String,
    val stargazersCount: Int,
    val forksCount: Int,
    val language: String?,
    val topics: List<String>?,
    val releasesUrl: String,
    val updatedAt: String,
    val pushedAt: String? = null,
    val isFork: Boolean = false,
    val availablePlatforms: List<DiscoveryPlatform> = emptyList(),
    val downloadCount: Long = 0,
    val latestReleaseDate: String? = null,
    val latestReleaseTag: String? = null,
    val sourceHost: String? = null,
    val dailyStars: Int? = null,
    val topicCodes: List<String> = emptyList(),
    val rating: RepoRating? = null,
)
