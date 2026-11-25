package zed.rainxch.details.data.repository

import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.README
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.RELEASES
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.REPO_DETAILS
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.REPO_STATS
import zed.rainxch.core.data.dto.ForgejoRepoNetworkModel
import zed.rainxch.core.data.dto.GithubReadmeResponseDto
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.network.ForgejoApiClient
import zed.rainxch.core.data.network.ForgejoClientRegistry
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.details.data.mappers.toForgejoSummary
import zed.rainxch.details.data.model.CachedReadme
import zed.rainxch.details.data.model.toCachedReadme
import zed.rainxch.details.data.utils.decodeBase64Text
import zed.rainxch.details.data.utils.detectReadmeLanguage
import zed.rainxch.details.data.utils.preprocessForgejoMarkdown
import zed.rainxch.details.data.utils.spdxFromLicenseHeader
import zed.rainxch.details.domain.model.RepoStats

// Codeberg and other Forgejo hosts are read straight from the host, without the backend.
internal class ForgejoDetailsSource(
    private val clientRegistry: ForgejoClientRegistry,
    private val cacheManager: CacheManager,
    private val logger: KomiStoreLogger,
) {
    suspend fun repository(
        owner: String,
        name: String,
        host: String,
    ): GithubRepoSummary {
        val cacheKey = "details:repo:forgejo:$host:$owner/$name"
        return cacheManager.get<GithubRepoSummary>(cacheKey)
            ?: cacheManager.freshOrStale(cacheKey, REPO_DETAILS) {
                clientRegistry
                    .clientFor(host)
                    .getRepository(owner, name)
                    .map { it.toForgejoSummary(host) }
            }
    }

    suspend fun releases(
        owner: String,
        repo: String,
        host: String,
    ): List<GithubRelease> {
        val cacheKey = "details:releases:forgejo:v2:$host:$owner/$repo"
        cacheManager.get<List<GithubRelease>>(cacheKey)?.takeIf { it.isNotEmpty() }?.let { return it }
        val result =
            clientRegistry
                .clientFor(host)
                .getReleases(owner, repo)
                .getOrNull()
                .orEmpty()
                .filter { it.draft != true }
                .map { network ->
                    val body = network.body?.let { preprocessForgejoMarkdown(it.replace("\r\n", "\n"), host, owner, repo, "HEAD") }
                    network.copy(body = body).toDomain()
                }.sortedByDescending { it.publishedAt }
        return if (result.isEmpty()) {
            result
        } else {
            cacheManager.putOrFailure(cacheKey, result, RELEASES).getOrElse { cause ->
                cacheManager.staleOrThrow<List<GithubRelease>>(cacheKey, cause)
            }
        }
    }

    suspend fun readme(
        owner: String,
        repo: String,
        defaultBranch: String,
        host: String,
    ): Triple<String, String?, String>? {
        val cacheKey = "details:readme:forgejo:v2:$host:$owner/$repo"
        return cacheManager.get<CachedReadme>(cacheKey)?.toTriple()
            ?: fetchReadme(ForgejoRef(host, owner, repo, defaultBranch), cacheKey)
    }

    suspend fun stats(
        owner: String,
        repo: String,
        host: String,
    ): RepoStats {
        val cacheKey = "details:stats:forgejo:v2:$host:$owner/$repo"
        return cacheManager.get<RepoStats>(cacheKey)
            ?: cacheManager.freshOrStale(cacheKey, REPO_STATS) {
                val client = clientRegistry.clientFor(host)
                client.getRepository(owner, repo).map { info ->
                    statsOf(info, client, ForgejoRef(host, owner, repo, info.defaultBranch ?: "main"))
                }
            }
    }

    private suspend fun fetchReadme(
        ref: ForgejoRef,
        cacheKey: String,
    ): Triple<String, String?, String>? {
        val dto =
            findReadme(clientRegistry.clientFor(ref.host), ref)
                ?: return cacheManager.getStale<CachedReadme>(cacheKey)?.toTriple()
        val encoded = dto.content ?: return null
        val decoded =
            decodeBase64Text(encoded).getOrElse { e ->
                logger.warn("Failed to decode Forgejo readme for ${ref.host}/${ref.owner}/${ref.repo}: ${e.message}")
                return null
            }
        val processed = preprocessForgejoMarkdown(decoded, ref.host, ref.owner, ref.repo, ref.branch)
        val path = dto.path?.takeIf { it.isNotBlank() } ?: "README.md"
        val readme = Triple(processed, detectReadmeLanguage(processed), path)
        cacheManager.put(cacheKey, readme.toCachedReadme(), README)
        return readme
    }

    // Forgejo has no /readme endpoint: README.md first, then the first README* in the root listing.
    private suspend fun findReadme(
        client: ForgejoApiClient,
        ref: ForgejoRef,
    ): GithubReadmeResponseDto? =
        client.getContentsFile(ref.owner, ref.repo, "README.md", ref.branch).getOrNull()
            ?: client
                .listContentsRoot(ref.owner, ref.repo, ref.branch)
                .getOrNull()
                ?.firstOrNull { entry -> entry.type == "file" && entry.name?.let { README_FILE_NAME.matches(it) } == true }
                ?.let { entry -> entry.path ?: entry.name }
                ?.let { path -> client.getContentsFile(ref.owner, ref.repo, path, ref.branch).getOrNull() }

    private suspend fun statsOf(
        info: ForgejoRepoNetworkModel,
        client: ForgejoApiClient,
        ref: ForgejoRef,
    ): RepoStats =
        RepoStats(
            stars = info.starsCount,
            forks = info.forksCount,
            openIssues = info.openIssuesCount,
            license = detectLicense(client, ref),
            totalDownloads = totalDownloads(ref),
        )

    private suspend fun detectLicense(
        client: ForgejoApiClient,
        ref: ForgejoRef,
    ): String? =
        LICENSE_FILES
            .firstNotNullOfOrNull { name -> client.getContentsFile(ref.owner, ref.repo, name, ref.branch).getOrNull() }
            ?.content
            ?.let { decodeBase64Text(it).getOrNull() }
            ?.take(LICENSE_HEADER_CHARS)
            ?.let(::spdxFromLicenseHeader)

    private suspend fun totalDownloads(ref: ForgejoRef): Long {
        val legacyCacheKey = "details:releases:forgejo:${ref.host}:${ref.owner}/${ref.repo}"
        val knownReleases =
            cacheManager
                .get<List<GithubRelease>>(legacyCacheKey)
                .orEmpty()
                .ifEmpty {
                    try {
                        releases(ref.owner, ref.repo, ref.host)
                    } catch (e: LocalStoreException) {
                        logger.debug("Forgejo downloads sum: releases fetch failed: ${e.message}")
                        return 0L
                    }
                }
        return knownReleases.sumOf { release -> release.assets.sumOf { it.downloadCount } }
    }

    private data class ForgejoRef(
        val host: String,
        val owner: String,
        val repo: String,
        val branch: String,
    )

    private companion object {
        val README_FILE_NAME = Regex("""^README(\..+)?$""", RegexOption.IGNORE_CASE)
        val LICENSE_FILES = listOf("LICENSE", "LICENSE.md", "LICENSE.txt", "COPYING")
        const val LICENSE_HEADER_CHARS = 400
    }
}
