package zed.rainxch.core.data.download

import zed.rainxch.core.domain.model.error.NetworkException

// The source answered 404 or 410: the URL is gone, so the release is worth resolving again.
class AssetSourceGoneException(
    val statusCode: Int,
) : NetworkException("Unexpected code $statusCode", null)
