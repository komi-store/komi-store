package zed.rainxch.core.domain.model.reviews

data class ReviewsPage(
    val summary: RatingSummary,
    val reviews: List<Review>,
    val page: Int,
    val hasMore: Boolean,
)

enum class ReviewSort {
    TOP,
    NEWEST,
    HIGHEST,
    LOWEST,
}

// GitHub repos have no host; a Forgejo repo carries its host (codeberg.org, ...).
data class ReviewTarget(
    val owner: String,
    val name: String,
    val host: String? = null,
)
