package zed.rainxch.core.data.network

import zed.rainxch.core.domain.model.error.NetworkException

class BackendException(
    val statusCode: Int,
    message: String = "HTTP $statusCode",
) : NetworkException(message, null)
