package zed.rainxch.repopages.data.dto

import kotlinx.serialization.Serializable

@Serializable
data class CreateIssueRequest(
    val title: String,
    val body: String,
)
