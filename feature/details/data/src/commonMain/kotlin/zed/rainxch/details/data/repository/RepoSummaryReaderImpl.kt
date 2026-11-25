package zed.rainxch.details.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlin.coroutines.cancellation.CancellationException
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.REPO_DETAILS
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.REPO_STATS
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.USER_PROFILE
import zed.rainxch.core.data.dto.RepoByIdNetwork
import zed.rainxch.core.data.dto.RepoInfoNetwork
import zed.rainxch.core.data.dto.UserProfileNetwork
import zed.rainxch.core.data.mappers.toSummary
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.BackendException
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.RateLimitedException
import zed.rainxch.core.data.network.RefreshBudgetExhaustedException
import zed.rainxch.core.data.network.RefreshCooldownException
import zed.rainxch.core.data.network.RepoArchivedException
import zed.rainxch.core.data.network.RepoNotFoundException
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.data.network.getRepo
import zed.rainxch.core.data.network.getUser
import zed.rainxch.core.data.network.refreshRepo
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.account.github.GithubUserProfile
import zed.rainxch.core.domain.model.error.RefreshError
import zed.rainxch.core.domain.model.error.RefreshException
import zed.rainxch.details.data.mappers.toDomainProfile
import zed.rainxch.details.data.mappers.toGithubRepoSummary
import zed.rainxch.details.data.mappers.toRepoStats
import zed.rainxch.details.domain.model.RepoStats
import zed.rainxch.details.domain.repository.RepoSummaryReader

