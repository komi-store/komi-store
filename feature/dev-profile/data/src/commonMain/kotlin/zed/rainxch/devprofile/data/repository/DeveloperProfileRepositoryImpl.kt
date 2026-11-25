package zed.rainxch.devprofile.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.asNetworkFailure
import zed.rainxch.core.data.network.getReleases
import zed.rainxch.core.data.network.getUser
import zed.rainxch.core.data.network.getUserRepos
import zed.rainxch.core.data.network.networkFailures
import zed.rainxch.core.data.network.shouldFallbackToGithubOrRethrow
import zed.rainxch.core.data.repository.localStore
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.NetworkException
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.FavouritesRepository
import zed.rainxch.devprofile.data.dto.ContributionsResponse
import zed.rainxch.devprofile.data.dto.GitHubRepoResponse
import zed.rainxch.devprofile.data.dto.GitHubUserResponse
import zed.rainxch.devprofile.data.mappers.toDeveloperProfile
import zed.rainxch.devprofile.data.mappers.toDomain
import zed.rainxch.devprofile.data.mappers.toGitHubRepoResponse
import zed.rainxch.devprofile.domain.model.ContributionCalendar
import zed.rainxch.devprofile.domain.model.DeveloperProfile
import zed.rainxch.devprofile.domain.model.DeveloperRepository
import zed.rainxch.devprofile.domain.repository.DeveloperProfileRepository

