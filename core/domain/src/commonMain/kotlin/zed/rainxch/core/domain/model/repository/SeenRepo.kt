package zed.rainxch.core.domain.model.repository

data class SeenRepo(
    val repoId: Long,
    val repoName: String,
    val repoOwner: String,
    val repoOwnerAvatarUrl: String,
    val repoDescription: String?,
    val primaryLanguage: String?,
    val repoUrl: String,
    val seenAt: Long,
    val stargazersCount: Int? = null,
    val platforms: List<DiscoveryPlatform> = emptyList(),
    val latestVersion: String? = null,
    val sourceHost: String? = null,
)
