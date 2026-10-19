package zed.rainxch.repopages.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GithubUserDto(
    val login: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
)
