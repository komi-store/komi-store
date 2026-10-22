package zed.rainxch.core.domain.model.error

class RateLimitException(
    val rateLimitInfo: RateLimitInfo,
) : Exception()
