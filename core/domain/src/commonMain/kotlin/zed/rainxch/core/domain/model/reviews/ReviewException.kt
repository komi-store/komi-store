package zed.rainxch.core.domain.model.reviews

class ReviewException(
    val kind: ReviewError,
    val retryAfterSeconds: Long? = null,
) : Exception("Review request failed: $kind")

// One per backend error code in docs/agents/reviews.md, plus the two ways a call never got an answer.
enum class ReviewError {
    AUTH_REQUIRED,
    AUTH_INVALID,
    ACCOUNT_TOO_NEW,
    OWN_REPO,
    OWN_REVIEW,
    BANNED,
    NOT_MAINTAINER,
    NOT_MODERATOR,
    REPO_NOT_FOUND,
    REVIEW_NOT_FOUND,
    INVALID_INPUT,
    RATE_LIMITED,
    UNAVAILABLE,
    NETWORK,
}
