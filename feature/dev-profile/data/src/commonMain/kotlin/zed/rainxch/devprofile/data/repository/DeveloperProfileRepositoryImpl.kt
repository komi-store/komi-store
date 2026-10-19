package zed.rainxch.devprofile.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.url
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
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
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.shouldFallbackToGithubOrRethrow
import zed.rainxch.core.domain.logging.KomiStoreLogger
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
import zed.rainxch.devprofile.domain.model.ContributionDay
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
            val backendResult = backendApiClient.getUser(username)
            backendResult.fold(
                onSuccess = { return@withContext Result.success(it.toDeveloperProfile()) },
                onFailure = { error ->
                    if (!shouldFallbackToGithubOrRethrow(error)) return@withContext Result.failure(error)
                },
            )

            try {
                val response = httpClient.get("/users/$username")

                if (!response.status.isSuccess()) {
                    return@withContext Result.failure(
                        Exception("Failed to fetch developer profile: ${response.status.description}"),
                    )
                }

                val userResponse: GitHubUserResponse = response.body()
                Result.success(userResponse.toDomain())
            } catch (e: RateLimitException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to fetch developer profile for $username")
                Result.failure(e)
            }
        }
    }

    override suspend fun getContributionCalendar(username: String): Result<ContributionCalendar> {
        return withContext(Dispatchers.IO) {
            try {
                val response =
                    httpClient.get {
                        url("https://github-contributions-api.jogruber.de/v4/$username?y=last")
                        headers { remove("X-GitHub-Token") }
                    }
                if (!response.status.isSuccess()) {
                    return@withContext Result.failure(
                        Exception("Failed to fetch contributions: ${response.status.description}"),
                    )
                }
                val body: ContributionsResponse = response.body()
                val days =
                    body.contributions.map {
                        ContributionDay(date = it.date, count = it.count, level = it.level)
                    }
                val total = body.total["lastYear"] ?: body.total.values.firstOrNull() ?: 0
                Result.success(ContributionCalendar(totalLastYear = total, days = days))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn("Failed to fetch contributions for $username: ${e.message}")
                Result.failure(e)
            }
        }
    }

    override suspend fun getDeveloperRepositories(username: String): Result<List<DeveloperRepository>> {
        return withContext(Dispatchers.IO) {
            try {
                val allRepos = mutableListOf<GitHubRepoResponse>()
                var page = 1
                val perPage = 100
                var useBackend = true

                while (true) {
                    val pageRepos: List<GitHubRepoResponse> =
                        if (useBackend) {
                            val backendResult =
                                backendApiClient.getUserRepos(
                                    username = username,
                                    page = page,
                                    perPage = perPage,
                                    sort = "updated",
                                    type = "owner",
                                )
                            val mapped =
                                backendResult.fold(
                                    onSuccess = { it.map { repo -> repo.toGitHubRepoResponse() } },
                                    onFailure = { error ->
                                        if (!shouldFallbackToGithubOrRethrow(error)) {
                                            return@withContext Result.failure(error)
                                        }
                                        useBackend = false
                                        null
                                    },
                                )
                            if (mapped != null) {
                                mapped
                            } else {
                                continue
                            }
                        } else {
                            val response =
                                httpClient.get("/users/$username/repos") {
                                    parameter("per_page", perPage)
                                    parameter("page", page)
                                    parameter("type", "owner")
                                    parameter("sort", "updated")
                                    parameter("direction", "desc")
                                }

                            if (!response.status.isSuccess()) {
                                return@withContext Result.failure(
                                    Exception("Failed to fetch repositories: ${response.status.description}"),
                                )
                            }

                            response.body()
                        }

                    if (pageRepos.isEmpty()) break

                    allRepos.addAll(pageRepos.filter { !it.archived && !it.fork })

                    if (pageRepos.size < perPage) break
                    page++
                }

                val allFavorites = favouritesRepository.getAllFavorites().first()
                val favoriteIds = allFavorites.map { it.repoId }.toSet()

                val processedRepos =
                    coroutineScope {
                        val semaphore = Semaphore(20)
                        val deferredResults =
                            allRepos.map { repo ->
                                async {
                                    semaphore.withPermit {
                                        processRepository(repo, favoriteIds)
                                    }
                                }
                            }
                        deferredResults.awaitAll()
                    }

                Result.success(processedRepos)
            } catch (e: RateLimitException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error("Failed to fetch repositories for $username")
                Result.failure(e)
            }
        }
    }

    private suspend fun processRepository(
        repo: GitHubRepoResponse,
        favoriteIds: Set<Long>,
    ): DeveloperRepository {
        val installedApps = installedAppsDao.getAppsByRepoId(repo.id)
        val isFavorite = favoriteIds.contains(repo.id)

        val info =
            checkReleaseInfo(
                owner = repo.fullName.split("/")[0],
                repoName = repo.name,
            )

        return repo.toDomain(
            hasReleases = info.hasReleases,
            hasInstallableAssets = info.hasInstallable,
            isInstalled = installedApps.any { !it.isPendingInstall },
            isFavorite = isFavorite,
            latestVersion = info.latestVersion,
            latestReleaseAt = info.publishedAt,
        )
    }

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

    private suspend fun checkReleaseInfo(
        owner: String,
        repoName: String,
    ): ReleaseInfo {
        val backendResult = backendApiClient.getReleases(owner, repoName, perPage = 10)
        backendResult.fold(
            onSuccess = { releases ->
                val stableRelease = releases.firstOrNull { it.draft != true && it.prerelease != true }
                if (stableRelease == null) {
                    return ReleaseInfo(releases.isNotEmpty(), false, null, null)
                }
                val hasInstallable =
                    stableRelease.assets.any { asset ->
                        val name = asset.name.lowercase()
                        when (platform) {
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
                return ReleaseInfo(
                    hasReleases = true,
                    hasInstallable = hasInstallable,
                    latestVersion = stableRelease.tagName,
                    publishedAt = stableRelease.publishedAt,
                )
            },
            onFailure = { error ->
                if (!shouldFallbackToGithubOrRethrow(error)) return ReleaseInfo.EMPTY
            },
        )

        return try {
            val response =
                httpClient.get("/repos/$owner/$repoName/releases") {
                    parameter("per_page", 10)
                }

            if (!response.status.isSuccess()) {
                return ReleaseInfo.EMPTY
            }

            val releases: List<ReleaseNetworkModel> = response.body()

            val stableRelease =
                releases.firstOrNull {
                    it.draft != true && it.prerelease != true
                }

            if (stableRelease == null) {
                return ReleaseInfo(releases.isNotEmpty(), false, null, null)
            }

            val hasInstallableAssets =
                stableRelease.assets.any { asset ->
                    val name = asset.name.lowercase()
                    when (platform) {
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

            ReleaseInfo(
                hasReleases = true,
                hasInstallable = hasInstallableAssets,
                latestVersion = stableRelease.tagName,
                publishedAt = stableRelease.publishedAt,
            )
        } catch (e: RateLimitException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to check releases for $owner/$repoName : ${e.message}")
            ReleaseInfo.EMPTY
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
}
