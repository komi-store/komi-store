package zed.rainxch.details.presentation

data class DetailsArgs(
    val repositoryId: Long,
    val owner: String,
    val repo: String,
    val isComingFromUpdate: Boolean,
    val sourceHost: String? = null,
    val packageName: String? = null,
)
