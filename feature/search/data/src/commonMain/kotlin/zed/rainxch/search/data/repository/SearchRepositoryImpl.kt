package zed.rainxch.search.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import zed.rainxch.core.data.cache.CacheManager
import zed.rainxch.core.data.cache.CacheManager.CacheTtl.SEARCH_RESULTS
import zed.rainxch.core.data.dto.GithubRepoNetworkModel
import zed.rainxch.core.data.dto.GithubRepoSearchResponse
import zed.rainxch.core.data.mappers.toSummary
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.data.network.shouldFallbackToGithubOrRethrow
import zed.rainxch.core.domain.model.repository.DiscoveryPlatform
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.repository.PaginatedDiscoveryRepositories
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.account.github.GithubUser
import zed.rainxch.domain.model.ExploreResult
import zed.rainxch.domain.model.ProgrammingLanguage
import zed.rainxch.domain.model.SortBy
import zed.rainxch.domain.model.SortOrder
import zed.rainxch.domain.repository.SearchRepository
import zed.rainxch.search.data.dto.GithubReleaseNetworkModel
import zed.rainxch.search.data.utils.LruCache

class SearchRepositoryImpl(
    private val clientProvider: GitHubClientProvider,
    private val backendApiClient: BackendApiClient,
    private val cacheManager: CacheManager,
    private val forgejoClientRegistry: zed.rainxch.core.data.network.ForgejoClientRegistry,
    private val tokenStore: zed.rainxch.core.data.data_source.TokenStore,
) : SearchRepository {
    private val httpClient: HttpClient get() = clientProvider.client
    private val releaseCheckCache = LruCache<String, GithubRepoSummary>(maxSize = 500)
    private val cacheMutex = Mutex()

    companion object {
        private const val PER_PAGE = 100
        private const val VERIFY_CONCURRENCY = 15
        private const val PER_CHECK_TIMEOUT_MS = 2000L
        private const val MAX_AUTO_SKIP_PAGES = 3
        private const val BACKEND_PAGE_SIZE = 20
    }

    private fun searchCacheKey(
        query: String,
        platform: DiscoveryPlatform,
        language: ProgrammingLanguage,
        sortBy: SortBy,
        sortOrder: SortOrder,
        page: Int,
    ): String {
        val queryHash =
            query
                .trim()
                .lowercase()
                .hashCode()
                .toUInt()
                .toString(16)
        return "search:$queryHash:${platform.name}:${language.name}:${sortBy.name}:${sortOrder.name}:page$page"
    }

    override fun searchRepositories(
        query: String,
        platform: DiscoveryPlatform,
        language: ProgrammingLanguage,
        sortBy: SortBy,
        sortOrder: SortOrder,
        page: Int,
        source: zed.rainxch.domain.model.SearchSource,
    ): Flow<PaginatedDiscoveryRepositories> =
        channelFlow {
            if (source is zed.rainxch.domain.model.SearchSource.Forgejo) {
                forgejoSearch(source.host, query, page)
                return@channelFlow
            }

            val cacheKey = searchCacheKey(query, platform, language, sortBy, sortOrder, page)

            val privateMatches =
                if (page == 1 && query.isNotBlank()) {
                    searchPrivateRepos(query, platform, language)
                } else {
                    emptyList()
                }

            val cached = cacheManager.get<PaginatedDiscoveryRepositories>(cacheKey)
            if (cached != null) {
                send(cached.prepend(privateMatches))
                return@channelFlow
            }

            val backendResult = tryBackendSearch(query, platform, sortBy, page)
            if (backendResult != null) {
                cacheManager.put(cacheKey, backendResult, SEARCH_RESULTS)
                send(backendResult.prepend(privateMatches))
                return@channelFlow
            }

            fallbackGithubSearch(query, platform, language, sortBy, sortOrder, page, cacheKey, privateMatches)
        }.flowOn(Dispatchers.IO)

    private suspend fun isSignedIn(): Boolean =
        try {
            tokenStore.currentToken()?.accessToken?.isNotBlank() == true
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }

    private suspend fun searchPrivateRepos(
        query: String,
        platform: DiscoveryPlatform,
        language: ProgrammingLanguage,
    ): List<GithubRepoSummary> {
        if (!isSignedIn()) return emptyList()

        val safeQuery = query.trim().replace("\"", "")
        val q =
            buildString {
                append("\"$safeQuery\" in:name,description fork:true is:private")
                if (language != ProgrammingLanguage.All && language.queryValue != null) {
                    append(" language:${language.queryValue}")
                }
            }

        return try {
            val response =
                httpClient
                    .executeRequest<GithubRepoSearchResponse> {
                        get("/search/repositories") {
                            parameter("q", q)
                            parameter("per_page", 20)
                        }
                    }.getOrNull() ?: return emptyList()

            val privateItems = response.items.filter { it.private }
            if (platform == DiscoveryPlatform.All) {
                privateItems.map { it.toSummary() }
            } else {
                verifyBatch(privateItems, platform)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun PaginatedDiscoveryRepositories.prepend(
        extra: List<GithubRepoSummary>,
    ): PaginatedDiscoveryRepositories {
        if (extra.isEmpty()) return this
        val existingIds = repos.mapTo(mutableSetOf()) { it.id }
        val deduped = extra.filter { it.id !in existingIds }
        if (deduped.isEmpty()) return this
        return copy(
            repos = deduped + repos,
            totalCount = totalCount?.plus(deduped.size),
        )
    }

    private suspend fun kotlinx.coroutines.channels.ProducerScope<PaginatedDiscoveryRepositories>.forgejoSearch(
        host: String,
        query: String,
        page: Int,
    ) {
        val client = forgejoClientRegistry.clientFor(host)
        val result = client.searchRepositories(query = query, page = page, limit = PER_PAGE)
        val repos = result.getOrNull()?.data.orEmpty()
        val summaries = repos.map { repo ->
            GithubRepoSummary(
                id = zed.rainxch.core.domain.utils.RepoIdCodec.encode(host, repo.id),
                name = repo.name,
                fullName = repo.fullName ?: "${repo.owner.login}/${repo.name}",
                owner = GithubUser(
                    id = repo.owner.id,
                    login = repo.owner.login,
                    avatarUrl = repo.owner.avatarUrl,
                    htmlUrl = repo.owner.htmlUrl,
                ),
                description = repo.description,
                defaultBranch = repo.defaultBranch ?: "main",
                htmlUrl = repo.htmlUrl,
                stargazersCount = repo.starsCount,
                forksCount = repo.forksCount,
                language = repo.language,
                topics = null,
                releasesUrl = "${repo.htmlUrl}/releases",
                updatedAt = repo.updatedAt ?: "",
                isFork = false,
                sourceHost = host,
            )
        }
        send(
            PaginatedDiscoveryRepositories(
                repos = summaries,
                hasMore = repos.size >= PER_PAGE,
                nextPageIndex = page + 1,
                totalCount = null,
                passthroughAttempted = false,
            ),
        )
    }

    private suspend fun tryBackendSearch(
        query: String,
        platform: DiscoveryPlatform,
        sortBy: SortBy,
        page: Int,
    ): PaginatedDiscoveryRepositories? {
        if (query.isBlank()) return null

        if (sortBy == SortBy.MostForks) return null

        val platformSlug = when (platform) {
            DiscoveryPlatform.Android -> "android"
            DiscoveryPlatform.Windows -> "windows"
            DiscoveryPlatform.Macos -> "macos"
            DiscoveryPlatform.Linux -> "linux"
            DiscoveryPlatform.Ios -> return null 
            DiscoveryPlatform.All -> null
        }

        val sort = when (sortBy) {
            SortBy.MostStars -> "stars"
            SortBy.BestMatch -> "relevance"
            SortBy.RecentlyUpdated -> "updated"
            SortBy.RecentlyReleased -> "releases"
            SortBy.MostForks -> null
        }

        val signedIn = isSignedIn()
        val offset = (page - 1) * BACKEND_PAGE_SIZE
        val result = backendApiClient.search(
            query = query,
            platform = platformSlug,
            sort = sort,
            limit = BACKEND_PAGE_SIZE,
            offset = offset,
        )

        return result.fold(
            onSuccess = { searchResponse ->
                val repos = searchResponse.items.map { it.toSummary(platform) }
                val hasMore = offset + repos.size < searchResponse.totalHits
                PaginatedDiscoveryRepositories(
                    repos = repos,
                    hasMore = hasMore,
                    nextPageIndex = page + 1,
                    totalCount = searchResponse.totalHits,
                    passthroughAttempted = searchResponse.passthroughAttempted,
                )
            },
            onFailure = { e ->

                if (!shouldFallbackToGithubOrRethrow(e, signedIn)) {
                    throw e
                }
                null
            },
        )
    }

    private suspend fun kotlinx.coroutines.channels.ProducerScope<PaginatedDiscoveryRepositories>.fallbackGithubSearch(
        query: String,
        platform: DiscoveryPlatform,
        language: ProgrammingLanguage,
        sortBy: SortBy,
        sortOrder: SortOrder,
        page: Int,
        cacheKey: String,
        privateMatches: List<GithubRepoSummary>,
    ) {
        val searchQuery = buildSearchQuery(query, language)
        val sort = sortBy.toGithubSortParam()
        val order = sortOrder.toGithubParam()

        try {
            var currentPage = page
            var pagesSkipped = 0

            while (pagesSkipped <= MAX_AUTO_SKIP_PAGES) {
                currentCoroutineContext().ensureActive()

                val response =
                    httpClient
                        .executeRequest<GithubRepoSearchResponse> {
                            get("/search/repositories") {
                                parameter("q", searchQuery)
                                parameter("per_page", PER_PAGE)
                                parameter("page", currentPage)
                                if (sort != null) {
                                    parameter("sort", sort)
                                    parameter("order", order)
                                }
                            }
                        }.getOrThrow()

                val total = response.totalCount
                val baseHasMore =
                    (currentPage * PER_PAGE) < total && response.items.isNotEmpty()

                if (response.items.isEmpty()) {
                    send(
                        PaginatedDiscoveryRepositories(
                            repos = emptyList(),
                            hasMore = false,
                            nextPageIndex = currentPage + 1,
                            totalCount = total,
                        ).prepend(privateMatches),
                    )
                    return
                }

                val verified = verifyBatch(response.items, platform)

                if (verified.isNotEmpty()) {
                    val result =
                        PaginatedDiscoveryRepositories(
                            repos = verified,
                            hasMore = baseHasMore,
                            nextPageIndex = currentPage + 1,
                            totalCount = total,
                        )
                    cacheManager.put(cacheKey, result, SEARCH_RESULTS)
                    send(result.prepend(privateMatches))
                    return
                }

                if (!baseHasMore) {
                    send(
                        PaginatedDiscoveryRepositories(
                            repos = emptyList(),
                            hasMore = false,
                            nextPageIndex = currentPage + 1,
                            totalCount = total,
                        ).prepend(privateMatches),
                    )
                    return
                }

                currentPage++
                pagesSkipped++
            }

            send(
                PaginatedDiscoveryRepositories(
                    repos = emptyList(),
                    hasMore = true,
                    nextPageIndex = currentPage + 1,
                    totalCount = null,
                ).prepend(privateMatches),
            )
        } catch (e: RateLimitException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            send(
                PaginatedDiscoveryRepositories(
                    repos = emptyList(),
                    hasMore = false,
                    nextPageIndex = page,
                ).prepend(privateMatches),
            )
        }
    }

    private suspend fun verifyBatch(
        items: List<GithubRepoNetworkModel>,
        searchPlatform: DiscoveryPlatform,
    ): List<GithubRepoSummary> {
        val semaphore = Semaphore(VERIFY_CONCURRENCY)

        val deferredChecks =
            coroutineScope {
                items.map { repo ->
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
                }
            }

        return buildList {
            for (i in items.indices) {
                currentCoroutineContext().ensureActive()
                val result =
                    try {
                        deferredChecks[i].await()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                if (result != null) add(result)
            }
        }
    }

    private fun buildSearchQuery(
        userQuery: String,
        language: ProgrammingLanguage,
    ): String {
        val clean = userQuery.trim()
        val q =
            if (clean.isBlank()) {
                "stars:>100"
            } else {
                "\"$clean\""
            }
        val scope = " in:name,description"
        val common = " archived:false fork:true"

        val languageFilter =
            if (language != ProgrammingLanguage.All && language.queryValue != null) {
                " language:${language.queryValue}"
            } else {
                ""
            }

        return ("$q$scope$common" + languageFilter).trim()
    }

    private fun assetMatchesPlatform(
        nameRaw: String,
        platform: DiscoveryPlatform,
    ): Boolean {
        val name = nameRaw.lowercase()
        return when (platform) {
            DiscoveryPlatform.All -> {
                name.endsWith(".apk") ||
                    name.endsWith(".msi") || name.endsWith(".exe") ||
                    name.endsWith(".dmg") || name.endsWith(".pkg") ||
                    name.endsWith(".appimage") || name.endsWith(".deb") ||
                    name.endsWith(".rpm") || name.endsWith(".pkg.tar.zst") ||
                    name.endsWith(".ipa")
            }

            DiscoveryPlatform.Android -> {
                name.endsWith(".apk")
            }

            DiscoveryPlatform.Windows -> {
                name.endsWith(".exe") || name.endsWith(".msi")
            }

            DiscoveryPlatform.Macos -> {
                name.endsWith(".dmg") || name.endsWith(".pkg")
            }

            DiscoveryPlatform.Linux -> {
                name.endsWith(".appimage") || name.endsWith(".deb") ||
                    name.endsWith(".rpm") || name.endsWith(".pkg.tar.zst")
            }

            DiscoveryPlatform.Ios -> {
                name.endsWith(".ipa")
            }
        }
    }

    private fun detectAvailablePlatforms(assetNames: List<String>): List<DiscoveryPlatform> =
        buildList {
            DiscoveryPlatform.entries
                .filter { it != DiscoveryPlatform.All }
                .forEach { platform ->
                    if (assetNames.any { assetMatchesPlatform(it, platform) }) {
                        add(platform)
                    }
                }
        }

    private suspend fun checkRepoHasInstallers(
        repo: GithubRepoNetworkModel,
        targetPlatform: DiscoveryPlatform,
    ): GithubRepoSummary? {
        return try {
            val allReleases =
                httpClient
                    .executeRequest<List<GithubReleaseNetworkModel>> {
                        get("/repos/${repo.owner.login}/${repo.name}/releases") {
                            header("Accept", "application/vnd.github.v3+json")
                            parameter("per_page", 5)
                        }
                    }.getOrNull() ?: return null

            val stableRelease =
                allReleases.firstOrNull {
                    it.draft != true && it.prerelease != true
                }

            if (stableRelease == null || stableRelease.assets.isEmpty()) {
                return null
            }

            val hasRelevantAssets =
                stableRelease.assets.any { asset ->
                    assetMatchesPlatform(asset.name, targetPlatform)
                }

            if (hasRelevantAssets) {
                val assetNames = stableRelease.assets.map { it.name }
                val platforms = detectAvailablePlatforms(assetNames)
                val summary = repo.toSummary()
                summary.copy(
                    updatedAt = stableRelease.publishedAt ?: summary.updatedAt,
                    availablePlatforms = platforms,
                )
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
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

    override suspend fun exploreFromGithub(
        query: String,
        platform: DiscoveryPlatform,
        page: Int,
    ): ExploreResult {
        val platformSlug = when (platform) {
            DiscoveryPlatform.Android -> "android"
            DiscoveryPlatform.Windows -> "windows"
            DiscoveryPlatform.Macos -> "macos"
            DiscoveryPlatform.Linux -> "linux"
            DiscoveryPlatform.Ios -> "ios"
            DiscoveryPlatform.All -> null
        }

        val response = backendApiClient.searchExplore(
            query = query,
            platform = platformSlug,
            page = page,
        ).getOrThrow()

        return ExploreResult(
            repos = response.items.map { it.toSummary() },
            page = response.page,
            hasMore = response.hasMore,
        )
    }
}
