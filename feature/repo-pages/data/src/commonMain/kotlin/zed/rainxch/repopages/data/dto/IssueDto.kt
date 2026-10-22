package zed.rainxch.repopages.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class IssueDto(
    val number: Int,
    val title: String = "",
    val state: String = "open",
    val user: GithubUserDto? = null,
    val comments: Int = 0,
    val body: String? = null,
    val labels: List<LabelDto> = emptyList(),
    val reactions: ReactionsDto? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("pull_request") val pullRequest: JsonElement? = null,
)
