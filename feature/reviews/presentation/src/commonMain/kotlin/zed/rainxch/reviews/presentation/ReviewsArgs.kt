package zed.rainxch.reviews.presentation

data class ReviewsArgs(
    val owner: String,
    val repo: String,
    val sourceHost: String?,
    val openComposer: Boolean,
    val composerRating: Int,
    val version: String?,
    val appLabel: String?,
)
