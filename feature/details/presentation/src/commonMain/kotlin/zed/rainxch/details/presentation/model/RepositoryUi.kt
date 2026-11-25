package zed.rainxch.details.presentation.model

data class RepositoryUi(
    val id: Long,
    val name: String,
    val ownerLogin: String,
    val ownerAvatarUrl: String,
    val description: String?,
    val htmlUrl: String,
    val isFork: Boolean,
    val lastPushAt: String,
    val sourceHost: String?,
)
