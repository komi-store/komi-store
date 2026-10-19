package zed.rainxch.repopages.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReactionsDto(
    @SerialName("+1") val plusOne: Int = 0,
)