internal class RepoSummaryReaderImpl(
    private val clientProvider: GitHubClientProvider,
    private val backendApiClient: BackendApiClient,
    private val cacheManager: CacheManager,
    private val backendFallback: BackendFallback,
    private val forgejo: ForgejoDetailsSource,
    private val logger: KomiStoreLogger,
) : RepoSummaryReader {
    private val httpClient: HttpClient get() = clientProvider.client

    override suspend fun getRepositoryById(id: Long): GithubRepoSummary {
        val cacheKey = DetailsCacheKeys.repoById(id)
        return cacheManager.get<GithubRepoSummary>(cacheKey)?.also { logger.debug("Cache hit for repo id=$id") }
            ?: cacheManager.freshOrStale(
                key = cacheKey,
                ttlMillis = REPO_DETAILS,
                onStale = { logger.debug("Network error, using stale cache for repo id=$id") },
            ) { githubGet<RepoByIdNetwork>("/repositories/$id").map { it.toGithubRepoSummary() } }
    }

    override suspend fun getRepositoryByOwnerAndName(
        owner: String,
        name: String,
        sourceHost: String?,
    ): GithubRepoSummary {
        if (sourceHost != null) return forgejo.repository(owner, name, sourceHost)
        val cacheKey = DetailsCacheKeys.repo(owner, name)
        return cacheManager.get<GithubRepoSummary>(cacheKey)?.also { logger.debug("Cache hit for repo $owner/$name") }
            ?: fromBackend(backendApiClient.getRepo(owner, name).map { it.toSummary() }, cacheKey, REPO_DETAILS, "repo $owner/$name")
            ?: cacheManager.freshOrStale(
                key = cacheKey,
                ttlMillis = REPO_DETAILS,
                onStale = { logger.debug("Network error, using stale cache for $owner/$name") },
            ) { githubGet<RepoByIdNetwork>("/repos/$owner/$name").map { it.toGithubRepoSummary() } }
    }

    override suspend fun refreshRepository(
        owner: String,
        name: String,
    ): GithubRepoSummary {
        val outcome = backendApiClient.refreshRepo(owner, name)
        outcome.exceptionOrNull()?.let { throw it.toRefreshException() }
        val result = outcome.getOrThrow().toSummary()
        cacheManager.put(DetailsCacheKeys.repo(owner, name), result, REPO_DETAILS)
        cacheManager.invalidate(DetailsCacheKeys.repoById(result.id))
        cacheManager.invalidate(DetailsCacheKeys.stats(owner, name))
        cacheManager.invalidate(DetailsCacheKeys.latestRelease(owner, name))
        cacheManager.invalidate(DetailsCacheKeys.releases(owner, name))
        return result
    }

    override suspend fun getRepoStats(
        owner: String,
        repo: String,
        sourceHost: String?,
    ): RepoStats {
        if (sourceHost != null) return forgejo.stats(owner, repo, sourceHost)
        val cacheKey = DetailsCacheKeys.stats(owner, repo)
        return cacheManager.get<RepoStats>(cacheKey)?.also { logger.debug("Cache hit for repo stats $owner/$repo") }
            ?: fromBackend(backendApiClient.getRepo(owner, repo).map { it.toRepoStats() }, cacheKey, REPO_STATS, "repo stats $owner/$repo")
            ?: cacheManager.freshOrStale(
                key = cacheKey,
                ttlMillis = REPO_STATS,
                onStale = { logger.debug("Network error, using stale cache for stats $owner/$repo") },
            ) {
                logger.debug("Backend miss for stats $owner/$repo, falling back to GitHub API")
                githubGet<RepoInfoNetwork>("/repos/$owner/$repo").map { it.toRepoStats() }
            }
    }

    override suspend fun getUserProfile(username: String): GithubUserProfile {
        val cacheKey = DetailsCacheKeys.profile(username)
        return cacheManager.get<GithubUserProfile>(cacheKey)?.also { logger.debug("Cache hit for user profile $username") }
            ?: fromBackend(backendApiClient.getUser(username).map { it.toDomainProfile() }, cacheKey, USER_PROFILE, "profile $username")
            ?: cacheManager.freshOrStale(
                key = cacheKey,
                ttlMillis = USER_PROFILE,
                onStale = { logger.debug("Network error, using stale cache for profile $username") },
            ) { githubGet<UserProfileNetwork>("/users/$username").map { it.toDomainProfile() } }
    }

    private suspend inline fun <reified T> githubGet(path: String): Result<T> =
        httpClient.executeRequest<T> {
            get(path) {
                header(HttpHeaders.Accept, "application/vnd.github+json")
            }
        }

    // Null means the backend failed over, so GitHub answers instead.
    private suspend inline fun <reified T> fromBackend(
        answer: Result<T>,
        cacheKey: String,
        ttlMillis: Long,
        subject: String,
    ): T? =
        answer.fold(
            onSuccess = { value ->
                logger.debug("Backend hit for $subject")
                value.also { cacheManager.put(cacheKey, it, ttlMillis) }
            },
            onFailure = { e ->
                if (backendFallback.failsOverToGithub(e)) {
                    logger.debug("Backend infra error for $subject (${e.message}), falling back to GitHub")
                    null
                } else {
                    cacheManager.staleOrThrow<T>(cacheKey, e) { logger.debug("Backend 4xx for $subject, serving stale cache") }
                }
            },
        )

    private fun Throwable.toRefreshException(): Throwable =
        when (this) {
            is CancellationException -> this
            is RefreshCooldownException -> RefreshException(RefreshError.COOLDOWN, retryAfterSeconds)
            is RefreshBudgetExhaustedException -> RefreshException(RefreshError.BUDGET_EXHAUSTED, retryAfterSeconds)
            is RateLimitedException -> RefreshException(RefreshError.COOLDOWN, retryAfterSeconds)
            is RepoArchivedException -> RefreshException(RefreshError.ARCHIVED)
            is RepoNotFoundException -> RefreshException(RefreshError.NOT_FOUND)
            is BackendException -> RefreshException(if (statusCode in SERVER_ERROR_CODES) RefreshError.UPSTREAM else RefreshError.GENERIC)
            else -> RefreshException(RefreshError.GENERIC)
        }

    private companion object {
        val SERVER_ERROR_CODES = 500..599
    }
}
