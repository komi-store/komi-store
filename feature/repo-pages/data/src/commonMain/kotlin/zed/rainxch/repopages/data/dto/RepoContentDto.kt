package zed.rainxch.repopages.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class RepoContentDto(
    val content: String? = null,
    val encoding: String? = null,
)
