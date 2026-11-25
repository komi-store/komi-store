package zed.rainxch.apps.data.repository

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import zed.rainxch.apps.domain.model.GithubRepoInfo
import zed.rainxch.apps.domain.repository.RepoLookup
import zed.rainxch.core.data.dto.BackendRepoResponse
import zed.rainxch.core.data.dto.GithubRepoNetworkModel
import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.ForgejoClientRegistry
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.data.network.getReleases
import zed.rainxch.core.data.network.getRepo
import zed.rainxch.core.data.network.shouldFallbackToGithubOrRethrow
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.isEffectivelyPreRelease
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.utils.RepoIdCodec
import kotlin.time.TimeMark
import kotlin.time.TimeSource

// Backend first, GitHub on fallback; a non-null sourceHost asks that Forgejo host instead.
internal class RepoLookupImpl(
    private val clientProvider: GitHubClientProvider,
    private val backendApiClient: BackendApiClient,
    private val forgejoClientRegistry: ForgejoClientRegistry,
    private val tweaksRepository: TweaksRepository,
    private val logger: KomiStoreLogger,
) : RepoLookup {
    override suspend fun getLatestRelease(
        owner: String,
        repo: String,
        includePreReleases: Boolean,
        sourceHost: String?,
    ): GithubRelease? =
        if (sourceHost != null) {
            forgejoLatestRelease(sourceHost, owner, repo, includePreReleases)
        } else {
            backendLatestRelease(owner, repo, includePreReleases)
        }

    override suspend fun fetchRepoInfo(
        owner: String,
        repo: String,
        sourceHost: String?,
    ): GithubRepoInfo? =
        if (sourceHost != null) {
            forgejoRepoInfo(sourceHost, owner, repo)
        } else {
            backendRepoInfo(owner, repo)
        }

    private suspend fun backendLatestRelease(
        owner: String,
        repo: String,
        includePreReleases: Boolean,
    ): GithubRelease? {
        val start = TimeSource.Monotonic.markNow()
        return backendApiClient.getReleases(owner, repo, perPage = LATEST_RELEASE_WINDOW).fold(
            onSuccess = { releases ->
                logger.debug("Backend releases for $owner/$repo took ${start.elapsedNow().inWholeMilliseconds} ms")
                releases.newest(includePreReleases)?.toDomain()
            },
            onFailure = { error ->
                if (shouldFallbackToGithubOrRethrow(error)) {
                    logger.warn(
                        "Backend releases for $owner/$repo failed after " +
                            "${start.elapsedNow().inWholeMilliseconds} ms (${error.message}), asking GitHub directly",
                    )
                    githubLatestRelease(owner, repo, includePreReleases)
                } else {
                    null
                }
            },
        )
    }

    private suspend fun githubLatestRelease(
        owner: String,
        repo: String,
        includePreReleases: Boolean,
    ): GithubRelease? {
        val start = TimeSource.Monotonic.markNow()
        val response = withTimeoutOrNull(DIRECT_RELEASES_TIMEOUT_MS) { githubReleases(owner, repo, LATEST_RELEASE_WINDOW) }
        return when (response) {
            null -> {
                logGithubFailure(owner, repo, start, "GitHub did not answer within ${DIRECT_RELEASES_TIMEOUT_MS / MILLIS_PER_SECOND} s")
                null
            }

            else -> {
                response.fold(
                    onSuccess = { releases ->
                        logger.debug("GitHub releases for $owner/$repo took ${start.elapsedNow().inWholeMilliseconds} ms")
                        releases.newest(includePreReleases)?.toDomain()
                    },
                    onFailure = { error ->
                        logGithubFailure(owner, repo, start, error.message)
                        null
                    },
                )
            }
        }
    }

    private fun logGithubFailure(
        owner: String,
        repo: String,
        start: TimeMark,
        reason: String?,
    ) {
        logger.error(
            "Failed to fetch latest release for $owner/$repo from GitHub after " +
                "${start.elapsedNow().inWholeMilliseconds} ms: $reason",
        )
    }

    private suspend fun backendRepoInfo(
        owner: String,
        repo: String,
    ): GithubRepoInfo? =
        backendApiClient.getRepo(owner, repo).fold(
            onSuccess = { backendRepo -> backendRepo.toRepoInfo(latestTagFor(owner, repo, backendRepo)) },
            onFailure = { error -> if (shouldFallbackToGithubOrRethrow(error)) githubRepoInfo(owner, repo) else null },
        )

    private suspend fun latestTagFor(
        owner: String,
        repo: String,
        backendRepo: BackendRepoResponse,
    ): String? {
        val includePreReleases = tweaksRepository.getIncludePreReleases().first()
        return if (includePreReleases || backendRepo.latestReleaseTag == null) {
            resolveLatestTag(owner, repo, includePreReleases) ?: backendRepo.latestReleaseTag
        } else {
            backendRepo.latestReleaseTag
        }
    }

    private suspend fun githubRepoInfo(
        owner: String,
        repo: String,
    ): GithubRepoInfo? =
        clientProvider.client
            .executeRequest<GithubRepoNetworkModel> {
                get("/repos/$owner/$repo") {
                    header(HttpHeaders.Accept, GITHUB_JSON)
                }
            }.fold(
                onSuccess = { repoModel ->
                    val includePreReleases = tweaksRepository.getIncludePreReleases().first()
                    repoModel.toRepoInfo(resolveLatestTag(owner, repo, includePreReleases))
                },
                onFailure = { error ->
                    logger.error("Failed to fetch repo info for $owner/$repo: ${error.message}")
                    null
                },
            )

    private suspend fun resolveLatestTag(
        owner: String,
        repo: String,
        includePreReleases: Boolean,
    ): String? =
        backendApiClient.getReleases(owner, repo, perPage = TAG_RELEASE_WINDOW).fold(
            onSuccess = { releases -> releases.newest(includePreReleases)?.tagName },
            onFailure = { error -> if (shouldFallbackToGithubOrRethrow(error)) githubLatestTag(owner, repo, includePreReleases) else null },
        )

    // The tag is a nice-to-have on top of the repo info, so a GitHub rate limit here only leaves it out.
    private suspend fun githubLatestTag(
        owner: String,
        repo: String,
        includePreReleases: Boolean,
    ): String? =
        try {
            githubReleases(owner, repo, TAG_RELEASE_WINDOW).getOrNull()?.newest(includePreReleases)?.tagName
        } catch (_: RateLimitException) {
            null
        }

    private suspend fun githubReleases(
        owner: String,
        repo: String,
        perPage: Int,
    ): Result<List<ReleaseNetwork>> =
        clientProvider.client.executeRequest {
            get("/repos/$owner/$repo/releases") {
                header(HttpHeaders.Accept, GITHUB_JSON)
                parameter("per_page", perPage)
            }
        }

    private suspend fun forgejoLatestRelease(
        host: String,
        owner: String,
        repo: String,
        includePreReleases: Boolean,
    ): GithubRelease? =
        forgejoClientRegistry
            .clientFor(host)
            .getReleases(owner, repo, perPage = LATEST_RELEASE_WINDOW)
            .getOrNull()
            ?.asSequence()
            ?.filter { it.draft != true }
            ?.map { it.toDomain() }
            ?.filter { includePreReleases || !it.isEffectivelyPreRelease() }
            ?.maxByOrNull { it.publishedAt }

    private suspend fun forgejoRepoInfo(
        host: String,
        owner: String,
        repo: String,
    ): GithubRepoInfo? {
        val client = forgejoClientRegistry.clientFor(host)
        val repoModel = client.getRepository(owner, repo).getOrNull() ?: return null
        return GithubRepoInfo(
            id = RepoIdCodec.encode(host, repoModel.id),
            name = repoModel.name,
            owner = repoModel.owner.login,
            ownerAvatarUrl = repoModel.owner.avatarUrl,
            description = repoModel.description,
            language = repoModel.language,
            htmlUrl = repoModel.htmlUrl,
            latestReleaseTag = client.getLatestRelease(owner, repo).getOrNull()?.tagName,
        )
    }

    private fun List<ReleaseNetwork>.newest(includePreReleases: Boolean): ReleaseNetwork? =
        asSequence()
            .filter { it.draft != true }
            .filter { includePreReleases || it.prerelease != true }
            .maxByOrNull { it.publishedAt ?: it.createdAt ?: "" }

    private fun BackendRepoResponse.toRepoInfo(latestTag: String?): GithubRepoInfo =
        GithubRepoInfo(
            id = id,
            name = name,
            owner = owner.login,
            ownerAvatarUrl = owner.avatarUrl.orEmpty(),
            description = description,
            language = language,
            htmlUrl = htmlUrl,
            latestReleaseTag = latestTag,
        )

    private fun GithubRepoNetworkModel.toRepoInfo(latestTag: String?): GithubRepoInfo =
        GithubRepoInfo(
            id = id,
            name = name,
            owner = owner.login,
            ownerAvatarUrl = owner.avatarUrl,
            description = description,
            language = language,
            htmlUrl = htmlUrl,
            latestReleaseTag = latestTag,
        )

    private companion object {
        const val GITHUB_JSON = "application/vnd.github+json"
        const val LATEST_RELEASE_WINDOW = 10
        const val TAG_RELEASE_WINDOW = 5
        const val DIRECT_RELEASES_TIMEOUT_MS = 20_000L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
