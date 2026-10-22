@file:OptIn(ExperimentalTime::class)

package zed.rainxch.core.data.repository

import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import zed.rainxch.core.data.dto.GitHubStarredResponse
import zed.rainxch.core.data.dto.GitHubStarredResponseWithTimestamp
import zed.rainxch.core.data.dto.parseStarredPage
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.dao.StarredRepoDao
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toEntity
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.gitHubJson
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.StarredRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class StarredRepositoryImpl(
    private val starredRepoDao: StarredRepoDao,
    private val installedAppsDao: InstalledAppDao,
    private val platform: Platform,
    private val clientProvider: GitHubClientProvider,
    private val backendApiClient: zed.rainxch.core.data.network.BackendApiClient,
) : StarredRepository {
    private val httpClient: HttpClient get() = clientProvider.client

    companion object {
        private const val SYNC_THRESHOLD_MS = 24 * 60 * 60 * 1000L
        private const val STARRED_MEDIA_TYPE = "application/vnd.github.star+json"
    }

    override fun getAllStarred(): Flow<List<zed.rainxch.core.domain.model.repository.StarredRepository>> =
        starredRepoDao
            .getAllStarred()
            .map { it.map { entity -> entity.toDomain() } }

    override suspend fun isStarred(repoId: Long): Boolean = starredRepoDao.isStarred(repoId)

    override suspend fun setStarred(
        owner: String,
        repo: String,
        starred: Boolean,
    ): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val response =
                    if (starred) {
                        httpClient.put("/user/starred/$owner/$repo") {
                            header("Content-Length", "0")
                        }
                    } else {
                        httpClient.delete("/user/starred/$owner/$repo")
                    }
                when {
                    response.status.isSuccess() -> {
                        Result.success(Unit)
                    }

                    response.status.value == 401 -> {
                        Result.failure(Exception("Authentication required. Please sign in with GitHub."))
                    }

                    else -> {
                        Result.failure(Exception("Failed to update star: ${response.status.description}"))
                    }
                }
            } catch (e: RateLimitException) {
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun fetchStarredForUsername(
        username: String,
    ): Result<List<zed.rainxch.core.domain.model.repository.StarredRepository>> =
        withContext(Dispatchers.IO) {
            try {
                val sanitized = username.trim().trimStart('@')
                if (sanitized.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Username is empty"))
                }

                val allRepos = mutableListOf<GitHubStarredResponse>()
                var page = 1
                val perPage = 100

                while (true) {
                    val response =
                        httpClient.get("/users/$sanitized/starred") {
                            header(HttpHeaders.Accept, STARRED_MEDIA_TYPE)
                            parameter("per_page", perPage)
                            parameter("page", page)
                        }

                    if (!response.status.isSuccess()) {
                        val reason =
                            when (response.status.value) {
                                404 -> "User '$sanitized' not found."
                                403 -> "GitHub rate limit reached. Try again later or sign in."
                                else -> "Failed to fetch stars: ${response.status.description}"
                            }
                        return@withContext Result.failure(Exception(reason))
                    }

                    val repos = parseStarredPage(gitHubJson, response.body())
                    if (repos.isEmpty()) break
                    allRepos.addAll(repos.map { it.repo.copy(starredAt = it.starredAt) })
                    if (repos.size < perPage) break
                    page++
                }

                val now = Clock.System.now().toEpochMilliseconds()
                val results =
                    coroutineScope {
                        val semaphore = Semaphore(25)
                        allRepos
                            .map { repo ->
                                async {
                                    semaphore.withPermit {
                                        val release =
                                            checkForValidAssets(repo.owner.login, repo.name, ::matchesAnyPlatform)
                                                ?: return@withPermit null
                                        val installedApps = installedAppsDao.getAppsByRepoId(repo.id)
                                        val firstInstalled =
                                            installedApps.firstOrNull { !it.isPendingInstall }
                                        zed.rainxch.core.domain.model.repository.StarredRepository(
                                            repoId = repo.id,
                                            repoName = repo.name,
                                            repoOwner = repo.owner.login,
                                            repoOwnerAvatarUrl = repo.owner.avatarUrl,
                                            repoDescription = repo.description,
                                            primaryLanguage = repo.language,
                                            repoUrl = repo.htmlUrl,
                                            stargazersCount = repo.stargazersCount,
                                            forksCount = repo.forksCount,
                                            openIssuesCount = repo.openIssuesCount,
                                            isInstalled = firstInstalled != null,
                                            installedPackageName = firstInstalled?.packageName,
                                            latestVersion = release.version,
                                            latestReleaseUrl = release.url,
                                            starredAt = repo.starredAt.toEpochMillisecondsOrNull(),
                                            addedAt = now,
                                            lastSyncedAt = now,
                                        )
                                    }
                                }
                            }.awaitAll()
                            .filterNotNull()
                    }

                Result.success(results)
            } catch (e: RateLimitException) {
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "Failed to fetch starred for username" }
                Result.failure(e)
            }
        }

    override suspend fun getLastSyncTime(): Long? = starredRepoDao.getLastSyncTime()

    override suspend fun needsSync(): Boolean {
        val lastSync = getLastSyncTime() ?: return true
        val now = Clock.System.now().toEpochMilliseconds()
        return (now - lastSync) > SYNC_THRESHOLD_MS
    }

    override suspend fun syncStarredRepos(forceRefresh: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                if (!forceRefresh && !needsSync()) {
                    return@withContext Result.success(Unit)
                }

                val allRepos = mutableListOf<GitHubStarredResponse>()
                var page = 1
                val perPage = 100

                while (true) {
                    val response =
                        httpClient.get("/user/starred") {
                            header(HttpHeaders.Accept, STARRED_MEDIA_TYPE)
                            parameter("per_page", perPage)
                            parameter("page", page)
                        }

                    if (!response.status.isSuccess()) {
                        if (response.status.value == 401) {
                            return@withContext Result.failure(
                                Exception("Authentication required. Please sign in with GitHub."),
                            )
                        }
                        return@withContext Result.failure(
                            Exception("Failed to fetch starred repos: ${response.status.description}"),
                        )
                    }

                    val repos = parseStarredPage(gitHubJson, response.body())

                    if (repos.isEmpty()) break

                    allRepos.addAll(repos.map { it.repo.copy(starredAt = it.starredAt) })

                    if (repos.size < perPage) break
                    page++
                }

                val now = Clock.System.now().toEpochMilliseconds()
                val starredRepos = mutableListOf<zed.rainxch.core.domain.model.repository.StarredRepository>()

                coroutineScope {
                    val semaphore = Semaphore(25)
                    val deferredResults =
                        allRepos.map { repo ->
                            async {
                                semaphore.withPermit {
                                    val release = checkForValidAssets(repo.owner.login, repo.name)
                                    val installedApps = installedAppsDao.getAppsByRepoId(repo.id)
                                    val firstInstalled = installedApps.firstOrNull { !it.isPendingInstall }

                                    zed.rainxch.core.domain.model.repository.StarredRepository(
                                        repoId = repo.id,
                                        repoName = repo.name,
                                        repoOwner = repo.owner.login,
                                        repoOwnerAvatarUrl = repo.owner.avatarUrl,
                                        repoDescription = repo.description,
                                        primaryLanguage = repo.language,
                                        repoUrl = repo.htmlUrl,
                                        stargazersCount = repo.stargazersCount,
                                        forksCount = repo.forksCount,
                                        openIssuesCount = repo.openIssuesCount,
                                        isInstalled = firstInstalled != null,
                                        installedPackageName = firstInstalled?.packageName,
                                        latestVersion = release?.version,
                                        latestReleaseUrl = release?.url,
                                        starredAt = repo.starredAt.toEpochMillisecondsOrNull(),
                                        addedAt = now,
                                        lastSyncedAt = now,
                                    )
                                }
                            }
                        }

                    starredRepos.addAll(deferredResults.awaitAll())
                }

                starredRepoDao.replaceAllStarred(starredRepos.map { it.toEntity() })

                Result.success(Unit)
            } catch (e: RateLimitException) {
                Result.failure(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e(e) { "Failed to sync starred repos" }
                Result.failure(e)
            }
        }

    private fun matchesPlatform(assetName: String): Boolean {
        val name = assetName.lowercase()
        return when (platform) {
            Platform.ANDROID -> {
                name.endsWith(".apk")
            }

            Platform.WINDOWS -> {
                name.endsWith(".msi") || name.endsWith(".exe")
            }

            Platform.MACOS -> {
                name.endsWith(".dmg") || name.endsWith(".pkg")
            }

            Platform.LINUX -> {
                name.endsWith(".appimage") || name.endsWith(".deb") ||
                    name.endsWith(".rpm") || name.endsWith(".pkg.tar.zst")
            }
        }
    }

    private fun matchesAnyPlatform(assetName: String): Boolean {
        val name = assetName.lowercase()
        return name.endsWith(".apk") || name.endsWith(".msi") || name.endsWith(".exe") ||
            name.endsWith(".dmg") || name.endsWith(".pkg") || name.endsWith(".appimage") ||
            name.endsWith(".deb") || name.endsWith(".rpm") || name.endsWith(".pkg.tar.zst")
    }

    private suspend fun checkForValidAssets(
        owner: String,
        repo: String,
        matcher: (String) -> Boolean = ::matchesPlatform,
    ): StableReleaseInfo? {
        val backendResult = backendApiClient.getReleases(owner, repo, perPage = 10)
        backendResult.fold(
            onSuccess = { releases ->
                val stable =
                    releases.firstOrNull { it.draft != true && it.prerelease != true }
                        ?: return null
                if (stable.assets.isEmpty()) return null
                return if (stable.assets.any { asset -> matcher(asset.name) }) {
                    StableReleaseInfo(version = stable.tagName, url = stable.htmlUrl)
                } else {
                    null
                }
            },
            onFailure = { error ->
                if (!zed.rainxch.core.data.network
                        .shouldFallbackToGithubOrRethrow(error)
                ) {
                    return null
                }
            },
        )

        return try {
            val releasesResponse =
                httpClient.get("/repos/$owner/$repo/releases") {
                    header("Accept", "application/vnd.github.v3+json")
                    parameter("per_page", 10)
                }

            if (!releasesResponse.status.isSuccess()) {
                return null
            }

            val allReleases: List<GithubReleaseNetworkModel> = releasesResponse.body()

            val stableRelease =
                allReleases.firstOrNull {
                    it.draft != true && it.prerelease != true
                } ?: return null

            if (stableRelease.assets.isEmpty()) {
                return null
            }

            val relevantAssets =
                stableRelease.assets.filter { asset -> matcher(asset.name) }

            if (relevantAssets.isNotEmpty()) {
                StableReleaseInfo(version = stableRelease.tagName, url = stableRelease.htmlUrl)
            } else {
                null
            }
        } catch (e: RateLimitException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e) { "Failed to check valid assets for $owner/$repo" }
            null
        }
    }

    private data class StableReleaseInfo(
        val version: String?,
        val url: String?,
    )

    @Serializable
    private data class GithubReleaseNetworkModel(
        val assets: List<AssetNetworkModel>,
        val draft: Boolean? = null,
        val prerelease: Boolean? = null,
        @SerialName("tag_name") val tagName: String? = null,
        @SerialName("html_url") val htmlUrl: String? = null,
        @SerialName("published_at") val publishedAt: String? = null,
    )

    @Serializable
    private data class AssetNetworkModel(
        val name: String,
    )
}

// A malformed starred_at would otherwise cancel the sync's coroutineScope; it costs only the
// entry's ordering hint.
internal fun String?.toEpochMillisecondsOrNull(): Long? {
    val raw = this ?: return null
    return try {
        Instant.parse(raw).toEpochMilliseconds()
    } catch (e: IllegalArgumentException) {
        Logger.w(e) { "Unparsable starred_at '$raw'; treating the entry as oldest" }
        null
    }
}
