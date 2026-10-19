package zed.rainxch.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubReadmeResponseDto(
    @SerialName("name") val name: String? = null,
    @SerialName("path") val path: String? = null,
    @SerialName("content") val content: String? = null,
    @SerialName("encoding") val encoding: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("download_url") val downloadUrl: String? = null,
)
