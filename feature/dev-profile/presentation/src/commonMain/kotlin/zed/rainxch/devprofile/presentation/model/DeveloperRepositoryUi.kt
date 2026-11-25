package zed.rainxch.devprofile.presentation.model

data class DeveloperRepositoryUi(
    val id: Long,
    val name: String,
    val fullName: String,
    val description: String?,
    val htmlUrl: String,
    val stargazersCount: Int,
    val forksCount: Int,
    val openIssuesCount: Int,
    val language: String?,
    val hasReleases: Boolean,
    val hasInstallableAssets: Boolean,
    val isInstalled: Boolean,
    val isFavorite: Boolean,
    val latestVersion: String?,
    val latestReleaseAt: String?,
    val updatedAt: String,
)
