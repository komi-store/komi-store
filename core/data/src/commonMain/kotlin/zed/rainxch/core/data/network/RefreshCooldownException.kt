package zed.rainxch.core.data.network

import zed.rainxch.core.domain.model.error.NetworkException

class RefreshCooldownException(
    val retryAfterSeconds: Long,
) : NetworkException("Refresh cooldown ($retryAfterSeconds s)", null)
