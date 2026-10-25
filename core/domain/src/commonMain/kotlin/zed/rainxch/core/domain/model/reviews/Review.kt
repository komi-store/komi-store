package zed.rainxch.core.domain.model.reviews

import kotlin.time.Instant

// A rating runs from one to five stars.
val REVIEW_RATING_RANGE = 1..5

data class Review(
    val id: String,
    val rating: Int,
    val text: String?,
    val version: String?,
    val platform: ReviewPlatform?,
    val appLabel: String?,
    val author: ReviewAuthor,
    val createdAt: Instant?,
    val updatedAt: Instant?,
    val edited: Boolean,
    // Upvotes minus downvotes; can be negative.
    val score: Int,
    // Only ever true on the viewer's own review (from /mine) and in the moderation queue.
    val hidden: Boolean,
    val reply: ReviewReply?,
)

data class ReviewAuthor(
    val id: Long,
    val login: String,
    val avatarUrl: String?,
)

data class ReviewReply(
    val author: ReviewAuthor,
    val text: String,
    val createdAt: Instant?,
    val updatedAt: Instant?,
)

enum class ReviewPlatform {
    ANDROID,
    WINDOWS,
    MACOS,
    LINUX,
}
