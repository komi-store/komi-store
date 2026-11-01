package zed.rainxch.core.data.repository

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import co.touchlab.kermit.Logger
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zed.rainxch.core.data.local.db.AppDatabase
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.dao.UpdateHistoryDao
import zed.rainxch.core.data.local.db.dao.recordUnmatchedRelease
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.data.local.db.entities.UpdateHistoryEntity
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toEntity
import zed.rainxch.core.data.mappers.toReleaseWindow
import zed.rainxch.core.data.mappers.toUpdateCheckWindow
import zed.rainxch.core.data.network.GitHubClientProvider
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.installation.InstallSource
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.ParkedInstallDisposal
import zed.rainxch.core.domain.model.installation.clearPending
import zed.rainxch.core.domain.model.installation.confirmInstall
import zed.rainxch.core.domain.model.installation.markPending
import zed.rainxch.core.domain.model.smart_detect.MatchingPreview
import zed.rainxch.core.domain.network.DigestVerifier
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.utils.AssetFilter
import zed.rainxch.core.domain.utils.ResolvedRelease
import zed.rainxch.core.domain.utils.VersionMath
import kotlin.coroutines.cancellation.CancellationException

class InstalledAppsRepositoryImpl(
    private val database: AppDatabase,
    private val installedAppsDao: InstalledAppDao,
    private val historyDao: UpdateHistoryDao,
    private val installer: Installer,
    private val clientProvider: GitHubClientProvider,
    private val backendApiClient: zed.rainxch.core.data.network.BackendApiClient,
    private val forgejoClientRegistry: zed.rainxch.core.data.network.ForgejoClientRegistry,
    private val packageMonitor: PackageMonitor,
    private val digestVerifier: DigestVerifier,
) : InstalledAppsRepository {
    private val parkedInstallSettler =
        ParkedInstallSettler(
            dao = installedAppsDao,
            packageMonitor = packageMonitor,
            digestVerifier = digestVerifier,
            discard = { packageName, path -> discardParkedInstall(packageName, path) },
        )

    private val checkAllMutex = Mutex()

    private val httpClient: HttpClient get() = clientProvider.client

    private companion object {
        const val RELEASE_WINDOW = 50

        // A lost revision race gets one retry, which re-reads the row another writer advanced.
        const val FORCED_SELECTION_ATTEMPTS = 2
    }

    override suspend fun <R> executeInTransaction(block: suspend () -> R): R =
        database.useWriterConnection { transactor ->
            transactor.immediateTransaction {
                block()
            }
        }

    override fun getAllInstalledApps(): Flow<List<InstalledApp>> =
        installedAppsDao
            .getAllInstalledApps()
            .map { it.map { app -> app.toDomain() } }

    override fun getAppsWithUpdates(): Flow<List<InstalledApp>> =
        installedAppsDao
            .getAppsWithUpdates()
            .map { it.map { app -> app.toDomain() } }

    override fun getUpdateCount(): Flow<Int> = installedAppsDao.getUpdateCount()

    override suspend fun getAppByPackage(packageName: String): InstalledApp? =
        installedAppsDao
            .getAppByPackage(packageName)
            ?.toDomain()

    override suspend fun getAppByRepoId(repoId: Long): InstalledApp? = installedAppsDao.getAppByRepoId(repoId)?.toDomain()

    override fun getAppByRepoIdAsFlow(repoId: Long): Flow<InstalledApp?> =
        installedAppsDao.getAppByRepoIdAsFlow(repoId).map { it?.toDomain() }

    override suspend fun getAppsByRepoId(repoId: Long): List<InstalledApp> = installedAppsDao.getAppsByRepoId(repoId).map { it.toDomain() }

    override fun getAppsByRepoIdAsFlow(repoId: Long): Flow<List<InstalledApp>> =
        installedAppsDao.getAppsByRepoIdAsFlow(repoId).map { list -> list.map { it.toDomain() } }

    override suspend fun isAppInstalled(repoId: Long): Boolean = installedAppsDao.getAppByRepoId(repoId) != null

    override suspend fun saveInstalledApp(app: InstalledApp) {
        installedAppsDao.insertApp(app.toEntity())
    }

    override suspend fun deleteInstalledApp(packageName: String) {
        installedAppsDao.deleteByPackageName(packageName)
    }

    private suspend fun fetchReleaseWindow(
        owner: String,
        repo: String,
        includePreReleases: Boolean,
        sourceHost: String? = null,
    ): List<GithubRelease> {
        if (sourceHost != null) {
            return fetchForgejoReleaseWindow(sourceHost, owner, repo, includePreReleases)
        }
        val backendResult = backendApiClient.getReleases(owner, repo, perPage = RELEASE_WINDOW)
        val backendReleases =
            backendResult.fold(
                onSuccess = { it },
                onFailure = { error ->
                    if (!zed.rainxch.core.data.network
                            .shouldFallbackToGithubOrRethrow(error)
                    ) {
                        return emptyList()
                    }
                    null
                },
            )
        if (backendReleases != null) {
            return backendReleases.toReleaseWindow(includePreReleases)
        }

        return fetchGithubReleaseWindow(httpClient, owner, repo, includePreReleases, RELEASE_WINDOW)
    }

    override suspend fun checkForUpdates(packageName: String): Boolean =
        checkEnabledApp(packageName, recordUnmatched = true) { app ->
            fetchReleaseWindow(
                owner = app.repoOwner,
                repo = app.repoName,
                includePreReleases = app.includePreReleases,
                sourceHost = app.sourceHost,
            )
        }

    override suspend fun checkForUpdatesWithReleases(
        packageName: String,
        releases: List<GithubRelease>,
    ): Boolean =
        checkEnabledApp(packageName, recordUnmatched = false) { app ->
            releases.toUpdateCheckWindow(includePreReleases = app.includePreReleases, limit = RELEASE_WINDOW)
        }

    override suspend fun recordRefreshedRelease(
        packageName: String,
        release: GithubRelease,
        asset: GithubAsset,
    ): Boolean = recordForcedSelection(packageName, ForcedSelection(release, asset))

    // The pair a forced refresh names, used as given instead of resolved from a window.
    private data class ForcedSelection(
        val release: GithubRelease,
        val asset: GithubAsset,
    )

    // The Details page names the pair it shows, so neither the window lookup nor its filter runs.
    // A lost revision race gets one retry against the re-read row; whatever the row holds at the
    // end is the answer the caller gets.
    private suspend fun recordForcedSelection(
        packageName: String,
        forced: ForcedSelection,
    ): Boolean {
        var reflected = false
        var attempt = 0
        while (!reflected && attempt < FORCED_SELECTION_ATTEMPTS) {
            attempt += 1
            val app = loadCheckableApp(packageName) ?: break
            checkAgainstWindow(
                app = app,
                releases = listOf(forced.release),
                recordUnmatched = false,
                forcedSelection = forced,
            )
            val stored = installedAppsDao.getAppByPackage(packageName)
            reflected =
                stored != null &&
                stored.latestReleaseId == forced.release.id &&
                stored.latestAssetId == forced.asset.id
        }
        return reflected
    }

    // The row every check operates on: an absent or disabled row is not checked, and a parked
    // install the platform has since completed is settled before judging.
    private suspend fun loadCheckableApp(packageName: String): InstalledAppEntity? {
        val stored = installedAppsDao.getAppByPackage(packageName) ?: return null
        return if (stored.updateCheckEnabled) parkedInstallSettler.settleIfInstalled(stored) else null
    }

    private suspend fun checkEnabledApp(
        packageName: String,
        recordUnmatched: Boolean,
        window: suspend (InstalledAppEntity) -> List<GithubRelease>,
    ): Boolean {
        val app = loadCheckableApp(packageName) ?: return false
        val releases =
            try {
                window(app)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.e { "Failed to check updates for $packageName: ${e.message}" }
                installedAppsDao.updateLastChecked(packageName, System.currentTimeMillis())
                return false
            }
        return checkAgainstWindow(app, releases, recordUnmatched)
    }

    private suspend fun checkAgainstWindow(
        app: InstalledAppEntity,
        releases: List<GithubRelease>,
        recordUnmatched: Boolean,
        forcedSelection: ForcedSelection? = null,
    ): Boolean {
        val packageName = app.packageName

        try {
            // An empty window is a failed fetch (backend error, rate limit, network), not
            // proof the repo lost its releases. Keep what the last good check found and
            // leave lastCheckedAt alone so the next check retries.
            if (releases.isEmpty() && forcedSelection == null) {
                Logger.d { "No releases for ${app.appName} this time; keeping its stored update state" }
                return app.isUpdateAvailable
            }

            val resolved =
                if (forcedSelection != null) {
                    ResolvedRelease(
                        release = forcedSelection.release,
                        primaryAsset = forcedSelection.asset,
                        variantWasLost = false,
                    )
                } else {
                    resolveTrackedReleaseFor(installedAppsDao, installer, app, releases)
                }

            if (resolved == null) {
                Logger.d {
                    "No matching release found for ${app.appName} in window of ${releases.size}; " +
                        "filter=${app.assetFilterRegex}, fallback=${app.fallbackToOlderReleases}"
                }
                if (!recordUnmatched) return app.isUpdateAvailable
                installedAppsDao.recordUnmatchedRelease(packageName)
                return false
            }

            val (matchedRelease, primaryAsset, variantWasLost) = resolved

            // A generic window can be an older cached copy than the one a forced refresh already
            // persisted. Keep the fresher stored release instead of walking the card back. A
            // forced pair has no window to be older than: the page that named it just read it.
            if (forcedSelection == null && windowWouldRegressStoredRelease(app, matchedRelease)) {
                return app.isUpdateAvailable
            }

            val bound =
                if (forcedSelection != null) {
                    storedInstalledBinding(app)
                } else {
                    resolveInstalledBinding(
                        app = app,
                        releases = releases,
                        matchedRelease = matchedRelease,
                        matchedAsset = primaryAsset,
                        dao = installedAppsDao,
                        packageMonitor = packageMonitor,
                        verifier = digestVerifier,
                    )
                }

            // A rolling tag reuses one tag string across builds. Without a binding, only the
            // installed file can tell whether the release is the build the device runs.
            val tagNeedsSelfAttestation =
                VersionMath.isTimestampTrackedTag(matchedRelease.tagName) ||
                    VersionMath.detectScheme(matchedRelease.tagName) == VersionMath.Scheme.Unknown
            val tagRunsRelease =
                VersionMath.isExactSameVersion(matchedRelease.tagName, app.installedVersion)
            val fileProofApplies =
                bound == null && primaryAsset.digest != null && tagRunsRelease && tagNeedsSelfAttestation
            // A refresh may not read the installed file, so it cannot attest the build it runs.
            val canReadInstalledFile = forcedSelection == null
            val selfAttestationApplies = canReadInstalledFile && fileProofApplies
            val selfAttestedDigest =
                if (selfAttestationApplies) {
                    packageMonitor
                        .getInstalledPackageInfo(packageName)
                        ?.apkPath
                        ?.let { apkPath -> digestVerifier.computeSha256(apkPath) }
                } else {
                    null
                }
            if (selfAttestedDigest != null && selfAttestedDigest == primaryAsset.digest) {
                rebindProvenIdentity(
                    dao = installedAppsDao,
                    app = app,
                    matchedRelease = matchedRelease,
                    matchedAsset = primaryAsset,
                    matchedDigest = selfAttestedDigest,
                    boundAt = System.currentTimeMillis(),
                )
            }

            // A refresh may not read the installed file, so an unbound same-tag release has no
            // proof its bytes differ from the build the device runs.
            val verdict =
                decideUpdateVerdict(
                    app = app,
                    matchedRelease = matchedRelease,
                    matchedAsset = primaryAsset,
                    bound = bound,
                    selfAttestedDigest = selfAttestedDigest,
                    canReadInstalledFile = canReadInstalledFile,
                )

            val isUpdateAvailable = verdict.isUpdateAvailable

            Logger.d {
                "[UPDATE-CHECK] ${app.appName} $packageName " +
                    "installedTag=${app.installedVersion} matchedTag=${matchedRelease.tagName} " +
                    "storedPublishedAt=${app.latestReleasePublishedAt} " +
                    "matchedPublishedAt=${matchedRelease.publishedAt} " +
                    "bound=${bound != null} " +
                    "attested=${selfAttestedDigest ?: "-"} " +
                    "isUpdate=$isUpdateAvailable"
            }

            val written =
                writeCheckedRelease(
                    dao = installedAppsDao,
                    app = app,
                    matchedRelease = matchedRelease,
                    matchedAsset = primaryAsset,
                    verdict = verdict,
                    variantWasLost = variantWasLost.takeIf { forcedSelection == null },
                )
            // Another check won the revision. The row is the only truth now: report it instead of
            // the result this check failed to persist.
            return written
                ?: installedAppsDao.getAppByPackage(packageName)?.isUpdateAvailable
                ?: app.isUpdateAvailable
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e { "Failed to check updates for $packageName: ${e.message}" }
            installedAppsDao.updateLastChecked(packageName, System.currentTimeMillis())
        }

        return false
    }

    override suspend fun resolveTrackedRelease(packageName: String): ResolvedRelease? {
        val app = installedAppsDao.getAppByPackage(packageName) ?: return null

        // The dead URL came from the backend's stale release copy, so the fallback must read the
        // host itself instead of asking the same backend again.
        val releases =
            if (app.sourceHost != null) {
                fetchForgejoReleaseWindow(app.sourceHost, app.repoOwner, app.repoName, app.includePreReleases)
            } else {
                fetchGithubReleaseWindow(httpClient, app.repoOwner, app.repoName, app.includePreReleases, RELEASE_WINDOW)
            }

        if (releases.isEmpty()) return null

        return resolveTrackedReleaseFor(installedAppsDao, installer, app, releases)
    }

    override suspend fun checkAllForUpdates() {
        // App start, Library and the background worker can ask at once; a run already in
        // flight answers all of them.
        if (checkAllMutex.isLocked) {
            checkAllMutex.withLock { }
            return
        }
        checkAllMutex.withLock {
            val apps = installedAppsDao.getAllInstalledApps().first()
            apps.forEach { app ->
                if (app.updateCheckEnabled) {
                    try {
                        checkForUpdates(app.packageName)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.w { "Failed to check updates for ${app.packageName}: ${e.message}" }
                    }
                }
            }
        }
    }

    override suspend fun updateAppVersion(
        packageName: String,
        newTag: String,
        newReleaseId: Long?,
        newAssetId: Long?,
        newAssetDigest: String?,
        newReleasePublishedAt: String?,
        newAssetName: String?,
        newAssetUrl: String?,
        newVersionName: String,
        newVersionCode: Long,
        signingFingerprint: String?,
        isPendingInstall: Boolean,
    ) {
        val app = installedAppsDao.getAppByPackage(packageName) ?: return

        Logger.d {
            "Updating app version: $packageName from ${app.installedVersion} to $newTag"
        }

        historyDao.insertHistory(
            UpdateHistoryEntity(
                packageName = packageName,
                appName = app.appName,
                repoOwner = app.repoOwner,
                repoName = app.repoName,
                fromVersion = app.installedVersion,
                toVersion = newTag,
                updatedAt = System.currentTimeMillis(),
                updateSource = InstallSource.THIS_APP,
                success = true,
            ),
        )

        installedAppsDao.updateApp(
            app
                .toDomain()
                .confirmInstall(
                    tag = newTag,
                    releaseId = newReleaseId,
                    assetId = newAssetId,
                    assetDigest = newAssetDigest,
                    releasePublishedAt = newReleasePublishedAt,
                    assetName = newAssetName,
                    assetUrl = newAssetUrl,
                    versionName = newVersionName,
                    versionCode = newVersionCode,
                    signingFingerprint = signingFingerprint,
                    isPending = isPendingInstall,
                    at = System.currentTimeMillis(),
                ).toEntity(),
        )
    }

    override suspend fun clearInstallBinding(packageName: String) {
        installedAppsDao.clearInstallBinding(packageName)
    }

    override suspend fun updateApp(app: InstalledApp) {
        installedAppsDao.updateApp(app.toEntity())
    }

    override suspend fun updateInstalledVersion(
        packageName: String,
        installedVersion: String,
        installedVersionName: String?,
        installedVersionCode: Long,
        isUpdateAvailable: Boolean,
    ) {
        installedAppsDao.updateInstalledVersion(
            packageName = packageName,
            installedVersion = installedVersion,
            installedVersionName = installedVersionName,
            installedVersionCode = installedVersionCode,
            isUpdateAvailable = isUpdateAvailable,
        )
    }

    override suspend fun updateSigningFingerprint(
        packageName: String,
        signingFingerprint: String,
    ) {
        installedAppsDao.updateSigningFingerprint(packageName, signingFingerprint)
    }

    override suspend fun updatePendingStatus(
        packageName: String,
        isPending: Boolean,
    ) {
        val app = installedAppsDao.getAppByPackage(packageName) ?: return
        installedAppsDao.updateApp(
            app
                .toDomain()
                .let {
                    if (isPending) {
                        it.markPending(
                            releaseId = app.latestReleaseId,
                            assetId = app.latestAssetId,
                            assetDigest = app.latestAssetDigest,
                            releasePublishedAt = app.latestReleasePublishedAt,
                            // The caller marks a download it is about to hand to the
                            // installer, so this is the moment the 24h window starts.
                            startedAt = System.currentTimeMillis(),
                        )
                    } else {
                        it.clearPending()
                    }
                }.toEntity(),
        )
    }

    override suspend fun setIncludePreReleases(
        packageName: String,
        enabled: Boolean,
    ) {
        installedAppsDao.updateIncludePreReleases(packageName, enabled)
    }

    override suspend fun setUpdateCheckEnabled(
        packageName: String,
        enabled: Boolean,
    ) {
        installedAppsDao.updateUpdateCheckEnabled(packageName, enabled)
        if (enabled) {
            try {
                checkForUpdates(packageName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w {
                    "Failed to re-check after enabling update check for $packageName: ${e.message}"
                }
            }
        } else {
            installedAppsDao.clearUpdateMetadata(packageName, System.currentTimeMillis())
        }
    }

    override suspend fun setAssetFilter(
        packageName: String,
        regex: String?,
        fallbackToOlderReleases: Boolean,
    ) {
        val normalized = regex?.trim()?.takeIf { it.isNotEmpty() }
        installedAppsDao.updateAssetFilter(
            packageName = packageName,
            regex = normalized,
            fallback = fallbackToOlderReleases,
        )

        try {
            checkForUpdates(packageName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w {
                "Saved new asset filter for $packageName but immediate " +
                    "re-check failed: ${e.message}"
            }
        }
    }

    // The advanced-settings save writes this flag before the filter, and the filter's own
    // re-check then judges the row with the new setting, so no second fetch runs here.
    override suspend fun setDetectAssetRebuildsInRelease(
        packageName: String,
        enabled: Boolean,
    ) {
        installedAppsDao.updateDetectAssetRebuildsInRelease(packageName, enabled)
    }

    override suspend fun setPreferredVariant(
        packageName: String,
        variant: String?,
        tokens: String?,
        glob: String?,
        pickedIndex: Int?,
        siblingCount: Int?,
    ) {
        val normalizedVariant = variant?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedTokens = tokens?.trim()?.takeIf { it.isNotEmpty() }
        val normalizedGlob = glob?.trim()?.takeIf { it.isNotEmpty() }
        installedAppsDao.updatePreferredVariant(
            packageName = packageName,
            variant = normalizedVariant,
            tokens = normalizedTokens,
            glob = normalizedGlob,
            pickedIndex = pickedIndex,
            siblingCount = siblingCount?.takeIf { it > 0 },
        )

        try {
            checkForUpdates(packageName)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w {
                "Saved new variant for $packageName but immediate " +
                    "re-check failed: ${e.message}"
            }
        }
    }

    override suspend fun clearPreferredVariant(packageName: String) {
        setPreferredVariant(
            packageName = packageName,
            variant = null,
            tokens = null,
            glob = null,
            pickedIndex = null,
            siblingCount = null,
        )
    }

    override suspend fun setSkippedReleaseTag(
        packageName: String,
        tag: String?,
    ) {
        installedAppsDao.setSkippedReleaseTag(packageName, tag?.takeIf { it.isNotBlank() })
    }

    override fun getAppsWithSkippedReleaseTag(): Flow<List<InstalledApp>> =
        installedAppsDao
            .getAppsWithSkippedReleaseTag()
            .map { it.map { entity -> entity.toDomain() } }

    override suspend fun setPendingInstallFilePath(
        packageName: String,
        path: String?,
        version: String?,
        assetName: String?,
    ) {
        installedAppsDao.updatePendingInstallFilePath(
            packageName = packageName,
            path = path,
            version = version,
            assetName = assetName,
        )
    }

    override suspend fun markAwaitingInstall(
        packageName: String,
        path: String,
        version: String?,
        assetName: String?,
        releaseId: Long?,
        assetId: Long?,
        releasePublishedAt: String?,
        assetDigest: String?,
    ) {
        installedAppsDao.markAwaitingInstall(
            packageName = packageName,
            path = path,
            version = version,
            assetName = assetName,
            releaseId = releaseId,
            assetId = assetId,
            releasePublishedAt = releasePublishedAt,
            digest = assetDigest,
        )
    }

    override suspend fun discardParkedInstall(
        packageName: String,
        expectedPath: String?,
    ): ParkedInstallDisposal {
        if (expectedPath != null) {
            if (tryDeleteParkedFile(expectedPath) == ParkedFileDisposal.Survived) {
                Logger.w { "Parked file for $packageName survived the discard; pointer kept" }
                return ParkedInstallDisposal.Retained
            }
        }
        installedAppsDao.clearPendingInstallIfPathMatches(
            packageName = packageName,
            path = expectedPath,
        )
        val remainingPath = installedAppsDao.getAppByPackage(packageName)?.pendingInstallFilePath
        return if (remainingPath != null) {
            Logger.d { "Parked install for $packageName still names $remainingPath; row kept" }
            ParkedInstallDisposal.Retained
        } else {
            Logger.d { "Discarded parked install for $packageName (path=${expectedPath ?: "none"})" }
            ParkedInstallDisposal.Discarded
        }
    }

    override suspend fun previewMatchingAssets(
        owner: String,
        repo: String,
        regex: String?,
        includePreReleases: Boolean,
        fallbackToOlderReleases: Boolean,
    ): MatchingPreview {
        val parseResult = AssetFilter.parse(regex)
        if (parseResult != null && parseResult.isFailure) {
            return MatchingPreview(
                release = null,
                matchedAssets = emptyList(),
                regexError = parseResult.exceptionOrNull()?.message,
            )
        }
        val filter = parseResult?.getOrNull()

        val releases = fetchReleaseWindow(owner, repo, includePreReleases)
        if (releases.isEmpty()) {
            return MatchingPreview(release = null, matchedAssets = emptyList())
        }

        val candidates =
            if (filter != null && !fallbackToOlderReleases) {
                releases.take(1)
            } else {
                releases
            }

        for (release in candidates) {
            val installableForPlatform =
                release.assets.filter { installer.isAssetInstallable(it.name) }
            val matched =
                if (filter == null) {
                    installableForPlatform
                } else {
                    installableForPlatform.filter { filter.matches(it.name) }
                }
            if (matched.isNotEmpty()) {
                return MatchingPreview(release = release, matchedAssets = matched)
            }
        }

        return MatchingPreview(
            release = releases.firstOrNull(),
            matchedAssets = emptyList(),
        )
    }

    private suspend fun fetchForgejoReleaseWindow(
        host: String,
        owner: String,
        repo: String,
        includePreReleases: Boolean,
    ): List<GithubRelease> {
        val client = forgejoClientRegistry.clientFor(host)
        return try {
            val releases =
                client.getReleases(owner, repo, perPage = RELEASE_WINDOW).getOrNull()
                    ?: return emptyList()
            releases.toReleaseWindow(includePreReleases)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e { "Forgejo fetch failed for $host/$owner/$repo: ${e.message}" }
            emptyList()
        }
    }
}

internal enum class ParkedFileDisposal {
    Gone,

    Survived,
}

internal fun tryDeleteParkedFile(path: String): ParkedFileDisposal {
    val file = java.io.File(path)
    val deleted = runCatching { file.delete() }.getOrDefault(false)
    if (deleted) return ParkedFileDisposal.Gone
    return if (file.exists()) ParkedFileDisposal.Survived else ParkedFileDisposal.Gone
}
