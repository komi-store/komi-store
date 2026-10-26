package zed.rainxch.core.data.local.db.entities

// The variant columns of an installed_apps row; writing a pin always clears the stale flag.
data class InstalledAppVariantPin(
    val packageName: String,
    val preferredAssetVariant: String?,
    val preferredAssetTokens: String?,
    val assetGlobPattern: String?,
    val pickedAssetIndex: Int?,
    val pickedAssetSiblingCount: Int?,
    val preferredVariantStale: Boolean = false,
)
