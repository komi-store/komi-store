package zed.rainxch.apps.data.repository

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import zed.rainxch.apps.domain.model.GithubRepoInfo
import zed.rainxch.apps.domain.model.ImportFormat
import zed.rainxch.apps.domain.model.ImportResult
import zed.rainxch.apps.domain.repository.AppsRepository
import zed.rainxch.core.data.dto.GithubRepoNetworkModel
import zed.rainxch.core.data.dto.ReleaseNetwork
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toExportedAppOrSkip
import zed.rainxch.core.data.mappers.toObtainiumApp
import zed.rainxch.core.data.network.BackendApiClient
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.data.network.executeRequest
import zed.rainxch.core.data.network.shouldFallbackToGithubOrRethrow
import zed.rainxch.core.domain.helpers.AppLauncher
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.account.github.isEffectivelyPreRelease
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.installation.DeviceApp
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.transfer.ExportedApp
import zed.rainxch.core.domain.model.transfer.ExportedAppList
import zed.rainxch.core.domain.model.transfer.ObtainiumExport
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.utils.AssetVariant
import zed.rainxch.core.domain.utils.VersionMath
import kotlin.time.Clock
import kotlin.time.TimeSource

class AppsRepositoryImpl(
    private val appLauncher: AppLauncher,
    private val appsRepository: InstalledAppsRepository,
    private val logger: KomiStoreLogger,
    private val clientProvider: GitHubClientProvider,
    private val backendApiClient: BackendApiClient,
    private val packageMonitor: PackageMonitor,
    private val tweaksRepository: TweaksRepository,
    private val forgejoClientRegistry: zed.rainxch.core.data.network.ForgejoClientRegistry,
) : AppsRepository {
    private val httpClient: HttpClient get() = clientProvider.client
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getApps(): Flow<List<InstalledApp>> = appsRepository.getAllInstalledApps()

    override suspend fun openApp(
        installedApp: InstalledApp,
        onCantLaunchApp: () -> Unit,
    ) {
        val canLaunch = appLauncher.canLaunchApp(installedApp)

        if (canLaunch) {
            appLauncher
                .launchApp(installedApp)
                .onFailure { error ->
                    logger.error("Failed to launch app: ${error.message}")
                    onCantLaunchApp()
                }
        } else {
            onCantLaunchApp()
        }
    }

    override suspend fun getLatestRelease(
        owner: String,
        repo: String,
        includePreReleases: Boolean,
        sourceHost: String?,
    ): GithubRelease? {
        if (sourceHost != null) {
            return fetchForgejoLatestRelease(sourceHost, owner, repo, includePreReleases)
        }
        val backendStart = TimeSource.Monotonic.markNow()
        val backendResult = backendApiClient.getReleases(owner, repo, perPage = 10)
        backendResult.fold(
            onSuccess = { releases ->
                logger.debug(
                    "Backend releases for $owner/$repo took ${backendStart.elapsedNow().inWholeMilliseconds} ms",
                )
                return releases
                    .asSequence()
                    .filter { it.draft != true }
                    .filter { includePreReleases || it.prerelease != true }
                    .maxByOrNull { it.publishedAt ?: it.createdAt ?: "" }
                    ?.toDomain()
            },
            onFailure = { error ->
                if (!shouldFallbackToGithubOrRethrow(error)) return null
                logger.warn(
                    "Backend releases for $owner/$repo failed after " +
                        "${backendStart.elapsedNow().inWholeMilliseconds} ms (${error.message}), asking GitHub directly",
                )
            },
        )

        val githubStart = TimeSource.Monotonic.markNow()
        return try {
            val releases =
                withTimeoutOrNull(DIRECT_RELEASES_TIMEOUT_MS) {
                    httpClient
                        .executeRequest<List<ReleaseNetwork>> {
                            get("/repos/$owner/$repo/releases") {
                                header(HttpHeaders.Accept, "application/vnd.github+json")
                                parameter("per_page", 10)
                            }
                        }.getOrThrow()
                } ?: throw IllegalStateException("GitHub did not answer within ${DIRECT_RELEASES_TIMEOUT_MS / 1000} s")
            logger.debug("GitHub releases for $owner/$repo took ${githubStart.elapsedNow().inWholeMilliseconds} ms")

            releases
                .asSequence()
                .filter { it.draft != true }
                .filter { includePreReleases || it.prerelease != true }
                .maxByOrNull { it.publishedAt ?: it.createdAt ?: "" }
                ?.toDomain()
        } catch (e: RateLimitException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error(
                "Failed to fetch latest release for $owner/$repo from GitHub after " +
                    "${githubStart.elapsedNow().inWholeMilliseconds} ms: ${e.message}",
            )
            null
        }
    }

    override suspend fun getDeviceApps(): List<DeviceApp> = packageMonitor.getAllInstalledApps()

    override suspend fun getTrackedPackageNames(): Set<String> =
        appsRepository
            .getAllInstalledApps()
            .first()
            .map { it.packageName }
            .toSet()

    override suspend fun fetchRepoInfo(
        owner: String,
        repo: String,
        sourceHost: String?,
    ): GithubRepoInfo? {
        if (sourceHost != null) {
            return fetchForgejoRepoInfo(sourceHost, owner, repo)
        }
        val backendResult = backendApiClient.getRepo(owner, repo)
        backendResult.fold(
            onSuccess = { backendRepo ->
                val includePreReleases = tweaksRepository.getIncludePreReleases().first()
                val latestTag =
                    if (includePreReleases || backendRepo.latestReleaseTag == null) {
                        resolveLatestTagViaReleases(owner, repo, includePreReleases)
                            ?: backendRepo.latestReleaseTag
                    } else {
                        backendRepo.latestReleaseTag
                    }
                return GithubRepoInfo(
                    id = backendRepo.id,
                    name = backendRepo.name,
                    owner = backendRepo.owner.login,
                    ownerAvatarUrl = backendRepo.owner.avatarUrl.orEmpty(),
                    description = backendRepo.description,
                    language = backendRepo.language,
                    htmlUrl = backendRepo.htmlUrl,
                    latestReleaseTag = latestTag,
                )
            },
            onFailure = { error ->
                if (!shouldFallbackToGithubOrRethrow(error)) return null
            },
        )

        return try {
            val repoModel =
                httpClient
                    .executeRequest<GithubRepoNetworkModel> {
                        get("/repos/$owner/$repo") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                        }
                    }.getOrThrow()

            val includePreReleases = tweaksRepository.getIncludePreReleases().first()
            val latestTag = resolveLatestTagViaReleases(owner, repo, includePreReleases)

            GithubRepoInfo(
                id = repoModel.id,
                name = repoModel.name,
                owner = repoModel.owner.login,
                ownerAvatarUrl = repoModel.owner.avatarUrl,
                description = repoModel.description,
                language = repoModel.language,
                htmlUrl = repoModel.htmlUrl,
                latestReleaseTag = latestTag,
            )
        } catch (e: RateLimitException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Failed to fetch repo info for $owner/$repo: ${e.message}")
            null
        }
    }

    private suspend fun resolveLatestTagViaReleases(
        owner: String,
        repo: String,
        includePreReleases: Boolean,
    ): String? {
        val backendReleases = backendApiClient.getReleases(owner, repo, perPage = 5)
        backendReleases.fold(
            onSuccess = { releases ->
                return releases
                    .asSequence()
                    .filter { it.draft != true }
                    .filter { includePreReleases || it.prerelease != true }
                    .maxByOrNull { it.publishedAt ?: it.createdAt ?: "" }
                    ?.tagName
            },
            onFailure = { error ->
                if (!shouldFallbackToGithubOrRethrow(error)) return null
            },
        )

        return try {
            val releases =
                httpClient
                    .executeRequest<List<ReleaseNetwork>> {
                        get("/repos/$owner/$repo/releases") {
                            header(HttpHeaders.Accept, "application/vnd.github+json")
                            parameter("per_page", 5)
                        }
                    }.getOrThrow()

            releases
                .asSequence()
                .filter { it.draft != true }
                .filter { includePreReleases || it.prerelease != true }
                .maxByOrNull { it.publishedAt ?: it.createdAt ?: "" }
                ?.tagName
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    override suspend fun linkAppToRepo(
        deviceApp: DeviceApp,
        repoInfo: GithubRepoInfo,
        assetFilterRegex: String?,
        fallbackToOlderReleases: Boolean,
        pickedAssetName: String?,
        pickedAssetSiblingCount: Int,
        pickedAssetReleaseTag: String?,
        preferredAssetVariant: String?,
        preferredAssetTokens: String?,
        assetGlobPattern: String?,
        pickedAssetIndex: Int?,
        sourceHost: String?,
    ) {
        val now = Clock.System.now().toEpochMilliseconds()
        val globalPreRelease = tweaksRepository.getIncludePreReleases().first()
        val normalizedFilter = assetFilterRegex?.trim()?.takeIf { it.isNotEmpty() }

        val derivedVariant =
            preferredAssetVariant?.trim()?.takeIf { it.isNotEmpty() }
                ?: pickedAssetName?.let {
                    AssetVariant.deriveFromPickedAsset(it, pickedAssetSiblingCount, pickedAssetReleaseTag)
                }

        val freshFingerprint =
            if (preferredAssetTokens == null && assetGlobPattern == null && pickedAssetName != null) {
                AssetVariant.fingerprintFromPickedAsset(pickedAssetName, pickedAssetSiblingCount, pickedAssetReleaseTag)
            } else {
                null
            }
        val resolvedTokens =
            preferredAssetTokens
                ?: freshFingerprint?.tokens?.let { AssetVariant.serializeTokens(it) }
        val resolvedGlob = assetGlobPattern ?: freshFingerprint?.glob
        val resolvedSiblingCount = pickedAssetSiblingCount.takeIf { it > 0 }

        // Store the release tag (not the APK versionName) when the two can't be reconciled,
        // so update checks compare tag-vs-tag instead of false-nagging (GH#729).
        val reconcilable =
            VersionMath.versionsReconcilable(deviceApp.versionName, repoInfo.latestReleaseTag)
        val initialIsUpdateAvailable =
            reconcilable &&
                VersionMath.isVersionNewer(
                    candidate = repoInfo.latestReleaseTag,
                    current = deviceApp.versionName,
                )
        val initialInstalledVersion =
            if (reconcilable) {
                deviceApp.versionName ?: "unknown"
            } else {
                repoInfo.latestReleaseTag ?: deviceApp.versionName ?: "unknown"
            }

        val installedApp =
            InstalledApp(
                packageName = deviceApp.packageName,
                repoId = repoInfo.id,
                repoName = repoInfo.name,
                repoOwner = repoInfo.owner,
                repoOwnerAvatarUrl = repoInfo.ownerAvatarUrl,
                repoDescription = repoInfo.description,
                primaryLanguage = repoInfo.language,
                repoUrl = repoInfo.htmlUrl,
                installedVersion = initialInstalledVersion,
                installedAssetName = null,
                installedAssetUrl = null,
                latestVersion = repoInfo.latestReleaseTag,
                latestAssetName = null,
                latestAssetUrl = null,
                latestAssetSize = null,
                appName = deviceApp.appName,
                installSource = InstallSource.MANUAL,
                installedAt = now,
                lastCheckedAt = 0L,
                lastUpdatedAt = now,
                isUpdateAvailable = initialIsUpdateAvailable,
                updateCheckEnabled = true,
                releaseNotes = null,
                systemArchitecture = "",
                fileExtension = "apk",
                isPendingInstall = false,
                installedVersionName = deviceApp.versionName,
                installedVersionCode = deviceApp.versionCode,
                signingFingerprint = deviceApp.signingFingerprint,
                includePreReleases = globalPreRelease,
                assetFilterRegex = normalizedFilter,
                fallbackToOlderReleases = fallbackToOlderReleases,
                preferredAssetVariant = derivedVariant,
                preferredVariantStale = false,
                preferredAssetTokens = resolvedTokens,
                assetGlobPattern = resolvedGlob,
                pickedAssetIndex = pickedAssetIndex,
                pickedAssetSiblingCount = resolvedSiblingCount,
                sourceHost = sourceHost,
            )

        appsRepository.saveInstalledApp(installedApp)
    }

    override suspend fun exportApps(): String {
        val apps = appsRepository.getAllInstalledApps().first()
        val exported =
            ExportedAppList(
                version = 4,
                exportedAt = Clock.System.now().toEpochMilliseconds(),
                apps =
                    apps.map { app ->
                        ExportedApp(
                            packageName = app.packageName,
                            repoOwner = app.repoOwner,
                            repoName = app.repoName,
                            repoUrl = app.repoUrl,
                            assetFilterRegex = app.assetFilterRegex,
                            fallbackToOlderReleases = app.fallbackToOlderReleases,
                            preferredAssetVariant = app.preferredAssetVariant,
                            preferredAssetTokens = app.preferredAssetTokens,
                            assetGlobPattern = app.assetGlobPattern,
                            pickedAssetIndex = app.pickedAssetIndex,
                            pickedAssetSiblingCount = app.pickedAssetSiblingCount,
                        )
                    },
            )
        return json.encodeToString(ExportedAppList.serializer(), exported)
    }

    override suspend fun exportObtainium(): String {
        val apps = appsRepository.getAllInstalledApps().first()
        val export =
            ObtainiumExport(
                apps = apps.map { it.toObtainiumApp() },
                overrideExportFormatVersion = OBTAINIUM_EXPORT_FORMAT_VERSION,
            )
        return json.encodeToString(ObtainiumExport.serializer(), export)
    }

    override suspend fun importApps(json: String): ImportResult {
        val parsed =
            try {
                this@AppsRepositoryImpl.json.parseToJsonElement(json)
            } catch (e: Exception) {
                logger.error("Import: not valid JSON: ${e.message}")
                return ImportResult(
                    failed = 1,
                    sourceFormat = ImportFormat.UNKNOWN,
                    unknownFormatPreview = json.take(UNKNOWN_PREVIEW_CHARS),
                )
            }

        val format = detectFormat(parsed)
        return when (format) {
            ImportFormat.NATIVE -> {
                importNative(json)
            }

            ImportFormat.OBTAINIUM -> {
                importObtainium(json)
            }

            ImportFormat.UNKNOWN -> {
                ImportResult(
                    failed = 1,
                    sourceFormat = ImportFormat.UNKNOWN,
                    unknownFormatPreview = json.take(UNKNOWN_PREVIEW_CHARS),
                )
            }
        }
    }

    private fun detectFormat(element: kotlinx.serialization.json.JsonElement): ImportFormat {
        val obj = (element as? JsonObject) ?: return ImportFormat.UNKNOWN
        val apps = obj["apps"] as? kotlinx.serialization.json.JsonArray ?: return ImportFormat.UNKNOWN

        var sawNative = false
        var sawObtainium = false
        for (item in apps) {
            val app = item as? JsonObject ?: continue
            if (app.containsKey("repoOwner") && app.containsKey("repoName")) {
                sawNative = true
                break
            }
            if (app.containsKey("id") && app.containsKey("url")) {
                val url = app["url"]?.jsonPrimitive?.contentOrNull
                if (url?.contains("github.com", ignoreCase = true) == true) {
                    sawObtainium = true
                }
            }
        }
        if (sawNative) return ImportFormat.NATIVE
        if (sawObtainium) return ImportFormat.OBTAINIUM

        if (apps.isEmpty()) {
            val versionNode = obj["version"]
            if (versionNode is kotlinx.serialization.json.JsonPrimitive && versionNode.contentOrNull?.toIntOrNull() != null) {
                return ImportFormat.NATIVE
            }
            if (obj.containsKey("overrideExportFormatVersion") || obj.containsKey("settings")) {
                return ImportFormat.OBTAINIUM
            }
        }
        return ImportFormat.UNKNOWN
    }

    private suspend fun importNative(rawJson: String): ImportResult {
        val exportedList =
            try {
                json.decodeFromString(ExportedAppList.serializer(), rawJson)
            } catch (e: Exception) {
                logger.error("Failed to parse native import JSON: ${e.message}")
                return ImportResult(
                    failed = 1,
                    sourceFormat = ImportFormat.NATIVE,
                )
            }

        val trackedPackages = getTrackedPackageNames()
        val imported = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val notInstalled = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for (exportedApp in exportedList.apps) {
            val label = "${exportedApp.repoOwner}/${exportedApp.repoName}"
            if (exportedApp.packageName in trackedPackages) {
                skipped += label
                continue
            }
            try {
                // A row is an installed app; one saved for a missing package shows Open and Uninstall.
                val systemInfo = packageMonitor.getInstalledPackageInfo(exportedApp.packageName)
                if (systemInfo == null) {
                    notInstalled += label
                    continue
                }

                val repoInfo = fetchRepoInfo(exportedApp.repoOwner, exportedApp.repoName)
                if (repoInfo == null) {
                    failed += label
                    continue
                }

                val deviceApp =
                    DeviceApp(
                        packageName = exportedApp.packageName,
                        appName = exportedApp.repoName,
                        versionName = systemInfo.versionName,
                        versionCode = systemInfo.versionCode,
                        signingFingerprint = systemInfo.signingFingerprint,
                    )
                linkAppToRepo(
                    deviceApp = deviceApp,
                    repoInfo = repoInfo,
                    assetFilterRegex = exportedApp.assetFilterRegex,
                    fallbackToOlderReleases = exportedApp.fallbackToOlderReleases,
                    pickedAssetSiblingCount = exportedApp.pickedAssetSiblingCount ?: 0,
                    preferredAssetVariant = exportedApp.preferredAssetVariant,
                    preferredAssetTokens = exportedApp.preferredAssetTokens,
                    assetGlobPattern = exportedApp.assetGlobPattern,
                    pickedAssetIndex = exportedApp.pickedAssetIndex,
                )
                imported += label
            } catch (e: Exception) {
                logger.error("Failed to import $label: ${e.message}")
                failed += label
            }
        }

        return ImportResult(
            imported = imported.size,
            skipped = skipped.size,
            failed = failed.size,
            notInstalled = notInstalled.size,
            importedItems = imported.toImmutableList(),
            skippedItems = skipped.toImmutableList(),
            notInstalledItems = notInstalled.toImmutableList(),
            failedItems = failed.toImmutableList(),
            sourceFormat = ImportFormat.NATIVE,
        )
    }

    private suspend fun importObtainium(rawJson: String): ImportResult {
        val export =
            try {
                json.decodeFromString(ObtainiumExport.serializer(), rawJson)
            } catch (e: Exception) {
                logger.error("Failed to parse Obtainium import JSON: ${e.message}")
                return ImportResult(
                    failed = 1,
                    sourceFormat = ImportFormat.OBTAINIUM,
                )
            }

        val trackedPackages = getTrackedPackageNames()
        val imported = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val notInstalled = mutableListOf<String>()
        val failed = mutableListOf<String>()
        val nonGitHub = mutableListOf<String>()

        for (obtainiumApp in export.apps) {
            val mapped = obtainiumApp.toExportedAppOrSkip(json)
            val exportedApp = mapped.exported
            if (exportedApp == null) {
                nonGitHub += listOfNotNull(mapped.nonGitHubLabel)
                failed += listOfNotNull(mapped.unsupportedFailureLabel)
                continue
            }

            val label = "${exportedApp.repoOwner}/${exportedApp.repoName}"
            if (exportedApp.packageName in trackedPackages) {
                skipped += label
                continue
            }

            try {
                val systemInfo = packageMonitor.getInstalledPackageInfo(exportedApp.packageName)
                if (systemInfo == null) {
                    notInstalled += label
                    continue
                }
                val repoInfo = fetchRepoInfo(exportedApp.repoOwner, exportedApp.repoName)
                if (repoInfo == null) {
                    failed += label
                    continue
                }
                val deviceApp =
                    DeviceApp(
                        packageName = exportedApp.packageName,
                        appName = obtainiumApp.name?.takeIf { it.isNotBlank() } ?: exportedApp.repoName,
                        versionName = systemInfo.versionName,
                        versionCode = systemInfo.versionCode,
                        signingFingerprint = systemInfo.signingFingerprint,
                    )
                linkAppToRepo(
                    deviceApp = deviceApp,
                    repoInfo = repoInfo,
                    assetFilterRegex = exportedApp.assetFilterRegex,
                    fallbackToOlderReleases = exportedApp.fallbackToOlderReleases,
                    pickedAssetIndex = exportedApp.pickedAssetIndex,
                )
                imported += label
            } catch (e: Exception) {
                logger.error("Failed to import Obtainium app $label: ${e.message}")
                failed += label
            }
        }

        return ImportResult(
            imported = imported.size,
            skipped = skipped.size,
            failed = failed.size,
            nonGitHubSkipped = nonGitHub.size,
            notInstalled = notInstalled.size,
            importedItems = imported.toImmutableList(),
            skippedItems = skipped.toImmutableList(),
            nonGitHubItems = nonGitHub.toImmutableList(),
            notInstalledItems = notInstalled.toImmutableList(),
            failedItems = failed.toImmutableList(),
            sourceFormat = ImportFormat.OBTAINIUM,
        )
    }

    private suspend fun fetchForgejoLatestRelease(
        host: String,
        owner: String,
        repo: String,
        includePreReleases: Boolean,
    ): GithubRelease? {
        val client = forgejoClientRegistry.clientFor(host)
        return try {
            val releases =
                client.getReleases(owner, repo, perPage = 10).getOrNull()
                    ?: return null
            releases
                .asSequence()
                .filter { it.draft != true }
                .map { it.toDomain() }
                .filter {
                    includePreReleases || !it.isEffectivelyPreRelease()
                }.maxByOrNull { it.publishedAt }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Forgejo latest-release fetch failed for $host/$owner/$repo: ${e.message}")
            null
        }
    }

    private suspend fun fetchForgejoRepoInfo(
        host: String,
        owner: String,
        repo: String,
    ): GithubRepoInfo? {
        val client = forgejoClientRegistry.clientFor(host)
        return try {
            val repoModel = client.getRepository(owner, repo).getOrNull() ?: return null
            val latestTag = client.getLatestRelease(owner, repo).getOrNull()?.tagName
            GithubRepoInfo(
                id =
                    zed.rainxch.core.domain.utils.RepoIdCodec
                        .encode(host, repoModel.id),
                name = repoModel.name,
                owner = repoModel.owner.login,
                ownerAvatarUrl = repoModel.owner.avatarUrl,
                description = repoModel.description,
                language = repoModel.language,
                htmlUrl = repoModel.htmlUrl,
                latestReleaseTag = latestTag,
            )
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.error("Forgejo repo fetch failed for $host/$owner/$repo: ${e.message}")
            null
        }
    }

    private companion object {
        const val UNKNOWN_PREVIEW_CHARS = 200
        const val OBTAINIUM_EXPORT_FORMAT_VERSION = 1
        const val DIRECT_RELEASES_TIMEOUT_MS = 20_000L
    }
}
