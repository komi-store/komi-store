package zed.rainxch.core.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Serializable
data class GitHubStarredResponseWithTimestamp(
    @SerialName("starred_at") val starredAt: String? = null,
    val repo: GitHubStarredResponse,
)

@Serializable
data class GitHubStarredResponse(
    val id: Long,
    val name: String,
    val owner: Owner,
    val description: String?,
    val language: String?,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("stargazers_count") val stargazersCount: Int,
    @SerialName("forks_count") val forksCount: Int,
    @SerialName("open_issues_count") val openIssuesCount: Int,
    @SerialName("starred_at") val starredAt: String? = null,
) {
    @Serializable
    data class Owner(
        val login: String,
        @SerialName("avatar_url") val avatarUrl: String,
    )
}

// A host that ignores the star media type sends bare repositories: keep them, without star
// times, instead of failing the sync. Parsed by hand because a JsonTransformingSerializer on
// the wrapper makes its generated serializer() ask for itself (ExceptionInInitializerError).
internal fun parseStarredPage(
    json: Json,
    element: JsonElement,
): List<GitHubStarredResponseWithTimestamp> =
    element.jsonArray.map { entry ->
        val obj = entry.jsonObject
        if (REPO_KEY in obj) {
            json.decodeFromJsonElement(GitHubStarredResponseWithTimestamp.serializer(), obj)
        } else {
            GitHubStarredResponseWithTimestamp(
                starredAt = null,
                repo = json.decodeFromJsonElement(GitHubStarredResponse.serializer(), obj),
            )
        }
    }

private const val REPO_KEY = "repo"
