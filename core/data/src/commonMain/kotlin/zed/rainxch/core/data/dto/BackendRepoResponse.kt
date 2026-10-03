package zed.rainxch.core.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class BackendRepoResponse(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: BackendRepoOwner,
    val description: String? = null,
    val defaultBranch: String? = null,
    val htmlUrl: String,
    val stargazersCount: Int,
    val forksCount: Int,
    val openIssuesCount: Int = 0,
    val license: BackendLicense? = null,
    val language: String? = null,
    val topics: List<String> = emptyList(),
    val topicCodes: List<String> = emptyList(),
    val releasesUrl: String? = null,
    val updatedAt: String? = null,
    val pushedAt: String? = null,
    val createdAt: String? = null,
    val latestReleaseDate: String? = null,
    val latestReleaseTag: String? = null,
    val releaseRecency: Int? = null,
    val releaseRecencyText: String? = null,
    val dailyStars: Int? = null,
    val trendingScore: Double? = null,
    val popularityScore: Double? = null,
    val hasInstallersAndroid: Boolean = false,
    val hasInstallersWindows: Boolean = false,
    val hasInstallersMacos: Boolean = false,
    val hasInstallersLinux: Boolean = false,
    val platformReleases: Map<String, BackendPlatformRelease>? = null,
    val downloadCount: Long = 0,
)
