package zed.rainxch.details.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.serialization.SerializationException
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.RELEASES
import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.data.network.getReleases
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.details.data.utils.preprocessGithubMarkdown
import zed.rainxch.details.domain.model.CachedReleases

internal interface ReleaseReads {
    suspend fun getLatestPublishedRelease(
        owner: String,
        repo: String,
        defaultBranch: String,
        sourceHost: String?,
    ): GithubRelease?

    suspend fun getAllReleases(read: ReleaseRead): List<GithubRelease>

    suspend fun getCachedReleases(
        owner: String,
        repo: String,
        sourceHost: String?,
    ): CachedReleases?
}

internal data class ReleaseRead(
    val owner: String,
    val repo: String,
    val defaultBranch: String,
    val sourceHost: String?,
    val bypassCache: Boolean,
    val allowStale: Boolean,
    val preferDirectSource: Boolean,
) {
    val cacheKey: String get() = DetailsCacheKeys.releases(owner, repo)
    val subject: String get() = "$owner/$repo"
}

internal class ReleaseReader(
    private val clientProvider: GitHubClientProvider,
    private val backendApiClient: BackendApiClient,
    private val cacheManager: CacheManager,
    private val backendFallback: BackendFallback,
    private val forgejo: ForgejoDetailsSource,
    private val logger: KomiStoreLogger,
) : ReleaseReads {
    private val httpClient: HttpClient get() = clientProvider.client

    override suspend fun getLatestPublishedRelease(
        owner: String,
        repo: String,
        defaultBranch: String,
        sourceHost: String?,
    ): GithubRelease? {
        if (sourceHost != null) return forgejo.releases(owner, repo, sourceHost).firstOrNull { !it.isPrerelease }
        val cacheKey = DetailsCacheKeys.latestRelease(owner, repo)
        return cacheManager.get<GithubRelease>(cacheKey)?.also { logger.debug("Cache hit for latest release $owner/$repo") }
            ?: fetchLatestPublished(owner, repo, defaultBranch, cacheKey)
    }

    override suspend fun getCachedReleases(
        owner: String,
        repo: String,
        sourceHost: String?,
    ): CachedReleases? {
        if (sourceHost != null) return null
        val cacheKey = DetailsCacheKeys.releases(owner, repo)
        val cached = cacheManager.get<List<GithubRelease>>(cacheKey)?.takeIf { it.isNotEmpty() }
        val cachedAt = cacheManager.getCachedAt(cacheKey)
        return if (cached != null && cachedAt != null) CachedReleases(cached, cachedAt) else null
    }

    override suspend fun getAllReleases(read: ReleaseRead): List<GithubRelease> {
        if (read.sourceHost != null) return forgejo.releases(read.owner, read.repo, read.sourceHost)
        val cached =
            if (read.bypassCache) {
                null
            } else {
                cacheManager.get<List<GithubRelease>>(read.cacheKey)?.takeIf { it.isNotEmpty() }?.also { releases ->
                    logger.debug("Cache hit for all releases ${read.subject}: ${releases.size} releases")
                }
            }
        return cached ?: if (read.preferDirectSource) readHostThenBackend(read) else readBackend(read, hostTried = false)
    }

    private suspend fun fetchLatestPublished(
        owner: String,
        repo: String,
        defaultBranch: String,
        cacheKey: String,
    ): GithubRelease? {
        val releases =
            try {
                githubReleases(owner, repo, LATEST_RELEASE_WINDOW).getOrNull()
            } catch (e: RateLimitException) {
                return cacheManager.staleOrThrow<GithubRelease>(cacheKey, e) {
                    logger.debug("Network error, using stale cache for latest release $owner/$repo")
                }
            }
        return releases
            ?.asSequence()
            ?.filter { it.draft != true && it.prerelease != true }
            ?.maxByOrNull { it.publishedAt ?: it.createdAt ?: "" }
            ?.let { latest -> latest.copy(body = releaseBody(latest.body, owner, repo, defaultBranch)).toDomain() }
            ?.also { cacheManager.put(cacheKey, it, RELEASES) }
    }

    // The backend answers from a copy up to an hour old with no way to force it, so a user-asked read tries the host first.
    private suspend fun readHostThenBackend(read: ReleaseRead): List<GithubRelease> {
        val direct =
            readHost(read).getOrElse { e ->
                logParseFailure(e, read)
                logger.debug("Direct releases read failed for ${read.subject} (${e.message}), trying the backend")
                emptyList()
            }
        return direct.ifEmpty { readBackend(read, hostTried = true) }
    }

    private suspend fun readBackend(
        read: ReleaseRead,
        hostTried: Boolean,
    ): List<GithubRelease> =
        backendApiClient.getReleases(read.owner, read.repo).fold(
            onSuccess = { releases ->
                val result = releases.toDomainReleases(read.owner, read.repo, read.defaultBranch)
                when {
                    result.isNotEmpty() -> result.also { cacheManager.put(read.cacheKey, it, RELEASES) }

                    // The backend's copy can lag behind the host, so an empty answer is checked there unless the host was just read.
                    hostTried -> result

                    else -> readHostWhenBackendEmpty(read)
                }
            },
            onFailure = { e ->
                if (!backendFallback.failsOverToGithub(e) || hostTried) {
                    staleReleases(read, "Backend 4xx for releases ${read.subject}, serving stale cache") ?: throw e
                } else {
                    logger.debug("Backend infra error for releases ${read.subject} (${e.message}), falling back to GitHub")
                    readHost(read).getOrElse { hostFailure -> staleAfterHostFailure(hostFailure, read) ?: throw hostFailure }
                }
            },
        )

    // Only a rate limit fails this read. Any other host failure, such as a blocked api.github.com, keeps the backend's empty answer.
    private suspend fun readHostWhenBackendEmpty(read: ReleaseRead): List<GithubRelease> =
        readHost(read).getOrElse { e ->
            staleAfterHostFailure(e, read)
                ?: if (e is RateLimitException) {
                    throw e
                } else {
                    logger.debug("Direct releases read failed for empty backend ${read.subject}: ${e.message}")
                    emptyList()
                }
        }

    private suspend fun readHost(read: ReleaseRead): Result<List<GithubRelease>> =
        catchingRateLimit { githubReleases(read.owner, read.repo, RELEASES_PAGE_SIZE) }
            .map { it.toDomainReleases(read.owner, read.repo, read.defaultBranch) }
            .onSuccess { if (it.isNotEmpty()) cacheManager.put(read.cacheKey, it, RELEASES) }

    private suspend fun githubReleases(
        owner: String,
        repo: String,
        perPage: Int,
    ): Result<List<ReleaseNetwork>> =
        httpClient.executeRequest<List<ReleaseNetwork>> {
            get("/repos/$owner/$repo/releases") {
                header(HttpHeaders.Accept, "application/vnd.github+json")
                parameter("per_page", perPage)
            }
        }

    private suspend fun staleAfterHostFailure(
        cause: Throwable,
        read: ReleaseRead,
    ): List<GithubRelease>? {
        logParseFailure(cause, read)
        val logMessage =
            if (cause is SerializationException) {
                "Serving stale cache for releases ${read.subject} after parse failure"
            } else {
                "Network error, using stale cache for releases ${read.subject}"
            }
        return staleReleases(read, logMessage)
    }

    private suspend fun staleReleases(
        read: ReleaseRead,
        logMessage: String,
    ): List<GithubRelease>? {
        if (!read.allowStale) return null
        return cacheManager.getStale<List<GithubRelease>>(read.cacheKey)?.also { logger.debug(logMessage) }
    }

    private fun logParseFailure(
        cause: Throwable,
        read: ReleaseRead,
    ) {
        if (cause is SerializationException) logger.error("Failed to parse releases for ${read.subject}: ${cause.message}", cause)
    }

    private fun List<ReleaseNetwork>.toDomainReleases(
        owner: String,
        repo: String,
        defaultBranch: String,
    ): List<GithubRelease> =
        filter { it.draft != true }
            .map { release -> release.copy(body = releaseBody(release.body, owner, repo, defaultBranch)).toDomain() }
            .sortedByDescending { it.publishedAt }

    private fun releaseBody(
        body: String?,
        owner: String,
        repo: String,
        defaultBranch: String,
    ): String? = body?.replace("\r\n", "\n")?.let { preprocessGithubMarkdown(it, owner, repo, defaultBranch) }

    private companion object {
        const val LATEST_RELEASE_WINDOW = 10
        const val RELEASES_PAGE_SIZE = 30
    }
}
