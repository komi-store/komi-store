package zed.rainxch.core.data.network

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import zed.rainxch.core.data.dto.BackendRepoResponse
import zed.rainxch.core.data.dto.GithubReadmeResponseDto
import zed.rainxch.core.data.dto.GithubRepoNetworkModel
import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.data.dto.UserProfileNetwork

private const val DEFAULT_REFRESH_COOLDOWN_SECONDS = 30L

suspend fun BackendApiClient.getRepo(
    owner: String,
    name: String,
): Result<BackendRepoResponse> =
    request(authed = true) { token ->
        get("repo/$owner/$name") {
            userToken(token)
        }.backendBodyOrFailure()
    }

suspend fun BackendApiClient.refreshRepo(
    owner: String,
    name: String,
): Result<BackendRepoResponse> =
    request(authed = true) { token ->
        val response =
            post("repo/$owner/$name/refresh") {
                userToken(token)
                allowSlowResponse(UPSTREAM_TIMEOUT_MS)
            }
        when {
            response.status.isSuccess() -> Result.success(response.body())
            response.status == HttpStatusCode.TooManyRequests -> Result.failure(response.toRefreshRefusal())
            response.status == HttpStatusCode.NotFound -> Result.failure(RepoNotFoundException())
            response.status == HttpStatusCode.Gone -> Result.failure(RepoArchivedException())
            else -> Result.failure(BackendException(response.status.value))
        }
    }

suspend fun BackendApiClient.getReleases(
    owner: String,
    name: String,
    page: Int = 1,
    perPage: Int = 100,
): Result<List<ReleaseNetwork>> =
    request(authed = true) { token ->
        get("releases/$owner/$name") {
            parameter("page", page)
            parameter("per_page", perPage)
            userToken(token)
            allowSlowResponse(UPSTREAM_TIMEOUT_MS)
        }.backendBodyOrFailure()
    }

suspend fun BackendApiClient.getReadme(
    owner: String,
    name: String,
): Result<GithubReadmeResponseDto> =
    request(authed = true) { token ->
        get("readme/$owner/$name") {
            userToken(token)
            allowSlowResponse(UPSTREAM_TIMEOUT_MS)
        }.backendBodyOrFailure()
    }

suspend fun BackendApiClient.getUserRepos(
    username: String,
    page: Int = 1,
    perPage: Int = 30,
    sort: String? = "updated",
    type: String? = null,
): Result<List<GithubRepoNetworkModel>> =
    request(authed = true) { token ->
        get("users/$username/repos") {
            parameter("page", page)
            parameter("per_page", perPage)
            if (sort != null) parameter("sort", sort)
            if (type != null) parameter("type", type)
            userToken(token)
            allowSlowResponse(UPSTREAM_TIMEOUT_MS)
        }.backendBodyOrFailure()
    }

suspend fun BackendApiClient.getUser(username: String): Result<UserProfileNetwork> =
    request(authed = true) { token ->
        get("user/$username") {
            userToken(token)
            allowSlowResponse(UPSTREAM_TIMEOUT_MS)
        }.backendBodyOrFailure()
    }

// A refresh 429 is the repo's cooldown, the backend's GitHub budget, or plain rate limiting, told apart by the error tag.
private suspend fun HttpResponse.toRefreshRefusal(): Exception {
    val body = errorBodyOrNull()
    val retryAfter =
        body?.longField("retry_after")
            ?: headers["Retry-After"]?.toLongOrNull()
            ?: DEFAULT_REFRESH_COOLDOWN_SECONDS
    return when (body?.stringField("error")) {
        "cooldown" -> {
            RefreshCooldownException(retryAfter)
        }

        "budget_exhausted" -> {
            RefreshBudgetExhaustedException(retryAfter)
        }

        else -> {
            RateLimitedException(
                retryAfterSeconds = retryAfter,
                resetEpochSeconds = headers["X-RateLimit-Reset"]?.toLongOrNull(),
                limit = headers["X-RateLimit-Limit"]?.toIntOrNull(),
            )
        }
    }
}
