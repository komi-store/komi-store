package zed.rainxch.details.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.README
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.RELEASES
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.REPO_DETAILS
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.REPO_STATS
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.USER_PROFILE
import zed.rainxch.core.data.dto.GithubReadmeResponseDto
import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.data.dto.RepoByIdNetwork
import zed.rainxch.core.data.dto.RepoInfoNetwork
import zed.rainxch.core.data.dto.UserProfileNetwork
import zed.rainxch.details.data.dto.AttestationsResponse
import zed.rainxch.core.data.dto.BackendRepoResponse
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toSummary
import zed.rainxch.core.data.data_source.TokenStore
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.BackendException
import zed.rainxch.core.data.network.RateLimitedException
import zed.rainxch.core.data.network.RefreshBudgetExhaustedException
import zed.rainxch.core.data.network.RefreshCooldownException
import zed.rainxch.core.data.network.RepoArchivedException
import zed.rainxch.core.data.network.RepoNotFoundException
import zed.rainxch.core.data.network.shouldFallbackToGithubOrRethrow as sharedShouldFallback
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.data.services.LocalizationManager
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.account.github.GithubUser
import zed.rainxch.core.domain.model.account.github.GithubUserProfile
import zed.rainxch.core.domain.model.error.RefreshError
import zed.rainxch.core.domain.model.error.RefreshException
import zed.rainxch.core.domain.utils.RepoIdCodec
import zed.rainxch.details.data.utils.ReadmeLocalizationHelper
import zed.rainxch.details.data.utils.preprocessMarkdown
import zed.rainxch.details.domain.model.CachedReleases
import zed.rainxch.details.domain.model.RepoStats
import zed.rainxch.details.domain.repository.DetailsRepository
import kotlin.coroutines.cancellation.CancellationException

