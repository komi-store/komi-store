@file:OptIn(ExperimentalTime::class)

package zed.rainxch.core.data.repository

import androidx.sqlite.SQLiteException
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.put
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
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
import zed.rainxch.core.data.dto.parseStarredPage
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.dao.StarredRepoDao
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toEntity
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.getReleases
import zed.rainxch.core.data.network.gitHubJson
import zed.rainxch.core.data.network.recoverHttpFailure
import zed.rainxch.core.data.network.shouldFallbackToGithubOrRethrow
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.StarredRepository
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import zed.rainxch.core.domain.model.repository.StarredRepository as StarredRepo

class StarredRepositoryImpl(
    private val starredRepoDao: StarredRepoDao,
    private val installedAppsDao: InstalledAppDao,
    private val platform: Platform,
    private val clientProvider: GitHubClientProvider,
    private val backendApiClient: BackendApiClient,
) : StarredRepository {
    private val httpClient: HttpClient get() = clientProvider.client

    companion object {
        private const val SYNC_THRESHOLD_MS = 24 * 60 * 60 * 1000L
        private const val STARRED_MEDIA_TYPE = "application/vnd.github.star+json"
        private const val STARRED_PAGE_SIZE = 100
        private const val RELEASE_CHECK_PAGE_SIZE = 10
        private const val RELEASE_CHECK_CONCURRENCY = 25
        private const val SIGN_IN_REQUIRED = "Authentication required. Please sign in with GitHub."
    }

    override fun getAllStarred(): Flow<List<StarredRepo>> =
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
                recoverHttpFailure({ Result.failure(it) }) {
                    val response =
                        if (starred) {
                            httpClient.put("/user/starred/$owner/$repo") {
                                header("Content-Length", "0")
                            }
                        } else {
                            httpClient.delete("/user/starred/$owner/$repo")
                        }
                    when {
                        response.status.isSuccess() -> Result.success(Unit)
                        response.status == HttpStatusCode.Unauthorized -> Result.failure(Exception(SIGN_IN_REQUIRED))
                        else -> Result.failure(Exception("Failed to update star: ${response.status.description}"))
                    }
                }
            } catch (e: RateLimitException) {
                Result.failure(e)
            }
        }

    override suspend fun fetchStarredForUsername(username: String): Result<List<StarredRepo>> =
        withContext(Dispatchers.IO) {
            val sanitized = username.trim().trimStart('@')
            if (sanitized.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Username is empty"))
            }
            starredCall("Failed to fetch starred for username") {
                fetchAllStarred("/users/$sanitized/starred") { status ->
                    Exception(
                        when (status) {
                            HttpStatusCode.NotFound -> "User '$sanitized' not found."
                            HttpStatusCode.Forbidden -> "GitHub rate limit reached. Try again later or sign in."
                            else -> "Failed to fetch stars: ${status.description}"
                        },
                    )
                }.map { repos ->
                    val now = Clock.System.now().toEpochMilliseconds()
                    repos
                        .mapWithReleaseChecks { repo ->
                            checkForValidAssets(repo.owner.login, repo.name, ::matchesAnyPlatform)?.let { release ->
                                repo.toStarred(release, now)
                            }
                        }.filterNotNull()
                }
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
            starredCall("Failed to sync starred repos") {
                if (!forceRefresh && !needsSync()) {
                    Result.success(Unit)
                } else {
                    fetchAllStarred("/user/starred") { status ->
                        Exception(
                            if (status == HttpStatusCode.Unauthorized) {
                                SIGN_IN_REQUIRED
                            } else {
                                "Failed to fetch starred repos: ${status.description}"
                            },
                        )
                    }.map { repos ->
                        val now = Clock.System.now().toEpochMilliseconds()
                        val starred =
                            repos.mapWithReleaseChecks { repo ->
                                repo.toStarred(checkForValidAssets(repo.owner.login, repo.name), now)
                            }
                        starredRepoDao.replaceAllStarred(starred.map { it.toEntity() })
                    }
                }
            }
        }

    // A rate limit reaches the caller untouched; the network, a page that won't parse or Room fail the call and are logged.
    private inline fun <T> starredCall(
        failureLog: String,
        block: () -> Result<T>,
    ): Result<T> =
        try {
            recoverHttpFailure(
                { e ->
                    Logger.e(e) { failureLog }
                    Result.failure(e)
                },
                block,
            )
        } catch (e: RateLimitException) {
            Result.failure(e)
        } catch (e: SQLiteException) {
            Logger.e(e) { failureLog }
            Result.failure(e)
        }

    // Every page of a starred list, or the refusal for the first page GitHub turned down.
    private suspend fun fetchAllStarred(
        path: String,
        refusal: (HttpStatusCode) -> Exception,
    ): Result<List<GitHubStarredResponse>> {
        val all = mutableListOf<GitHubStarredResponse>()
        var page = 1
        while (true) {
            val response =
                httpClient.get(path) {
                    header(HttpHeaders.Accept, STARRED_MEDIA_TYPE)
                    parameter("per_page", STARRED_PAGE_SIZE)
                    parameter("page", page)
                }
            if (!response.status.isSuccess()) return Result.failure(refusal(response.status))
            val repos = parseStarredPage(gitHubJson, response.body())
            all += repos.map { it.repo.copy(starredAt = it.starredAt) }
            if (repos.size < STARRED_PAGE_SIZE) return Result.success(all)
            page++
        }
    }

    private suspend fun <T> List<GitHubStarredResponse>.mapWithReleaseChecks(transform: suspend (GitHubStarredResponse) -> T): List<T> =
        coroutineScope {
            val semaphore = Semaphore(RELEASE_CHECK_CONCURRENCY)
            map { repo -> async { semaphore.withPermit { transform(repo) } } }.awaitAll()
        }

    private suspend fun GitHubStarredResponse.toStarred(
        release: StableReleaseInfo?,
        now: Long,
    ): StarredRepo {
        val firstInstalled = installedAppsDao.getAppsByRepoId(id).firstOrNull { !it.isPendingInstall }
        return StarredRepo(
            repoId = id,
            repoName = name,
            repoOwner = owner.login,
            repoOwnerAvatarUrl = owner.avatarUrl,
            repoDescription = description,
            primaryLanguage = language,
            repoUrl = htmlUrl,
            stargazersCount = stargazersCount,
            forksCount = forksCount,
            openIssuesCount = openIssuesCount,
            isInstalled = firstInstalled != null,
            installedPackageName = firstInstalled?.packageName,
            latestVersion = release?.version,
            latestReleaseUrl = release?.url,
            starredAt = starredAt.toEpochMillisecondsOrNull(),
            addedAt = now,
            lastSyncedAt = now,
        )
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
    ): StableReleaseInfo? =
        backendApiClient.getReleases(owner, repo, perPage = RELEASE_CHECK_PAGE_SIZE).fold(
            onSuccess = { releases ->
                releases
                    .firstOrNull { it.draft != true && it.prerelease != true }
                    ?.takeIf { stable -> stable.assets.any { matcher(it.name) } }
                    ?.let { StableReleaseInfo(version = it.tagName, url = it.htmlUrl) }
            },
            onFailure = { error ->
                if (shouldFallbackToGithubOrRethrow(error)) githubStableRelease(owner, repo, matcher) else null
            },
        )

    private suspend fun githubStableRelease(
        owner: String,
        repo: String,
        matcher: (String) -> Boolean,
    ): StableReleaseInfo? =
        recoverHttpFailure(
            { e ->
                Logger.w(e) { "Failed to check valid assets for $owner/$repo" }
                null
            },
        ) {
            val response =
                httpClient.get("/repos/$owner/$repo/releases") {
                    header("Accept", "application/vnd.github.v3+json")
                    parameter("per_page", RELEASE_CHECK_PAGE_SIZE)
                }
            if (response.status.isSuccess()) {
                response
                    .body<List<GithubReleaseNetworkModel>>()
                    .firstOrNull { it.draft != true && it.prerelease != true }
                    ?.takeIf { stable -> stable.assets.any { matcher(it.name) } }
                    ?.let { StableReleaseInfo(version = it.tagName, url = it.htmlUrl) }
            } else {
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

// A malformed starred_at would cancel the sync's coroutineScope; dropping it costs only the entry's ordering hint.
internal fun String?.toEpochMillisecondsOrNull(): Long? {
    val raw = this ?: return null
    return try {
        Instant.parse(raw).toEpochMilliseconds()
    } catch (e: IllegalArgumentException) {
        Logger.w(e) { "Unparsable starred_at '$raw'; treating the entry as oldest" }
        null
    }
}
