package zed.rainxch.repopages.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.networkFailures
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.NetworkException
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.repopages.data.dto.CreateCommentRequest
import zed.rainxch.repopages.data.dto.CreateIssueRequest
import zed.rainxch.repopages.data.dto.CreateReactionRequest
import zed.rainxch.repopages.data.dto.IssueCommentDto
import zed.rainxch.repopages.data.dto.IssueDto
import zed.rainxch.repopages.data.dto.PullRequestDto
import zed.rainxch.repopages.data.dto.RepoContentDto
import zed.rainxch.repopages.data.dto.SecurityAdvisoryDto
import zed.rainxch.repopages.data.mappers.toApiValue
import zed.rainxch.repopages.data.mappers.toIssueComment
import zed.rainxch.repopages.data.mappers.toRepoIssue
import zed.rainxch.repopages.data.mappers.toRepoIssueDetail
import zed.rainxch.repopages.data.mappers.toRepoPullRequest
import zed.rainxch.repopages.data.mappers.toSecurityAdvisory
import zed.rainxch.repopages.domain.model.IssueComment
import zed.rainxch.repopages.domain.model.IssueState
import zed.rainxch.repopages.domain.model.IssuesPage
import zed.rainxch.repopages.domain.model.RepoIssueDetail
import zed.rainxch.repopages.domain.model.RepoPullRequest
import zed.rainxch.repopages.domain.model.SecurityAdvisory
import zed.rainxch.repopages.domain.model.SecurityOverview
import zed.rainxch.repopages.domain.repository.RepoPagesRepository
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class RepoPagesRepositoryImpl(
    private val clientProvider: GitHubClientProvider,
    private val logger: KomiStoreLogger,
) : RepoPagesRepository {
    private val httpClient: HttpClient get() = clientProvider.client

    override suspend fun getIssues(
        owner: String,
        repo: String,
        state: IssueState,
        page: Int,
    ): Result<IssuesPage> =
        githubCall("Failed to fetch issues for $owner/$repo") {
            val response =
                httpClient.get("/repos/$owner/$repo/issues") {
                    parameter("state", state.toApiValue())
                    parameter("per_page", PER_PAGE)
                    parameter("page", page)
                    parameter("sort", "created")
                    parameter("direction", "desc")
                }
            if (!response.status.isSuccess()) {
                return@githubCall Result.failure(NetworkException("Failed to fetch issues: ${response.status.description}", null))
            }
            val dtos: List<IssueDto> = response.body()
            val issues = dtos.filter { it.pullRequest == null }.map { it.toRepoIssue() }
            Result.success(IssuesPage(issues = issues, hasMore = dtos.size >= PER_PAGE))
        }

    override suspend fun getIssueDetail(
        owner: String,
        repo: String,
        number: Int,
    ): Result<RepoIssueDetail> =
        githubCall("Failed to fetch issue $owner/$repo#$number") {
            coroutineScope {
                val issueDeferred = async { httpClient.get("/repos/$owner/$repo/issues/$number") }
                val commentsDeferred =
                    async {
                        httpClient.get("/repos/$owner/$repo/issues/$number/comments") {
                            parameter("per_page", COMMENTS_PER_PAGE)
                        }
                    }

                val issueResponse = issueDeferred.await()
                if (!issueResponse.status.isSuccess()) {
                    return@coroutineScope Result.failure(
                        NetworkException("Failed to fetch issue: ${issueResponse.status.description}", null),
                    )
                }
                val issueDto: IssueDto = issueResponse.body()

                val commentsResponse = commentsDeferred.await()
                val comments =
                    if (commentsResponse.status.isSuccess()) {
                        commentsResponse.body<List<IssueCommentDto>>().map { it.toIssueComment() }
                    } else {
                        emptyList()
                    }

                Result.success(issueDto.toRepoIssueDetail(comments))
            }
        }

    override suspend fun getSecurityOverview(
        owner: String,
        repo: String,
    ): Result<SecurityOverview> =
        githubCall("Failed to fetch security overview for $owner/$repo") {
            coroutineScope {
                val advisoriesDeferred = async { fetchAdvisories(owner, repo) }
                val policyDeferred = async { fetchSecurityPolicy(owner, repo) }
                Result.success(
                    SecurityOverview(
                        advisories = advisoriesDeferred.await(),
                        securityPolicyMarkdown = policyDeferred.await(),
                    ),
                )
            }
        }

    private suspend fun fetchAdvisories(
        owner: String,
        repo: String,
    ): List<SecurityAdvisory> =
        try {
            networkFailures {
                val response =
                    httpClient.get("/repos/$owner/$repo/security-advisories") {
                        parameter("per_page", PER_PAGE)
                        parameter("state", "published")
                    }
                if (response.status.isSuccess()) {
                    response.body<List<SecurityAdvisoryDto>>().map { it.toSecurityAdvisory() }
                } else {
                    emptyList()
                }
            }
        } catch (e: NetworkException) {
            logger.warn("Failed to fetch advisories for $owner/$repo: ${e.message}")
            emptyList()
        }

    private suspend fun fetchSecurityPolicy(
        owner: String,
        repo: String,
    ): String? = SECURITY_PATHS.firstNotNullOfOrNull { path -> readSecurityPolicy(owner, repo, path) }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun readSecurityPolicy(
        owner: String,
        repo: String,
        path: String,
    ): String? =
        try {
            networkFailures {
                val response = httpClient.get("/repos/$owner/$repo/contents/$path")
                val raw = if (response.status.isSuccess()) response.body<RepoContentDto>().content else null
                raw?.let { Base64.Mime.decode(it).decodeToString() }?.takeIf { it.isNotBlank() }
            }
        } catch (e: NetworkException) {
            logger.warn("Failed to read $path for $owner/$repo: ${e.message}")
            null
        }

    override suspend fun addIssueComment(
        owner: String,
        repo: String,
        number: Int,
        body: String,
    ): Result<IssueComment> =
        githubCall("Failed to post comment on $owner/$repo#$number") {
            val response =
                httpClient.post("/repos/$owner/$repo/issues/$number/comments") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateCommentRequest(body = body))
                }
            if (response.status.isSuccess()) {
                Result.success(response.body<IssueCommentDto>().toIssueComment())
            } else {
                Result.failure(authAwareError(response.status, "Failed to post comment"))
            }
        }

    override suspend fun createIssue(
        owner: String,
        repo: String,
        title: String,
        body: String,
    ): Result<Int> =
        githubCall("Failed to create issue on $owner/$repo") {
            val response =
                httpClient.post("/repos/$owner/$repo/issues") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateIssueRequest(title = title, body = body))
                }
            if (response.status.isSuccess()) {
                Result.success(response.body<IssueDto>().number)
            } else {
                Result.failure(authAwareError(response.status, "Failed to create issue"))
            }
        }

    override suspend fun reactToIssue(
        owner: String,
        repo: String,
        number: Int,
    ): Result<Boolean> = postReaction("/repos/$owner/$repo/issues/$number/reactions")

    override suspend fun reactToComment(
        owner: String,
        repo: String,
        commentId: Long,
    ): Result<Boolean> = postReaction("/repos/$owner/$repo/issues/comments/$commentId/reactions")

    private suspend fun postReaction(path: String): Result<Boolean> =
        githubCall("Failed to add reaction on $path") {
            val response =
                httpClient.post(path) {
                    contentType(ContentType.Application.Json)
                    setBody(CreateReactionRequest(content = "+1"))
                }
            if (response.status.isSuccess()) {
                Result.success(response.status == HttpStatusCode.Created)
            } else {
                Result.failure(authAwareError(response.status, "Failed to add reaction"))
            }
        }

    override suspend fun getPullRequests(
        owner: String,
        repo: String,
        state: IssueState,
        page: Int,
    ): Result<List<RepoPullRequest>> =
        githubCall("Failed to fetch pull requests for $owner/$repo") {
            val response =
                httpClient.get("/repos/$owner/$repo/pulls") {
                    parameter("state", state.toApiValue())
                    parameter("per_page", PER_PAGE)
                    parameter("page", page)
                    parameter("sort", "created")
                    parameter("direction", "desc")
                }
            if (response.status.isSuccess()) {
                Result.success(response.body<List<PullRequestDto>>().map { it.toRepoPullRequest() })
            } else {
                Result.failure(NetworkException("Failed to fetch pull requests: ${response.status.description}", null))
            }
        }

    // A failed call comes back as a Result failure, a rate limit included, so the screens show it in place.
    private suspend fun <T> githubCall(
        failureLog: String,
        block: suspend () -> Result<T>,
    ): Result<T> =
        withContext(Dispatchers.IO) {
            try {
                networkFailures { block() }
            } catch (e: NetworkException) {
                logger.error(failureLog, e)
                Result.failure(e)
            } catch (e: RateLimitException) {
                Result.failure(e)
            }
        }

    private fun authAwareError(
        status: HttpStatusCode,
        fallback: String,
    ): NetworkException =
        NetworkException(
            when (status) {
                HttpStatusCode.Unauthorized -> "Authentication required. Please sign in with GitHub."
                HttpStatusCode.Forbidden -> "$fallback (forbidden — you may lack permission or be rate-limited)."
                else -> fallback
            },
            null,
        )

    companion object {
        private const val PER_PAGE = 30
        private const val COMMENTS_PER_PAGE = 100
        private val SECURITY_PATHS =
            listOf(
                "SECURITY.md",
                ".github/SECURITY.md",
                "docs/SECURITY.md",
            )
    }
}
