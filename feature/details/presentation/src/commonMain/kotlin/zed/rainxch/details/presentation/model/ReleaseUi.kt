package zed.rainxch.details.presentation.model

data class ReleaseUi(
    val id: Long,
    val tagName: String,
    val name: String?,
    val publishedAt: String,
    val description: String?,
    val isPreRelease: Boolean,
    val preReleaseLabel: String?,
)
