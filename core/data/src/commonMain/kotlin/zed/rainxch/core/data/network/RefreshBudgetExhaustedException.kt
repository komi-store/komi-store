package zed.rainxch.core.data.network

import zed.rainxch.core.domain.model.error.NetworkException

class RefreshBudgetExhaustedException(
    val retryAfterSeconds: Long,
) : NetworkException("Refresh budget exhausted ($retryAfterSeconds s)", null)