class DetailsRepositoryImpl(
    private val clientProvider: GitHubClientProvider,
    private val backendApiClient: BackendApiClient,
    private val localizationManager: LocalizationManager,
    private val logger: KomiStoreLogger,
    private val cacheManager: CacheManager,
    private val forgejoClientRegistry: zed.rainxch.core.data.network.ForgejoClientRegistry,
    private val tokenStore: TokenStore,
) : DetailsRepository {
    private val httpClient: HttpClient get() = clientProvider.client

    private suspend fun isSignedIn(): Boolean =
        try {
            tokenStore.currentToken()?.accessToken?.trim()?.isNotEmpty() == true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }

    private fun zed.rainxch.core.data.dto.ForgejoRepoNetworkModel.toForgejoSummary(
        sourceHost: String,
    ): GithubRepoSummary = GithubRepoSummary(
        id = RepoIdCodec.encode(sourceHost, id),
        name = name,
        fullName = fullName ?: "${owner.login}/$name",
        owner = GithubUser(
            id = owner.id,
            login = owner.login,
            avatarUrl = owner.avatarUrl,
            htmlUrl = owner.htmlUrl,
        ),
        description = description,
        htmlUrl = htmlUrl,
        stargazersCount = starsCount,
        forksCount = forksCount,
        language = language,
        topics = null,
        releasesUrl = "$htmlUrl/releases",
        updatedAt = updatedAt ?: "",
        pushedAt = pushedAt,
        defaultBranch = defaultBranch ?: "main",
        sourceHost = sourceHost,
    )

    @Serializable
    private data class CachedReadme(
        val content: String,
        val languageCode: String?,
        val path: String,
    )

    private val readmeHelper = ReadmeLocalizationHelper(localizationManager)

    private fun shouldFallbackToGithubOrRethrow(cause: Throwable, isSignedIn: Boolean): Boolean =
        sharedShouldFallback(cause, isSignedIn)

    private fun BackendRepoResponse.toBackendSummary(): GithubRepoSummary = toSummary()

    private fun RepoByIdNetwork.toGithubRepoSummary(): GithubRepoSummary =
        GithubRepoSummary(
            id = id,
            name = name,
            fullName = fullName,
            owner =
                GithubUser(
                    id = owner.id,
                    login = owner.login,
                    avatarUrl = owner.avatarUrl,
                    htmlUrl = owner.htmlUrl,
                ),
            description = description,
            htmlUrl = htmlUrl,
            stargazersCount = stars,
            forksCount = forks,
            language = language,
            topics = topics,
            releasesUrl = "https://api.github.com/repos/${owner.login}/$name/releases{/id}",
            updatedAt = updatedAt,
            pushedAt = pushedAt,
            defaultBranch = defaultBranch,
        )

    override suspend fun getRepositoryById(id: Long): GithubRepoSummary {
        val cacheKey = "details:repo_id:$id"

        cacheManager.get<GithubRepoSummary>(cacheKey)?.let { cached ->
            logger.debug("Cache hit for repo id=$id")
            return cached
        }

        return try {
            val result =
                httpClient
                    .executeRequest<RepoByIdNetwork> {
                        get("/repositories/$id") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                        }
                    }.getOrThrow()
                    .toGithubRepoSummary()
            cacheManager.put(cacheKey, result, REPO_DETAILS)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cacheManager.getStale<GithubRepoSummary>(cacheKey)?.let { stale ->
                logger.debug("Network error, using stale cache for repo id=$id")
                return stale
            }
            throw e
        }
    }

    override suspend fun getRepositoryByOwnerAndName(
        owner: String,
        name: String,
        sourceHost: String?,
    ): GithubRepoSummary {
        if (sourceHost != null) return getForgejoRepository(owner, name, sourceHost)
        val cacheKey = "details:repo:$owner/$name"

        cacheManager.get<GithubRepoSummary>(cacheKey)?.let { cached ->
            logger.debug("Cache hit for repo $owner/$name")
            return cached
        }

        val backendResult = backendApiClient.getRepo(owner, name)
        backendResult.fold(
            onSuccess = { backendRepo ->
                logger.debug("Backend hit for repo $owner/$name")
                val result = backendRepo.toBackendSummary()
                cacheManager.put(cacheKey, result, REPO_DETAILS)
                return result
            },
            onFailure = { e ->
                if (!shouldFallbackToGithubOrRethrow(e, isSignedIn())) {

                    cacheManager.getStale<GithubRepoSummary>(cacheKey)?.let { stale ->
                        logger.debug("Backend 4xx for $owner/$name, serving stale cache")
                        return stale
                    }
                    throw e
                }
                logger.debug("Backend infra error for $owner/$name (${e.message}), falling back to GitHub")
            },
        )

        return try {
            val result =
                httpClient
                    .executeRequest<RepoByIdNetwork> {
                        get("/repos/$owner/$name") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                        }
                    }.getOrThrow()
                    .toGithubRepoSummary()

            cacheManager.put(cacheKey, result, REPO_DETAILS)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cacheManager.getStale<GithubRepoSummary>(cacheKey)?.let { stale ->
                logger.debug("Network error, using stale cache for $owner/$name")
                return stale
            }
            throw e
        }
    }

    override suspend fun refreshRepository(
        owner: String,
        name: String,
    ): GithubRepoSummary {
        val outcome = backendApiClient.refreshRepo(owner, name)
        outcome.exceptionOrNull()?.let { throw it.toRefreshException() }
        val backendRepo = outcome.getOrThrow()
        val result = backendRepo.toBackendSummary()
        val cacheKey = "details:repo:$owner/$name"
        cacheManager.put(cacheKey, result, REPO_DETAILS)
        cacheManager.invalidate("details:repo_id:${result.id}")
        cacheManager.invalidate("details:stats:v3:$owner/$name")
        cacheManager.invalidate("details:latest_release:$owner/$name")
        cacheManager.invalidate("details:releases:$owner/$name")
        return result
    }

    private fun Throwable.toRefreshException(): Throwable =
        when (this) {
            is CancellationException -> this
            is RefreshCooldownException ->
                RefreshException(RefreshError.COOLDOWN, retryAfterSeconds)
            is RefreshBudgetExhaustedException ->
                RefreshException(RefreshError.BUDGET_EXHAUSTED, retryAfterSeconds)
            is RateLimitedException ->
                RefreshException(RefreshError.COOLDOWN, retryAfterSeconds)
            is RepoArchivedException ->
                RefreshException(RefreshError.ARCHIVED)
            is RepoNotFoundException ->
                RefreshException(RefreshError.NOT_FOUND)
            is BackendException -> RefreshException(
                if (statusCode in 500..599) RefreshError.UPSTREAM else RefreshError.GENERIC,
            )
            else -> RefreshException(RefreshError.GENERIC)
        }

    override suspend fun getLatestPublishedRelease(
        owner: String,
        repo: String,
        defaultBranch: String,
        sourceHost: String?,
    ): GithubRelease? {
        if (sourceHost != null) {
            return getForgejoAllReleases(owner, repo, sourceHost)
                .firstOrNull { !it.isPrerelease }
        }
        val cacheKey = "details:latest_release:$owner/$repo"

        cacheManager.get<GithubRelease>(cacheKey)?.let { cached ->
            logger.debug("Cache hit for latest release $owner/$repo")
            return cached
        }

        return try {
            val releases =
                httpClient
                    .executeRequest<List<ReleaseNetwork>> {
                        get("/repos/$owner/$repo/releases") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                            parameter("per_page", 10)
                        }
                    }.getOrNull() ?: return null

            val latest =
                releases
                    .asSequence()
                    .filter { (it.draft != true) && (it.prerelease != true) }
                    .maxByOrNull { it.publishedAt ?: it.createdAt ?: "" }
                    ?: return null

            val result =
                latest
                    .copy(
                        body = processReleaseBody(latest.body, owner, repo, defaultBranch),
                    ).toDomain()

            cacheManager.put(cacheKey, result, RELEASES)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cacheManager.getStale<GithubRelease>(cacheKey)?.let { stale ->
                logger.debug("Network error, using stale cache for latest release $owner/$repo")
                return stale
            }
            throw e
        }
    }

    override suspend fun getCachedReleases(
        owner: String,
        repo: String,
        sourceHost: String?,
    ): CachedReleases? {
        if (sourceHost != null) return null
        val cacheKey = "details:releases:$owner/$repo"
        val cached = cacheManager.get<List<GithubRelease>>(cacheKey) ?: return null
        if (cached.isEmpty()) return null
        val cachedAt = cacheManager.getCachedAt(cacheKey) ?: return null
        return CachedReleases(releases = cached, cachedAtEpochMs = cachedAt)
    }

    override suspend fun getAllReleases(
        owner: String,
        repo: String,
        defaultBranch: String,
        sourceHost: String?,
        bypassCache: Boolean,
        allowStale: Boolean,
        preferDirectSource: Boolean,
    ): List<GithubRelease> {
        if (sourceHost != null) return getForgejoAllReleases(owner, repo, sourceHost)
        val cacheKey = "details:releases:$owner/$repo"

        if (!bypassCache) {
            cacheManager.get<List<GithubRelease>>(cacheKey)?.let { cached ->
                if (cached.isNotEmpty()) {
                    logger.debug("Cache hit for all releases $owner/$repo: ${cached.size} releases")
                    return cached
                }
            }
        }

        // A read the user explicitly asked for can go to the repository host first: the host is
        // where an edit lands, while the backend answers from a copy it keeps for an hour and
        // offers nothing to force it. The backend stays next in line, so a host read that fails
        // or comes back with nothing still lands on live data.
        var triedDirect = false
        if (preferDirectSource) {
            triedDirect = true
            val direct =
                try {
                    readReleasesFromGitHub(owner, repo, defaultBranch, cacheKey, allowStale)
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    logger.debug(
                        "Direct releases read failed for $owner/$repo (${t.message}), trying the backend",
                    )
                    emptyList()
                }
            if (direct.isNotEmpty()) return direct
        }

        val backendResult = backendApiClient.getReleases(owner, repo)
        backendResult.fold(
            onSuccess = { releases ->
                val result = releases
                    .filter { it.draft != true }
                    .map { release ->
                        release.copy(
                            body = processReleaseBody(release.body, owner, repo, defaultBranch),
                        ).toDomain()
                    }.sortedByDescending { it.publishedAt }
                if (result.isNotEmpty()) {
                    cacheManager.put(cacheKey, result, RELEASES)
                }
                return result
            },
            onFailure = { e ->
                // With the host read already tried first, there is nothing left to fall over to.
                if (!shouldFallbackToGithubOrRethrow(e, isSignedIn()) || triedDirect) {
                    if (allowStale) {
                        cacheManager.getStale<List<GithubRelease>>(cacheKey)?.let { stale ->
                            logger.debug("Backend 4xx for releases $owner/$repo, serving stale cache")
                            return stale
                        }
                    }
                    throw e
                }
                logger.debug("Backend infra error for releases $owner/$repo (${e.message}), falling back to GitHub")
            },
        )

        return readReleasesFromGitHub(owner, repo, defaultBranch, cacheKey, allowStale)
    }

    // The repository-host read — the request the fallback path has always made, now shared with
    // the refresh that goes to the host first.
    private suspend fun readReleasesFromGitHub(
        owner: String,
        repo: String,
        defaultBranch: String,
        cacheKey: String,
        allowStale: Boolean,
    ): List<GithubRelease> {
        return try {
            val releases =
                httpClient
                    .executeRequest<List<ReleaseNetwork>> {
                        get("/repos/$owner/$repo/releases") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                            parameter("per_page", 30)
                        }
                    }.getOrNull() ?: return emptyList()

            val result =
                releases
                    .filter { it.draft != true }
                    .map { release ->
                        release
                            .copy(
                                body = processReleaseBody(release.body, owner, repo, defaultBranch),
                            ).toDomain()
                    }.sortedByDescending { it.publishedAt }

            if (result.isNotEmpty()) {
                cacheManager.put(cacheKey, result, RELEASES)
            }
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: SerializationException) {

            logger.error("Failed to parse releases for $owner/$repo: ${e.message}", e)
            if (allowStale) {
                cacheManager.getStale<List<GithubRelease>>(cacheKey)?.let { stale ->
                    logger.debug("Serving stale cache for releases $owner/$repo after parse failure")
                    return stale
                }
            }
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (allowStale) {
                cacheManager.getStale<List<GithubRelease>>(cacheKey)?.let { stale ->
                    logger.debug("Network error, using stale cache for releases $owner/$repo")
                    return stale
                }
            }
            throw e
        }
    }

    private fun processReleaseBody(
        body: String?,
        owner: String,
        repo: String,
        defaultBranch: String,
    ): String? =
        body
            ?.replace("\r\n", "\n")
            ?.let { rawMarkdown ->
                preprocessMarkdown(
                    markdown = rawMarkdown,
                    baseUrl = "https://raw.githubusercontent.com/$owner/$repo/$defaultBranch/",
                    linkBaseUrl = "https://github.com/$owner/$repo/blob/$defaultBranch/",
                )
            }

    override suspend fun getReadme(
        owner: String,
        repo: String,
        defaultBranch: String,
        sourceHost: String?,
    ): Triple<String, String?, String>? {
        if (sourceHost != null) return getForgejoReadme(owner, repo, defaultBranch, sourceHost)

        val cacheKey = "details:readme:v4:$owner/$repo"

        cacheManager.get<CachedReadme>(cacheKey)?.let { cached ->
            logger.debug("Cache hit for readme $owner/$repo")
            return Triple(cached.content, cached.languageCode, cached.path)
        }

        val backendResult = backendApiClient.getReadme(owner, repo)
        backendResult.fold(
            onSuccess = { dto ->
                val processed = processReadmeFromBackend(dto, owner, repo, defaultBranch)
                if (processed != null) {
                    cacheManager.put(
                        cacheKey,
                        CachedReadme(
                            content = processed.first,
                            languageCode = processed.second,
                            path = processed.third,
                        ),
                        README,
                    )
                    return processed
                }

                logger.debug("Backend readme decode failed for $owner/$repo, falling back to raw URL")
            },
            onFailure = { e ->
                if (!shouldFallbackToGithubOrRethrow(e, isSignedIn())) {
                    cacheManager.getStale<CachedReadme>(cacheKey)?.let { stale ->
                        logger.debug("Backend 4xx for readme $owner/$repo, serving stale cache")
                        return Triple(stale.content, stale.languageCode, stale.path)
                    }

                    return null
                }
                logger.debug("Backend infra error for readme $owner/$repo (${e.message}), falling back to raw URL")
            },
        )

        val result = fetchReadmeFromApi(owner, repo, defaultBranch)

        if (result != null) {
            val cachedReadme =
                CachedReadme(
                    content = result.first,
                    languageCode = result.second,
                    path = result.third,
                )
            cacheManager.put(cacheKey, cachedReadme, README)
        }

        return result
    }

    @OptIn(ExperimentalEncodingApi::class)
    private fun processReadmeFromBackend(
        dto: GithubReadmeResponseDto,
        owner: String,
        repo: String,
        defaultBranch: String,
    ): Triple<String, String?, String>? {

        val rawContent = dto.content ?: return null
        val decoded = try {
            Base64.Mime.decode(rawContent).decodeToString()
        } catch (e: IllegalArgumentException) {
            logger.warn("Failed to base64-decode backend readme for $owner/$repo: ${e.message}")
            return null
        }
        val path = dto.path?.takeIf { it.isNotBlank() } ?: "README.md"
        val baseUrl = "https://raw.githubusercontent.com/$owner/$repo/$defaultBranch/"
        val linkBaseUrl = "https://github.com/$owner/$repo/blob/$defaultBranch/"
        val processed = preprocessMarkdown(markdown = decoded, baseUrl = baseUrl, linkBaseUrl = linkBaseUrl)
        val detectedLang = readmeHelper.detectReadmeLanguage(processed)
        logger.debug("Fetched README via backend (detected language: ${detectedLang ?: "unknown"})")
        return Triple(processed, detectedLang, path)
    }

    private suspend fun fetchReadmeFromApi(
        owner: String,
        repo: String,
        defaultBranch: String,
    ): Triple<String, String?, String>? {
        val baseUrl = "https://raw.githubusercontent.com/$owner/$repo/$defaultBranch/"
        val path = "README.md"

        return try {
            val rawMarkdown =
                httpClient
                    .executeRequest<String> {
                        get("$baseUrl$path")
                    }.getOrNull()

            if (rawMarkdown != null) {
                val linkBaseUrl = "https://github.com/$owner/$repo/blob/$defaultBranch/"
                val processed = preprocessMarkdown(markdown = rawMarkdown, baseUrl = baseUrl, linkBaseUrl = linkBaseUrl)
                val detectedLang = readmeHelper.detectReadmeLanguage(processed)
                logger.debug("Fetched README.md (detected language: ${detectedLang ?: "unknown"})")
                Triple(processed, detectedLang, path)
            } else {
                logger.error("Failed to fetch README.md for $owner/$repo")
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.error("Failed to fetch README.md: ${e.message}")
            null
        }
    }

    override suspend fun getRepoStats(
        owner: String,
        repo: String,
        sourceHost: String?,
    ): RepoStats {
        if (sourceHost != null) return getForgejoRepoStats(owner, repo, sourceHost)

        val cacheKey = "details:stats:v3:$owner/$repo"

        cacheManager.get<RepoStats>(cacheKey)?.let { cached ->
            logger.debug("Cache hit for repo stats $owner/$repo")
            return cached
        }

        val backendResult = backendApiClient.getRepo(owner, repo)
        backendResult.fold(
            onSuccess = { backendRepo ->
                logger.debug("Backend hit for repo stats $owner/$repo")
                val result = RepoStats(
                    stars = backendRepo.stargazersCount,
                    forks = backendRepo.forksCount,
                    openIssues = backendRepo.openIssuesCount,
                    license = backendRepo.license?.spdxId ?: backendRepo.license?.name,
                    totalDownloads = backendRepo.downloadCount,
                )
                cacheManager.put(cacheKey, result, REPO_STATS)
                return result
            },
            onFailure = { e ->
                if (!shouldFallbackToGithubOrRethrow(e, isSignedIn())) {
                    cacheManager.getStale<RepoStats>(cacheKey)?.let { stale ->
                        logger.debug("Backend 4xx for stats $owner/$repo, serving stale cache")
                        return stale
                    }
                    throw e
                }
                logger.debug("Backend infra error for stats $owner/$repo (${e.message}), falling back to GitHub")
            },
        )

        return try {
            logger.debug("Backend miss for stats $owner/$repo, falling back to GitHub API")
            val info =
                httpClient
                    .executeRequest<RepoInfoNetwork> {
                        get("/repos/$owner/$repo") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                        }
                    }.getOrThrow()

            val result =
                RepoStats(
                    stars = info.stars,
                    forks = info.forks,
                    openIssues = info.openIssues,
                    license = info.license?.spdxId ?: info.license?.name,
                    totalDownloads = 0,
                )

            cacheManager.put(cacheKey, result, REPO_STATS)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cacheManager.getStale<RepoStats>(cacheKey)?.let { stale ->
                logger.debug("Network error, using stale cache for stats $owner/$repo")
                return stale
            }
            throw e
        }
    }

    override suspend fun getUserProfile(username: String): GithubUserProfile {
        val cacheKey = "details:profile:$username"

        cacheManager.get<GithubUserProfile>(cacheKey)?.let { cached ->
            logger.debug("Cache hit for user profile $username")
            return cached
        }

        val backendResult = backendApiClient.getUser(username)
        backendResult.fold(
            onSuccess = { user ->
                val result = user.toDomainProfile()
                cacheManager.put(cacheKey, result, USER_PROFILE)
                return result
            },
            onFailure = { e ->
                if (!shouldFallbackToGithubOrRethrow(e, isSignedIn())) {
                    cacheManager.getStale<GithubUserProfile>(cacheKey)?.let { stale ->
                        logger.debug("Backend 4xx for profile $username, serving stale cache")
                        return stale
                    }
                    throw e
                }
                logger.debug("Backend infra error for profile $username (${e.message}), falling back to GitHub")
            },
        )

        return try {
            val user =
                httpClient
                    .executeRequest<UserProfileNetwork> {
                        get("/users/$username") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                        }
                    }.getOrThrow()

            val result = user.toDomainProfile()
            cacheManager.put(cacheKey, result, USER_PROFILE)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cacheManager.getStale<GithubUserProfile>(cacheKey)?.let { stale ->
                logger.debug("Network error, using stale cache for profile $username")
                return stale
            }
            throw e
        }
    }

    private fun UserProfileNetwork.toDomainProfile(): GithubUserProfile =
        GithubUserProfile(
            id = id,
            login = login,
            name = name,
            bio = bio,
            avatarUrl = avatarUrl,
            htmlUrl = htmlUrl,
            followers = followers,
            following = following,
            publicRepos = publicRepos,
            location = location,
            company = company,
            blog = blog,
            twitterUsername = twitterUsername,
        )

    private suspend fun getForgejoRepository(
        owner: String,
        name: String,
        sourceHost: String,
    ): GithubRepoSummary {
        val cacheKey = "details:repo:forgejo:$sourceHost:$owner/$name"
        cacheManager.get<GithubRepoSummary>(cacheKey)?.let { return it }
        val client = forgejoClientRegistry.clientFor(sourceHost)
        return try {
            val result = client.getRepository(owner, name).getOrThrow()
                .toForgejoSummary(sourceHost)
            cacheManager.put(cacheKey, result, REPO_DETAILS)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cacheManager.getStale<GithubRepoSummary>(cacheKey)?.let { return it }
            throw e
        }
    }

    private suspend fun getForgejoAllReleases(
        owner: String,
        repo: String,
        sourceHost: String,
    ): List<GithubRelease> {

        val cacheKey = "details:releases:forgejo:v2:$sourceHost:$owner/$repo"
        cacheManager.get<List<GithubRelease>>(cacheKey)?.takeIf { it.isNotEmpty() }?.let { return it }
        val client = forgejoClientRegistry.clientFor(sourceHost)
        return try {
            val releases = client.getReleases(owner, repo).getOrNull().orEmpty()
            val result = releases
                .filter { it.draft != true }
                .map { network ->
                    val processedBody = network.body?.let {
                        processForgejoBody(it, sourceHost, owner, repo)
                    }
                    network.copy(body = processedBody).toDomain()
                }
                .sortedByDescending { it.publishedAt }
            if (result.isNotEmpty()) cacheManager.put(cacheKey, result, RELEASES)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cacheManager.getStale<List<GithubRelease>>(cacheKey)?.let { return it }
            throw e
        }
    }

    private fun processForgejoBody(
        body: String,
        sourceHost: String,
        owner: String,
        repo: String,
    ): String {

        val normalized = body.replace("\r\n", "\n")
        val baseUrl = "https://$sourceHost/$owner/$repo/raw/branch/HEAD/"
        val linkBaseUrl = "https://$sourceHost/$owner/$repo/src/branch/HEAD/"
        return preprocessMarkdown(markdown = normalized, baseUrl = baseUrl, linkBaseUrl = linkBaseUrl)
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun getForgejoReadme(
        owner: String,
        repo: String,
        defaultBranch: String,
        sourceHost: String,
    ): Triple<String, String?, String>? {

        val cacheKey = "details:readme:forgejo:v2:$sourceHost:$owner/$repo"
        cacheManager.get<CachedReadme>(cacheKey)?.let {
            return Triple(it.content, it.languageCode, it.path)
        }
        val client = forgejoClientRegistry.clientFor(sourceHost)

        val dto = client.getContentsFile(owner, repo, "README.md", defaultBranch).getOrNull()
            ?: client.listContentsRoot(owner, repo, defaultBranch).getOrNull()
                ?.firstOrNull { entry ->
                    entry.type == "file" && entry.name?.let { READMEFileNameRegex.matches(it) } == true
                }
                ?.let { entry ->
                    client.getContentsFile(owner, repo, entry.path ?: entry.name!!, defaultBranch).getOrNull()
                }
            ?: run {
                cacheManager.getStale<CachedReadme>(cacheKey)?.let {
                    return Triple(it.content, it.languageCode, it.path)
                }
                return null
            }

        val rawContent = dto.content ?: return null
        val decoded = try {
            Base64.Mime.decode(rawContent).decodeToString()
        } catch (e: IllegalArgumentException) {
            logger.warn("Failed to decode Forgejo readme for $sourceHost/$owner/$repo: ${e.message}")
            return null
        }
        val path = dto.path?.takeIf { it.isNotBlank() } ?: "README.md"

        val baseUrl = "https://$sourceHost/$owner/$repo/raw/branch/$defaultBranch/"
        val linkBaseUrl = "https://$sourceHost/$owner/$repo/src/branch/$defaultBranch/"
        val processed = preprocessMarkdown(markdown = decoded, baseUrl = baseUrl, linkBaseUrl = linkBaseUrl)
        val detected = readmeHelper.detectReadmeLanguage(processed)
        cacheManager.put(
            cacheKey,
            CachedReadme(content = processed, languageCode = detected, path = path),
            README,
        )
        return Triple(processed, detected, path)
    }

    private companion object {

        private val READMEFileNameRegex = Regex("""^README(\..+)?$""", RegexOption.IGNORE_CASE)
    }

    private suspend fun getForgejoRepoStats(
        owner: String,
        repo: String,
        sourceHost: String,
    ): RepoStats {

        val cacheKey = "details:stats:forgejo:v2:$sourceHost:$owner/$repo"
        cacheManager.get<RepoStats>(cacheKey)?.let { return it }
        val client = forgejoClientRegistry.clientFor(sourceHost)
        return try {
            val info = client.getRepository(owner, repo).getOrThrow()

            val license = detectForgejoLicense(client, owner, repo, info.defaultBranch ?: "main")
            val downloads = sumForgejoReleaseDownloads(sourceHost, owner, repo)
            val result = RepoStats(
                stars = info.starsCount,
                forks = info.forksCount,
                openIssues = info.openIssuesCount,
                license = license,
                totalDownloads = downloads,
            )
            cacheManager.put(cacheKey, result, REPO_STATS)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            cacheManager.getStale<RepoStats>(cacheKey)?.let { return it }
            throw e
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun detectForgejoLicense(
        client: zed.rainxch.core.data.network.ForgejoApiClient,
        owner: String,
        repo: String,
        ref: String,
    ): String? {

        val candidates = listOf("LICENSE", "LICENSE.md", "LICENSE.txt", "COPYING")
        val dto = candidates.firstNotNullOfOrNull { name ->
            client.getContentsFile(owner, repo, name, ref).getOrNull()
        } ?: return null
        val raw = dto.content ?: return null
        val text = try {
            Base64.Mime.decode(raw).decodeToString().take(400)
        } catch (e: IllegalArgumentException) {
            return null
        }
        return spdxFromLicenseHeader(text)
    }

    private fun spdxFromLicenseHeader(text: String): String? {
        val upper = text.uppercase()
        return when {
            upper.contains("GNU AFFERO GENERAL PUBLIC LICENSE") && upper.contains("VERSION 3") -> "AGPL-3.0"
            upper.contains("GNU LESSER GENERAL PUBLIC LICENSE") && upper.contains("VERSION 3") -> "LGPL-3.0"
            upper.contains("GNU LESSER GENERAL PUBLIC LICENSE") && upper.contains("VERSION 2.1") -> "LGPL-2.1"
            upper.contains("GNU GENERAL PUBLIC LICENSE") && upper.contains("VERSION 3") -> "GPL-3.0"
            upper.contains("GNU GENERAL PUBLIC LICENSE") && upper.contains("VERSION 2") -> "GPL-2.0"
            upper.contains("APACHE LICENSE") && upper.contains("VERSION 2.0") -> "Apache-2.0"
            upper.contains("MIT LICENSE") || (upper.startsWith("MIT ") && upper.contains("PERMISSION")) -> "MIT"
            upper.contains("BSD 3-CLAUSE") || upper.contains("BSD-3-CLAUSE") -> "BSD-3-Clause"
            upper.contains("BSD 2-CLAUSE") || upper.contains("BSD-2-CLAUSE") -> "BSD-2-Clause"
            upper.contains("MOZILLA PUBLIC LICENSE") && upper.contains("VERSION 2.0") -> "MPL-2.0"
            upper.contains("THE UNLICENSE") || upper.contains("UNLICENSE") -> "Unlicense"
            upper.contains("CREATIVE COMMONS") && upper.contains("CC0") -> "CC0-1.0"
            upper.contains("EUROPEAN UNION PUBLIC LICENCE") -> "EUPL-1.2"
            else -> null
        }
    }

    private suspend fun sumForgejoReleaseDownloads(
        sourceHost: String,
        owner: String,
        repo: String,
    ): Long {
        val cacheKey = "details:releases:forgejo:$sourceHost:$owner/$repo"
        val cached = cacheManager.get<List<GithubRelease>>(cacheKey).orEmpty()
        val releases = if (cached.isNotEmpty()) {
            cached
        } else {
            try {
                getForgejoAllReleases(owner, repo, sourceHost)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.debug("Forgejo downloads sum: releases fetch failed: ${e.message}")
                return 0L
            }
        }
        return releases.sumOf { release ->
            release.assets.sumOf { it.downloadCount }
        }
    }

    override suspend fun checkAttestations(
        owner: String,
        repo: String,
        sha256Digest: String,
    ): Boolean =
        try {
            val response =
                httpClient
                    .executeRequest<AttestationsResponse> {
                        get("/repos/$owner/$repo/attestations/sha256:$sha256Digest") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                        }
                    }.getOrNull()
            response != null && response.attestations.isNotEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.debug("Attestation check failed for $owner/$repo: ${e.message}")
            false
        }

    override suspend fun fetchRawMarkdown(url: String): String? {
        val fetchUrl = if (url.startsWith("https://github.com/") && url.contains("/blob/")) {
            url.replaceFirst("https://github.com/", "https://raw.githubusercontent.com/")
               .replaceFirst("/blob/", "/")
        } else {
            url
        }

        return try {
            val rawMarkdown = httpClient.executeRequest<String> {
                get(fetchUrl)
            }.getOrNull()

            if (rawMarkdown != null) {
                val match = Regex("""https://(?:raw\.githubusercontent\.com|github\.com)/([^/]+)/([^/]+)/(?:blob/)?([^/]+)/(.*)""").find(url)
                if (match != null) {
                    val owner = match.groupValues[1]
                    val repo = match.groupValues[2]
                    val branch = match.groupValues[3]
                    val path = match.groupValues[4]

                    val basePath = path.substringBeforeLast("/", "")
                    val pathPrefix = if (basePath.isNotEmpty()) "$basePath/" else ""

                    val baseUrl = "https://raw.githubusercontent.com/$owner/$repo/$branch/$pathPrefix"
                    val linkBaseUrl = "https://github.com/$owner/$repo/blob/$branch/$pathPrefix"

                    preprocessMarkdown(markdown = rawMarkdown, baseUrl = baseUrl, linkBaseUrl = linkBaseUrl)
                } else {
                    rawMarkdown
                }
            } else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to fetch raw markdown from $url: ${e.message}")
            null
        }
    }
}
