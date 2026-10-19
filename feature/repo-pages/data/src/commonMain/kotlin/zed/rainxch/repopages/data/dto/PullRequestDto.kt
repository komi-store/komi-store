package zed.rainxch.repopages.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PullRequestDto(
    val number: Int,
    val title: String = "",
    val state: String = "open",
    val user: GithubUserDto? = null,
    val comments: Int = 0,
    val draft: Boolean = false,
    val labels: List<LabelDto> = emptyList(),
    @SerialName("merged_at") val mergedAt: String? = null,
    @SerialName("created_at") val createdAt: String = "",
)
