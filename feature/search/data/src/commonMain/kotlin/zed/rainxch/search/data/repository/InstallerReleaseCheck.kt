package zed.rainxch.search.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import zed.rainxch.core.data.dto.GithubRepoNetworkModel
import zed.rainxch.core.data.mappers.toSummary
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.search.data.dto.GithubReleaseNetworkModel
import zed.rainxch.search.data.utils.LruCache
import zed.rainxch.search.data.utils.assetMatchesPlatform
import zed.rainxch.search.data.utils.detectAvailablePlatforms

internal class InstallerReleaseCheck(
    private val clientProvider: GitHubClientProvider,
) {
    private val httpClient: HttpClient get() = clientProvider.client
    private val releaseCheckCache = LruCache<String, GithubRepoSummary>(maxSize = CACHE_SIZE)
    private val cacheMutex = Mutex()

    suspend fun verifyBatch(
        items: List<GithubRepoNetworkModel>,
        searchPlatform: DiscoveryPlatform,
    ): List<GithubRepoSummary> {
        val semaphore = Semaphore(VERIFY_CONCURRENCY)
        return coroutineScope {
            items
                .map { repo ->
                    async {
                        try {
                            semaphore.withPermit {
                                withTimeoutOrNull(PER_CHECK_TIMEOUT_MS) {
                                    checkRepoHasInstallersCached(repo, searchPlatform)
                                }
                            }
                        } catch (_: CancellationException) {
                            null
                        }
                    }
                }.awaitAll()
                .filterNotNull()
        }
    }

    private suspend fun checkRepoHasInstallersCached(
        repo: GithubRepoNetworkModel,
        targetPlatform: DiscoveryPlatform,
    ): GithubRepoSummary? {
        val key = "${repo.owner.login}/${repo.name}:LATEST_PLATFORM_${targetPlatform.name}"
        val cached =
            cacheMutex.withLock {
                if (releaseCheckCache.contains(key)) releaseCheckCache.get(key) else null
            }
        if (cached != null ||
            cacheMutex.withLock {
                releaseCheckCache.contains(key) && releaseCheckCache.get(key) == null
            }
        ) {
            return cached
        }

        val result = checkRepoHasInstallers(repo, targetPlatform)
        cacheMutex.withLock {
            releaseCheckCache.put(key, result)
        }
        return result
    }

    private suspend fun checkRepoHasInstallers(
        repo: GithubRepoNetworkModel,
        targetPlatform: DiscoveryPlatform,
    ): GithubRepoSummary? {
        val stableRelease = latestStableRelease(repo) ?: return null
        if (stableRelease.assets.none { assetMatchesPlatform(it.name, targetPlatform) }) return null

        val summary = repo.toSummary()
        return summary.copy(
            updatedAt = stableRelease.publishedAt ?: summary.updatedAt,
            availablePlatforms = detectAvailablePlatforms(stableRelease.assets.map { it.name }),
        )
    }

    // A rate-limited lookup counts as no installers, like any other failed one.
    private suspend fun latestStableRelease(repo: GithubRepoNetworkModel): GithubReleaseNetworkModel? {
        val releases =
            try {
                httpClient
                    .executeRequest<List<GithubReleaseNetworkModel>> {
                        get("/repos/${repo.owner.login}/${repo.name}/releases") {
                            header("Accept", "application/vnd.github.v3+json")
                            parameter("per_page", RELEASES_PER_CHECK)
                        }
                    }.getOrNull()
            } catch (_: RateLimitException) {
                null
            }
        return releases?.firstOrNull { it.draft != true && it.prerelease != true }
    }

    private companion object {
        const val CACHE_SIZE = 500
        const val VERIFY_CONCURRENCY = 15
        const val PER_CHECK_TIMEOUT_MS = 2000L
        const val RELEASES_PER_CHECK = 5
    }
}
