package zed.rainxch.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AssetNetwork(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("size") val size: Long,
    @SerialName("browser_download_url") val downloadUrl: String,
    @SerialName("uploader") val uploader: OwnerNetwork? = null,
    @SerialName("download_count") val downloadCount: Long = 0,
    @SerialName("digest") val digest: String? = null,
)
