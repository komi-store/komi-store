package zed.rainxch.repopages.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateCommentRequest(
    val body: String,
)
