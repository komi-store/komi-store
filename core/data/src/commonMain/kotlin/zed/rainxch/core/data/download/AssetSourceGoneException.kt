package zed.rainxch.core.data.download

// The source answered 404/410: the asset is not at the URL the download was handed any more.
// Kept apart from other failures on purpose — this is the shape a stale resolution leaves
// behind, and the one shape worth re-resolving the release for before giving up.
class AssetSourceGoneException(
    val statusCode: Int,
) : Exception("Unexpected code $statusCode") {
    companion object {
        fun isGoneCode(statusCode: Int): Boolean = statusCode == 404 || statusCode == 410
    }
}
