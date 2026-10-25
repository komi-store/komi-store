package zed.rainxch.reviews.presentation.utils

import org.jetbrains.compose.resources.StringResource
import zed.rainxch.core.domain.model.reviews.ReviewError
import zed.rainxch.core.domain.model.reviews.ReviewException
import zed.rainxch.githubstore.core.presentation.res.Res
import zed.rainxch.githubstore.core.presentation.res.reviews_cannot_account_too_new
import zed.rainxch.githubstore.core.presentation.res.reviews_cannot_banned
import zed.rainxch.githubstore.core.presentation.res.reviews_cannot_own_repo
import zed.rainxch.githubstore.core.presentation.res.reviews_error_generic
import zed.rainxch.githubstore.core.presentation.res.reviews_error_network
import zed.rainxch.githubstore.core.presentation.res.reviews_error_not_maintainer
import zed.rainxch.githubstore.core.presentation.res.reviews_error_not_moderator
import zed.rainxch.githubstore.core.presentation.res.reviews_error_own_review
import zed.rainxch.githubstore.core.presentation.res.reviews_error_rate_limited
import zed.rainxch.githubstore.core.presentation.res.reviews_error_sign_in
import zed.rainxch.githubstore.core.presentation.res.reviews_unavailable

fun Throwable.reviewMessage(): StringResource =
    when ((this as? ReviewException)?.kind) {
        ReviewError.AUTH_REQUIRED, ReviewError.AUTH_INVALID -> Res.string.reviews_error_sign_in
        ReviewError.ACCOUNT_TOO_NEW -> Res.string.reviews_cannot_account_too_new
        ReviewError.OWN_REPO -> Res.string.reviews_cannot_own_repo
        ReviewError.OWN_REVIEW -> Res.string.reviews_error_own_review
        ReviewError.BANNED -> Res.string.reviews_cannot_banned
        ReviewError.NOT_MAINTAINER -> Res.string.reviews_error_not_maintainer
        ReviewError.NOT_MODERATOR -> Res.string.reviews_error_not_moderator
        ReviewError.RATE_LIMITED -> Res.string.reviews_error_rate_limited
        ReviewError.UNAVAILABLE -> Res.string.reviews_unavailable
        ReviewError.NETWORK -> Res.string.reviews_error_network
        ReviewError.INVALID_INPUT, ReviewError.REPO_NOT_FOUND, ReviewError.REVIEW_NOT_FOUND, null -> Res.string.reviews_error_generic
    }

// The backend answers 404 for every review route while REVIEWS_ENABLED is off, and is optional by design.
fun Throwable.isReviewsUnavailable(): Boolean =
    (this as? ReviewException)?.kind == ReviewError.UNAVAILABLE ||
        (this as? ReviewException)?.kind == ReviewError.NETWORK
