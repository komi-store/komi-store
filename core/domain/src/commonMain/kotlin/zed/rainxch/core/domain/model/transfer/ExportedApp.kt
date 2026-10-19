package zed.rainxch.core.domain.model.transfer
import kotlinx.serialization.Serializable

@Serializable
data class ExportedApp(
    val packageName: String,
    val repoOwner: String,
    val repoName: String,
    val repoUrl: String,
    val assetFilterRegex: String? = null,
    val fallbackToOlderReleases: Boolean = false,
    val preferredAssetVariant: String? = null,
    val preferredAssetTokens: String? = null,
    val assetGlobPattern: String? = null,
    val pickedAssetIndex: Int? = null,
    val pickedAssetSiblingCount: Int? = null,
)
