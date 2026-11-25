package zed.rainxch.core.data.network

import zed.rainxch.core.domain.model.error.NetworkException

class RateLimitedException(
    val retryAfterSeconds: Long? = null,
    val resetEpochSeconds: Long? = null,
    val limit: Int? = null,
) : NetworkException("Rate limited by backend (429)", null)
