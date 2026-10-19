package zed.rainxch.repopages.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class LabelDto(
    val name: String = "",
    val color: String = "888888",
)
