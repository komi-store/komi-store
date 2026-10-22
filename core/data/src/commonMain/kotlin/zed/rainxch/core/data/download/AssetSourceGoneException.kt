package zed.rainxch.core.data.download

// The source answered 404 or 410: the URL is gone, so the release is worth resolving again.
class AssetSourceGoneException(
    val statusCode: Int,
) : Exception("Unexpected code $statusCode")
