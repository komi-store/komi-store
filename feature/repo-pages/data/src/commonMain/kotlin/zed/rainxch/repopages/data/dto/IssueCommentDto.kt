package zed.rainxch.repopages.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class IssueCommentDto(
    val id: Long = 0,
    val user: GithubUserDto? = null,
    val body: String? = null,
    val reactions: ReactionsDto? = null,
    @SerialName("created_at") val createdAt: String = "",
)
