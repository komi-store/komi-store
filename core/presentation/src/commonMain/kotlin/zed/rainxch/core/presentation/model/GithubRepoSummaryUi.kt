package zed.rainxch.core.presentation.model

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform

data class GithubRepoSummaryUi(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: GithubUserUi,
    val description: String?,
    val defaultBranch: String,
    val htmlUrl: String,
    val stargazersCount: Int,
    val forksCount: Int,
    val language: String?,
    val topics: ImmutableList<String>?,
    val releasesUrl: String,
    val updatedAt: String,
    val isFork: Boolean = false,
    val availablePlatforms: ImmutableList<DiscoveryPlatform> = persistentListOf(),
    val downloadCount: Long = 0,
    val latestReleaseDate: String? = null,
    val latestReleaseTag: String? = null,
    val sourceHost: String? = null,
    val dailyStars: Int? = null,
    val ratingAverage: Double? = null,
    val ratingCount: Int = 0,
)