class DeveloperProfileRepositoryImpl(
    private val clientProvider: GitHubClientProvider,
    private val backendApiClient: BackendApiClient,
    private val platform: Platform,
    private val installedAppsDao: InstalledAppDao,
    private val favouritesRepository: FavouritesRepository,
    private val logger: KomiStoreLogger,
) : DeveloperProfileRepository {
    private val httpClient: HttpClient get() = clientProvider.client

    override suspend fun getDeveloperProfile(username: String): Result<DeveloperProfile> {
        return withContext(Dispatchers.IO) {
            backendApiClient.getUser(username).fold(
                onSuccess = { return@withContext Result.success(it.toDeveloperProfile()) },
                onFailure = { error ->
                    if (!shouldFallbackToGithubOrRethrow(error)) return@withContext Result.failure(error)
                },
            )

            try {
                networkFailures {
                    val response = httpClient.get("/users/$username")
                    if (response.status.isSuccess()) {
                        Result.success(response.body<GitHubUserResponse>().toDomain())
                    } else {
                        Result.failure(NetworkException("Failed to fetch developer profile: ${response.status.description}", null))
                    }
                }
            } catch (e: NetworkException) {
                logger.error("Failed to fetch developer profile for $username", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun getContributionCalendar(username: String): Result<ContributionCalendar> =
        withContext(Dispatchers.IO) {
            try {
                networkFailures {
                    val response =
                        httpClient.get {
                            url("$CONTRIBUTIONS_API/$username?y=last")
                            headers { remove("X-GitHub-Token") }
                        }
                    if (response.status.isSuccess()) {
                        Result.success(response.body<ContributionsResponse>().toDomain())
                    } else {
                        Result.failure(NetworkException("Failed to fetch contributions: ${response.status.description}", null))
                    }
                }
            } catch (e: NetworkException) {
                logger.warn("Failed to fetch contributions for $username: ${e.message}")
                Result.failure(e)
            } catch (e: RateLimitException) {
                logger.warn("Failed to fetch contributions for $username: rate limited")
                Result.failure(e)
            }
        }

    override suspend fun getDeveloperRepositories(username: String): Result<List<DeveloperRepository>> =
        withContext(Dispatchers.IO) {
            try {
                val repos = ownedRepos(username)
                val favoriteIds =
                    favouritesRepository
                        .getAllFavorites()
                        .first()
                        .map { it.repoId }
                        .toSet()
                Result.success(withReleaseChecks(repos, favoriteIds))
            } catch (e: NetworkException) {
                logger.error("Failed to fetch repositories for $username", e)
                Result.failure(e)
            } catch (e: LocalStoreException) {
                logger.error("Failed to fetch repositories for $username", e)
                Result.failure(e)
            }
        }

    // Pages through the user's own repos, backend first; once the backend falls back, the rest come from GitHub.
    private suspend fun ownedRepos(username: String): List<GitHubRepoResponse> {
        val repos = mutableListOf<GitHubRepoResponse>()
        var useBackend = true
        var page = 1
        do {
            val backendPage = if (useBackend) backendRepoPage(username, page) else null
            useBackend = backendPage != null
            val pageRepos = backendPage ?: githubRepoPage(username, page)
            repos += pageRepos.filter { !it.archived && !it.fork }
            page++
        } while (pageRepos.size >= REPOS_PAGE_SIZE)
        return repos
    }

    // Null asks for the GitHub fallback.
    private suspend fun backendRepoPage(
        username: String,
        page: Int,
    ): List<GitHubRepoResponse>? =
        backendApiClient
            .getUserRepos(
                username = username,
                page = page,
                perPage = REPOS_PAGE_SIZE,
                sort = "updated",
                type = "owner",
            ).fold(
                onSuccess = { repos -> repos.map { it.toGitHubRepoResponse() } },
                onFailure = { error -> if (shouldFallbackToGithubOrRethrow(error)) null else throw error.asNetworkFailure() },
            )

    private suspend fun githubRepoPage(
        username: String,
        page: Int,
    ): List<GitHubRepoResponse> =
        networkFailures {
            val response =
                httpClient.get("/users/$username/repos") {
                    parameter("per_page", REPOS_PAGE_SIZE)
                    parameter("page", page)
                    parameter("type", "owner")
                    parameter("sort", "updated")
                    parameter("direction", "desc")
                }
            if (!response.status.isSuccess()) {
                throw NetworkException("Failed to fetch repositories: ${response.status.description}", null)
            }
            response.body()
        }

    private suspend fun withReleaseChecks(
        repos: List<GitHubRepoResponse>,
        favoriteIds: Set<Long>,
    ): List<DeveloperRepository> =
        coroutineScope {
            val semaphore = Semaphore(RELEASE_CHECK_CONCURRENCY)
            repos
                .map { repo ->
                    async { semaphore.withPermit { processRepository(repo, favoriteIds) } }
                }.awaitAll()
        }

    private suspend fun processRepository(
        repo: GitHubRepoResponse,
        favoriteIds: Set<Long>,
    ): DeveloperRepository {
        val installedApps = localStore { installedAppsDao.getAppsByRepoId(repo.id) }
        val info = releaseInfo(owner = repo.fullName.split("/")[0], repoName = repo.name)
        return repo.toDomain(
            hasReleases = info.hasReleases,
            hasInstallableAssets = info.hasInstallable,
            isInstalled = installedApps.any { !it.isPendingInstall },
            isFavorite = favoriteIds.contains(repo.id),
            latestVersion = info.latestVersion,
            latestReleaseAt = info.publishedAt,
        )
    }

    private suspend fun releaseInfo(
        owner: String,
        repoName: String,
    ): ReleaseInfo =
        backendApiClient
            .getReleases(owner, repoName, perPage = RELEASE_CHECK_PAGE_SIZE)
            .fold(
                onSuccess = { releases -> releases.map { it.toReleaseNetworkModel() }.toReleaseInfo() },
                onFailure = { error ->
                    if (shouldFallbackToGithubOrRethrow(error)) githubReleaseInfo(owner, repoName) else ReleaseInfo.EMPTY
                },
            )

    private suspend fun githubReleaseInfo(
        owner: String,
        repoName: String,
    ): ReleaseInfo =
        try {
            networkFailures {
                val response =
                    httpClient.get("/repos/$owner/$repoName/releases") {
                        parameter("per_page", RELEASE_CHECK_PAGE_SIZE)
                    }
                if (response.status.isSuccess()) {
                    response.body<List<ReleaseNetworkModel>>().toReleaseInfo()
                } else {
                    ReleaseInfo.EMPTY
                }
            }
        } catch (e: NetworkException) {
            logger.warn("Failed to check releases for $owner/$repoName : ${e.message}")
            ReleaseInfo.EMPTY
        }

    private fun List<ReleaseNetworkModel>.toReleaseInfo(): ReleaseInfo {
        val stable =
            firstOrNull { it.draft != true && it.prerelease != true }
                ?: return ReleaseInfo(hasReleases = isNotEmpty(), hasInstallable = false, latestVersion = null, publishedAt = null)
        val extensions = platform.installerExtensions()
        return ReleaseInfo(
            hasReleases = true,
            hasInstallable = stable.assets.any { asset -> extensions.any { asset.name.lowercase().endsWith(it) } },
            latestVersion = stable.tagName,
            publishedAt = stable.publishedAt,
        )
    }

    private fun Platform.installerExtensions(): List<String> =
        when (this) {
            Platform.ANDROID -> listOf(".apk")
            Platform.WINDOWS -> listOf(".msi", ".exe")
            Platform.MACOS -> listOf(".dmg", ".pkg")
            Platform.LINUX -> listOf(".appimage", ".deb", ".rpm", ".pkg.tar.zst")
        }

    private fun ReleaseNetwork.toReleaseNetworkModel(): ReleaseNetworkModel =
        ReleaseNetworkModel(
            assets = assets.map { AssetNetworkModel(name = it.name) },
            draft = draft,
            prerelease = prerelease,
            tagName = tagName,
            publishedAt = publishedAt,
        )

    private data class ReleaseInfo(
        val hasReleases: Boolean,
        val hasInstallable: Boolean,
        val latestVersion: String?,
        val publishedAt: String?,
    ) {
        companion object {
            val EMPTY = ReleaseInfo(false, false, null, null)
        }
    }

    @Serializable
    private data class ReleaseNetworkModel(
        val assets: List<AssetNetworkModel>,
        val draft: Boolean? = null,
        val prerelease: Boolean? = null,
        @SerialName("tag_name") val tagName: String,
        @SerialName("published_at") val publishedAt: String? = null,
    )

    @Serializable
    private data class AssetNetworkModel(
        val name: String,
    )

    private companion object {
        const val CONTRIBUTIONS_API = "https://github-contributions-api.jogruber.de/v4"
        const val REPOS_PAGE_SIZE = 100
        const val RELEASE_CHECK_PAGE_SIZE = 10
        const val RELEASE_CHECK_CONCURRENCY = 20
    }
}
