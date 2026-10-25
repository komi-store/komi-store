package zed.rainxch.core.domain.model.reviews

data class MyReviewState(
    val review: Review?,
    val votes: Map<String, ReviewVote>,
    val canReview: Boolean,
    val cannotReviewReason: CannotReviewReason?,
    val canReply: Boolean,
    val isModerator: Boolean,
)

enum class ReviewVote(
    val value: Int,
) {
    UP(1),
    DOWN(-1),
}

enum class CannotReviewReason {
    ACCOUNT_TOO_NEW,
    OWN_REPO,
    BANNED,
}

data class ReviewDraft(
    val rating: Int,
    val text: String?,
    val version: String?,
    val platform: ReviewPlatform?,
    val appLabel: String?,
)

enum class ReportReason {
    SPAM,
    OFFENSIVE,
    OFF_TOPIC,
    OTHER,
}
