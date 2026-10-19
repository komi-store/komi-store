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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.domain.logging.KomiStoreLogger
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
import zed.rainxch.repopages.data.mappers.toIssueLabel
import zed.rainxch.repopages.data.mappers.toIssueState
import zed.rainxch.repopages.data.mappers.toRepoIssue
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
        withContext(Dispatchers.IO) {
            try {
                val response =
                    httpClient.get("/repos/$owner/$repo/issues") {
                        parameter("state", state.toApiValue())
                        parameter("per_page", PER_PAGE)
                        parameter("page", page)
                        parameter("sort", "created")
                        parameter("direction", "desc")
                    }
                if (!response.status.isSuccess()) {
                    return@withContext Result.failure(
                        Exception("Failed to fetch issues: ${response.status.description}"),
                    )
                }
                val dtos: List<IssueDto> = response.body()
                val issues = dtos.filter { it.pullRequest == null }.map { it.toRepoIssue() }
                Result.success(IssuesPage(issues = issues, hasMore = dtos.size >= PER_PAGE))
            } catch (e: RateLimitException) {
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to fetch issues for $owner/$repo: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun getIssueDetail(
        owner: String,
        repo: String,
        number: Int,
    ): Result<RepoIssueDetail> =
        withContext(Dispatchers.IO) {
            try {
                coroutineScope {
                    val issueDeferred =
                        async {
                            httpClient.get("/repos/$owner/$repo/issues/$number")
                        }
                    val commentsDeferred =
                        async {
                            httpClient.get("/repos/$owner/$repo/issues/$number/comments") {
                                parameter("per_page", COMMENTS_PER_PAGE)
                            }
                        }

                    val issueResponse = issueDeferred.await()
                    if (!issueResponse.status.isSuccess()) {
                        return@coroutineScope Result.failure(
                            Exception("Failed to fetch issue: ${issueResponse.status.description}"),
                        )
                    }
                    val issueDto: IssueDto = issueResponse.body()

                    val commentsResponse = commentsDeferred.await()
                    val comments =
                        if (commentsResponse.status.isSuccess()) {
                            val commentDtos: List<IssueCommentDto> = commentsResponse.body()
                            commentDtos.map { it.toIssueComment() }
                        } else {
                            emptyList()
                        }

                    Result.success(
                        RepoIssueDetail(
                            number = issueDto.number,
                            title = issueDto.title,
                            state = issueDto.state.toIssueState(),
                            authorLogin = issueDto.user?.login.orEmpty(),
                            authorAvatarUrl = issueDto.user?.avatarUrl,
                            bodyMarkdown = issueDto.body.orEmpty(),
                            createdAt = issueDto.createdAt,
                            labels = issueDto.labels.map { it.toIssueLabel() },
                            comments = comments,
                            reactionThumbsUp = issueDto.reactions?.plusOne ?: 0,
                        ),
                    )
                }
            } catch (e: RateLimitException) {
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to fetch issue $owner/$repo#$number: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun getSecurityOverview(
        owner: String,
        repo: String,
    ): Result<SecurityOverview> =
        withContext(Dispatchers.IO) {
            try {
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
            } catch (e: RateLimitException) {
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to fetch security overview for $owner/$repo: ${e.message}")
                Result.failure(e)
            }
        }

    private suspend fun fetchAdvisories(
        owner: String,
        repo: String,
    ): List<SecurityAdvisory> {
        return try {
            val response =
                httpClient.get("/repos/$owner/$repo/security-advisories") {
                    parameter("per_page", PER_PAGE)
                    parameter("state", "published")
                }
            if (!response.status.isSuccess()) return emptyList()
            val dtos: List<SecurityAdvisoryDto> = response.body()
            dtos.map { it.toSecurityAdvisory() }
        } catch (e: RateLimitException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to fetch advisories for $owner/$repo: ${e.message}")
            emptyList()
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun fetchSecurityPolicy(
        owner: String,
        repo: String,
    ): String? {
        for (path in SECURITY_PATHS) {
            try {
                val response = httpClient.get("/repos/$owner/$repo/contents/$path")
                if (response.status == HttpStatusCode.NotFound) continue
                if (!response.status.isSuccess()) continue
                val dto: RepoContentDto = response.body()
                val raw = dto.content ?: continue
                val decoded = Base64.Mime.decode(raw).decodeToString()
                if (decoded.isNotBlank()) return decoded
            } catch (e: RateLimitException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("Failed to read $path for $owner/$repo: ${e.message}")
            }
        }
        return null
    }

    override suspend fun addIssueComment(
        owner: String,
        repo: String,
        number: Int,
        body: String,
    ): Result<IssueComment> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    httpClient.post("/repos/$owner/$repo/issues/$number/comments") {
                        contentType(ContentType.Application.Json)
                        setBody(CreateCommentRequest(body = body))
                    }
                if (!response.status.isSuccess()) {
                    return@withContext Result.failure(
                        authAwareError(response.status.value, "Failed to post comment"),
                    )
                }
                val dto: IssueCommentDto = response.body()
                Result.success(dto.toIssueComment())
            } catch (e: RateLimitException) {
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to post comment on $owner/$repo#$number: ${e.message}")
                Result.failure(e)
            }
        }

    override suspend fun createIssue(
        owner: String,
        repo: String,
        title: String,
        body: String,
    ): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    httpClient.post("/repos/$owner/$repo/issues") {
                        contentType(ContentType.Application.Json)
                        setBody(CreateIssueRequest(title = title, body = body))
                    }
                if (!response.status.isSuccess()) {
                    return@withContext Result.failure(
                        authAwareError(response.status.value, "Failed to create issue"),
                    )
                }
                val dto: IssueDto = response.body()
                Result.success(dto.number)
            } catch (e: RateLimitException) {
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to create issue on $owner/$repo: ${e.message}")
                Result.failure(e)
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
        withContext(Dispatchers.IO) {
            try {
                val response =
                    httpClient.post(path) {
                        contentType(ContentType.Application.Json)
                        setBody(CreateReactionRequest(content = "+1"))
                    }
                if (response.status.isSuccess()) {
                    Result.success(response.status == HttpStatusCode.Created)
                } else {
                    Result.failure(authAwareError(response.status.value, "Failed to add reaction"))
                }
            } catch (e: RateLimitException) {
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getPullRequests(
        owner: String,
        repo: String,
        state: IssueState,
        page: Int,
    ): Result<List<RepoPullRequest>> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    httpClient.get("/repos/$owner/$repo/pulls") {
                        parameter("state", state.toApiValue())
                        parameter("per_page", PER_PAGE)
                        parameter("page", page)
                        parameter("sort", "created")
                        parameter("direction", "desc")
                    }
                if (!response.status.isSuccess()) {
                    return@withContext Result.failure(
                        Exception("Failed to fetch pull requests: ${response.status.description}"),
                    )
                }
                val dtos: List<PullRequestDto> = response.body()
                Result.success(dtos.map { it.toRepoPullRequest() })
            } catch (e: RateLimitException) {
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to fetch pull requests for $owner/$repo: ${e.message}")
                Result.failure(e)
            }
        }

    private fun authAwareError(
        statusCode: Int,
        fallback: String,
    ): Exception =
        when (statusCode) {
            401 -> Exception("Authentication required. Please sign in with GitHub.")
            403 -> Exception("$fallback (forbidden — you may lack permission or be rate-limited).")
            else -> Exception(fallback)
        }

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
