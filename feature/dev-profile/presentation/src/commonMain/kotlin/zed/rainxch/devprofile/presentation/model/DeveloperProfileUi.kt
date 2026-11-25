package zed.rainxch.devprofile.presentation.model

data class DeveloperProfileUi(
    val login: String,
    val name: String?,
    val avatarUrl: String,
    val bio: String?,
    val company: String?,
    val location: String?,
    val blog: String?,
    val twitterUsername: String?,
    val publicRepos: Int,
    val followers: Int,
    val following: Int,
    val htmlUrl: String,
    val isOrganization: Boolean,
)
