package zed.rainxch.details.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.README
import zed.rainxch.core.data.dto.GithubReadmeResponseDto
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.data.network.getReadme
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.details.data.dto.AttestationsResponse
import zed.rainxch.details.data.model.CachedReadme
import zed.rainxch.details.data.model.toCachedReadme
import zed.rainxch.details.data.utils.decodeBase64Text
import zed.rainxch.details.data.utils.detectReadmeLanguage
import zed.rainxch.details.data.utils.githubRawBase
import zed.rainxch.details.data.utils.preprocessGithubMarkdown
import zed.rainxch.details.data.utils.preprocessMarkdown

internal interface RepoContentReads {
    suspend fun getReadme(
        owner: String,
        repo: String,
        defaultBranch: String,
        sourceHost: String?,
    ): Triple<String, String?, String>?

    suspend fun checkAttestations(
        owner: String,
        repo: String,
        sha256Digest: String,
    ): Boolean

    suspend fun fetchRawMarkdown(url: String): String?
}

internal class RepoContentReader(
    private val clientProvider: GitHubClientProvider,
    private val backendApiClient: BackendApiClient,
    private val cacheManager: CacheManager,
    private val backendFallback: BackendFallback,
    private val forgejo: ForgejoDetailsSource,
    private val logger: KomiStoreLogger,
) : RepoContentReads {
    private val httpClient: HttpClient get() = clientProvider.client

    override suspend fun getReadme(
        owner: String,
        repo: String,
        defaultBranch: String,
        sourceHost: String?,
    ): Triple<String, String?, String>? {
        if (sourceHost != null) return forgejo.readme(owner, repo, defaultBranch, sourceHost)
        val cacheKey = DetailsCacheKeys.readme(owner, repo)
        cacheManager.get<CachedReadme>(cacheKey)?.let { cached ->
            logger.debug("Cache hit for readme $owner/$repo")
            return cached.toTriple()
        }
        return when (val answer = readmeFromBackend(owner, repo, defaultBranch, cacheKey)) {
            is BackendReadme.Answered -> {
                answer.readme
            }

            BackendReadme.FailedOver -> {
                readmeFromRawUrl(owner, repo, defaultBranch)?.also { cacheManager.put(cacheKey, it.toCachedReadme(), README) }
            }
        }
    }

    override suspend fun checkAttestations(
        owner: String,
        repo: String,
        sha256Digest: String,
    ): Boolean =
        try {
            httpClient
                .executeRequest<AttestationsResponse> {
                    get("/repos/$owner/$repo/attestations/sha256:$sha256Digest") {
                        header(HttpHeaders.Accept, "application/vnd.github+json")
                    }
                }.getOrNull()
                ?.attestations
                ?.isNotEmpty() == true
        } catch (e: RateLimitException) {
            logger.debug("Attestation check failed for $owner/$repo: ${e.message}")
            false
        }

    override suspend fun fetchRawMarkdown(url: String): String? {
        val rawMarkdown =
            try {
                httpClient.executeRequest<String> { get(rawFetchUrl(url)) }.getOrNull()
            } catch (e: RateLimitException) {
                logger.error("Failed to fetch raw markdown from $url", e)
                null
            }
        return rawMarkdown?.let { preprocessLinkedMarkdown(it, url) }
    }

    private suspend fun readmeFromBackend(
        owner: String,
        repo: String,
        defaultBranch: String,
        cacheKey: String,
    ): BackendReadme =
        backendApiClient.getReadme(owner, repo).fold(
            onSuccess = { dto ->
                val readme = decodeBackendReadme(dto, owner, repo, defaultBranch)
                if (readme != null) {
                    cacheManager.put(cacheKey, readme.toCachedReadme(), README)
                    BackendReadme.Answered(readme)
                } else {
                    logger.debug("Backend readme decode failed for $owner/$repo, falling back to raw URL")
                    BackendReadme.FailedOver
                }
            },
            onFailure = { e ->
                if (backendFallback.failsOverToGithub(e)) {
                    logger.debug("Backend infra error for readme $owner/$repo (${e.message}), falling back to raw URL")
                    BackendReadme.FailedOver
                } else {
                    val stale = cacheManager.getStale<CachedReadme>(cacheKey)
                    if (stale != null) logger.debug("Backend 4xx for readme $owner/$repo, serving stale cache")
                    BackendReadme.Answered(stale?.toTriple())
                }
            },
        )

    private fun decodeBackendReadme(
        dto: GithubReadmeResponseDto,
        owner: String,
        repo: String,
        defaultBranch: String,
    ): Triple<String, String?, String>? {
        val encoded = dto.content ?: return null
        val decoded =
            decodeBase64Text(encoded).getOrElse { e ->
                logger.warn("Failed to base64-decode backend readme for $owner/$repo: ${e.message}")
                return null
            }
        val processed = preprocessGithubMarkdown(decoded, owner, repo, defaultBranch)
        val detectedLang = detectReadmeLanguage(processed)
        logger.debug("Fetched README via backend (detected language: ${detectedLang ?: "unknown"})")
        return Triple(processed, detectedLang, dto.path?.takeIf { it.isNotBlank() } ?: README_PATH)
    }

    private suspend fun readmeFromRawUrl(
        owner: String,
        repo: String,
        defaultBranch: String,
    ): Triple<String, String?, String>? =
        catchingRateLimit {
            httpClient.executeRequest<String> { get("${githubRawBase(owner, repo, defaultBranch)}$README_PATH") }
        }.fold(
            onSuccess = { rawMarkdown ->
                val processed = preprocessGithubMarkdown(rawMarkdown, owner, repo, defaultBranch)
                val detectedLang = detectReadmeLanguage(processed)
                logger.debug("Fetched README.md (detected language: ${detectedLang ?: "unknown"})")
                Triple(processed, detectedLang, README_PATH)
            },
            onFailure = { e ->
                logger.error("Failed to fetch README.md for $owner/$repo", e)
                null
            },
        )

    private fun rawFetchUrl(url: String): String =
        if (url.startsWith("https://github.com/") && url.contains("/blob/")) {
            url
                .replaceFirst("https://github.com/", "https://raw.githubusercontent.com/")
                .replaceFirst("/blob/", "/")
        } else {
            url
        }

    private fun preprocessLinkedMarkdown(
        rawMarkdown: String,
        url: String,
    ): String {
        val match = GITHUB_FILE_URL.find(url) ?: return rawMarkdown
        val (ownerAndRepo, branch, path) = match.destructured
        val basePath = path.substringBeforeLast("/", "")
        val pathPrefix = if (basePath.isNotEmpty()) "$basePath/" else ""
        return preprocessMarkdown(
            markdown = rawMarkdown,
            baseUrl = "https://raw.githubusercontent.com/$ownerAndRepo/$branch/$pathPrefix",
            linkBaseUrl = "https://github.com/$ownerAndRepo/blob/$branch/$pathPrefix",
        )
    }

    private sealed interface BackendReadme {
        data class Answered(
            val readme: Triple<String, String?, String>?,
        ) : BackendReadme

        data object FailedOver : BackendReadme
    }

    private companion object {
        const val README_PATH = "README.md"
        val GITHUB_FILE_URL = Regex("""https://(?:raw\.githubusercontent\.com|github\.com)/([^/]+/[^/]+)/(?:blob/)?([^/]+)/(.*)""")
    }
}
