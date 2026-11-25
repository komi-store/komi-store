package zed.rainxch.auth.data.network

import zed.rainxch.core.domain.model.error.NetworkException

class BackendHttpException(
    val statusCode: Int,
    message: String,
) : NetworkException(message, null)
