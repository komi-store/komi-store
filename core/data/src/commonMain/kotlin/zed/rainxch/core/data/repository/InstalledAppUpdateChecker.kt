package zed.rainxch.core.data.repository

import androidx.sqlite.SQLiteException
import co.touchlab.kermit.Logger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zed.rainxch.core.data.local.db.dao.InstalledAppDao
import zed.rainxch.core.data.local.db.dao.adoptMatchedTag
import zed.rainxch.core.data.local.db.dao.recordUnmatchedRelease
import zed.rainxch.core.data.local.db.entities.InstalledAppEntity
import zed.rainxch.core.data.local.db.entities.InstalledAppVersionInfo
import zed.rainxch.core.data.mappers.toDomain
import zed.rainxch.core.data.mappers.toUpdateCheckWindow
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRelease
import zed.rainxch.core.domain.model.error.LocalStoreException
import zed.rainxch.core.domain.model.error.RateLimitException
import zed.rainxch.core.domain.model.error.SystemPackagesException
import zed.rainxch.core.domain.model.smartdetect.MatchingPreview
import zed.rainxch.core.domain.network.DigestVerifier
import zed.rainxch.core.domain.repository.installedapps.InstalledAppUpdateChecks
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.PackageMonitor
import zed.rainxch.core.domain.utils.AssetFilter
import zed.rainxch.core.domain.utils.AssetVariant
import zed.rainxch.core.domain.utils.ReleaseTracking
import zed.rainxch.core.domain.utils.ResolvedRelease
import zed.rainxch.core.domain.utils.TrackedReleaseResolver
import zed.rainxch.core.domain.utils.UpdateVerdict
import zed.rainxch.core.domain.utils.VariantPin

// The update checks behind InstalledAppsRepository: fetch a release window, pick the tracked asset, store the verdict.
internal class InstalledAppUpdateChecker(
    private val dao: InstalledAppDao,
    private val installer: Installer,
    private val releaseSource: InstalledReleaseSource,
    packageMonitor: PackageMonitor,
    digestVerifier: DigestVerifier,
) : InstalledAppUpdateChecks {
    private val parkedInstallSettler = ParkedInstallSettler(dao, packageMonitor, digestVerifier)
    private val binder = InstalledReleaseBinder(dao, packageMonitor, digestVerifier)
    private val checkAllMutex = Mutex()

    override suspend fun checkForUpdates(packageName: String): Boolean =
        localStore {
            checkEnabledApp(packageName, recordUnmatched = true) { app ->
                releaseSource.window(app.repoOwner, app.repoName, app.includePreReleases, app.sourceHost)
            }
        }

    override suspend fun checkForUpdatesWithReleases(
        packageName: String,
        releases: List<GithubRelease>,
    ): Boolean =
        localStore {
            checkEnabledApp(packageName, recordUnmatched = false) { app ->
                releases.toUpdateCheckWindow(includePreReleases = app.includePreReleases, limit = RELEASE_WINDOW)
            }
        }

    // The dead URL came from the backend's stale release copy, so this reads the host itself.
    override suspend fun resolveTrackedRelease(packageName: String): ResolvedRelease? =
        localStore {
            dao.getAppByPackage(packageName)?.let { app ->
                val releases = releaseSource.hostWindow(app.repoOwner, app.repoName, app.includePreReleases, app.sourceHost)
                if (releases.isEmpty()) null else resolveTrackedReleaseFor(app, releases)
            }
        }

    override suspend fun checkAllForUpdates() {
        // App start, Library and the background worker can ask at once; a run already in flight answers all of them.
        if (checkAllMutex.isLocked) {
            checkAllMutex.withLock { }
            return
        }
        checkAllMutex.withLock {
            localStore { dao.getAllInstalledApps().first() }
                .filter { it.updateCheckEnabled }
                .forEach { app -> checkOneOfAll(app.packageName) }
        }
    }

    override suspend fun previewMatchingAssets(
        owner: String,
        repo: String,
        regex: String?,
        includePreReleases: Boolean,
        fallbackToOlderReleases: Boolean,
    ): MatchingPreview {
        val parsed = AssetFilter.parse(regex)
        val regexError = parsed?.exceptionOrNull()
        if (regexError != null) {
            return MatchingPreview(release = null, matchedAssets = emptyList(), regexError = regexError.message)
        }
        val filter = parsed?.getOrNull()
        val releases = releaseSource.window(owner, repo, includePreReleases)
        val candidates = if (filter != null && !fallbackToOlderReleases) releases.take(1) else releases
        val firstMatch =
            candidates.firstNotNullOfOrNull { release ->
                release.assets
                    .filter { installer.isAssetInstallable(it.name) && (filter == null || filter.matches(it.name)) }
                    .takeIf { it.isNotEmpty() }
                    ?.let { MatchingPreview(release = release, matchedAssets = it) }
            }
        return firstMatch ?: MatchingPreview(release = releases.firstOrNull(), matchedAssets = emptyList())
    }

    private suspend fun checkOneOfAll(packageName: String) {
        try {
            checkForUpdates(packageName)
        } catch (e: LocalStoreException) {
            Logger.w(e) { "Failed to check updates for $packageName" }
        } catch (e: SystemPackagesException) {
            Logger.w(e) { "Failed to check updates for $packageName" }
        }
    }

    private suspend fun checkEnabledApp(
        packageName: String,
        recordUnmatched: Boolean,
        window: suspend (InstalledAppEntity) -> List<GithubRelease>,
    ): Boolean {
        val stored = dao.getAppByPackage(packageName)?.takeIf { it.updateCheckEnabled } ?: return false
        val app = parkedInstallSettler.settleIfInstalled(stored)
        return try {
            checkAgainstWindow(app, window(app), recordUnmatched)
        } catch (e: RateLimitException) {
            recordFailedCheck(packageName, e)
        }
    }

    private suspend fun checkAgainstWindow(
        app: InstalledAppEntity,
        releases: List<GithubRelease>,
        recordUnmatched: Boolean,
    ): Boolean {
        // An empty window is a failed fetch, not proof the releases are gone, so lastCheckedAt stays for the next retry.
        if (releases.isEmpty()) {
            Logger.d { "No releases for ${app.appName} this time; keeping its stored update state" }
            return app.isUpdateAvailable
        }
        return try {
            val resolved = resolveTrackedReleaseFor(app, releases)
            if (resolved == null) recordNoMatch(app, releases.size, recordUnmatched) else applyMatch(app, releases, resolved)
        } catch (e: SQLiteException) {
            recordFailedCheck(app.packageName, e)
        } catch (e: SystemPackagesException) {
            recordFailedCheck(app.packageName, e)
        }
    }

    private suspend fun recordFailedCheck(
        packageName: String,
        cause: Exception,
    ): Boolean {
        Logger.e(cause) { "Failed to check updates for $packageName" }
        dao.updateLastChecked(packageName, System.currentTimeMillis())
        return false
    }

    private suspend fun recordNoMatch(
        app: InstalledAppEntity,
        windowSize: Int,
        recordUnmatched: Boolean,
    ): Boolean {
        Logger.d {
            "No matching release found for ${app.appName} in window of $windowSize; " +
                "filter=${app.assetFilterRegex}, fallback=${app.fallbackToOlderReleases}"
        }
        if (!recordUnmatched) return app.isUpdateAvailable
        dao.recordUnmatchedRelease(app.packageName)
        return false
    }

    private suspend fun applyMatch(
        app: InstalledAppEntity,
        releases: List<GithubRelease>,
        resolved: ResolvedRelease,
    ): Boolean {
        val (matchedRelease, primaryAsset, variantWasLost) = resolved
        val bound = binder.resolve(app, releases, matchedRelease, primaryAsset)
        val selfAttestedDigest = binder.selfAttestedDigest(app, matchedRelease, primaryAsset, bound)
        val verdict =
            UpdateVerdict.decide(
                installed = app.installedForVerdict(),
                stored = app.storedForVerdict(),
                matched = matchedRelease.matchedForVerdict(primaryAsset),
                skippedTag = app.skippedReleaseTag,
                bound = bound,
                selfAttestedDigest = selfAttestedDigest,
            )
        if (verdict.skipBecameStale) {
            dao.setSkippedReleaseTag(app.packageName, null)
        }
        Logger.d {
            "[UPDATE-CHECK] ${app.appName} ${app.packageName} " +
                "installedTag=${app.installedVersion} matchedTag=${matchedRelease.tagName} " +
                "storedPublishedAt=${app.latestReleasePublishedAt} " +
                "matchedPublishedAt=${matchedRelease.publishedAt} " +
                "bound=${bound != null} " +
                "attested=${selfAttestedDigest ?: "-"} " +
                "isUpdate=${verdict.isUpdateAvailable}"
        }
        storeVerdict(app, matchedRelease, primaryAsset, verdict)
        if (variantWasLost != app.preferredVariantStale) {
            dao.updateVariantStaleness(app.packageName, variantWasLost)
        }
        return verdict.isUpdateAvailable
    }

    private suspend fun storeVerdict(
        app: InstalledAppEntity,
        matchedRelease: GithubRelease,
        primaryAsset: GithubAsset,
        verdict: UpdateVerdict.Result,
    ) {
        dao.updateVersionInfo(
            InstalledAppVersionInfo(
                packageName = app.packageName,
                isUpdateAvailable = verdict.isUpdateAvailable,
                latestVersion = matchedRelease.tagName,
                latestAssetName = primaryAsset.name,
                latestAssetUrl = primaryAsset.downloadUrl,
                latestAssetSize = primaryAsset.size,
                latestReleaseId = matchedRelease.id,
                latestAssetId = primaryAsset.id,
                latestAssetDigest = primaryAsset.digest,
                releaseNotes = matchedRelease.description ?: "",
                lastCheckedAt = System.currentTimeMillis(),
                latestVersionName = matchedRelease.tagName,
                latestVersionCode = if (matchedRelease.tagName == app.latestVersion) app.latestVersionCode else null,
                latestReleasePublishedAt = matchedRelease.publishedAt,
            ),
        )
        val shouldRewriteTag =
            UpdateVerdict.shouldAdoptMatchedTag(
                codesAlreadyMatch = verdict.codesAlreadyMatch,
                installedTag = app.installedVersion,
                matchedTag = matchedRelease.tagName,
                deviceRunsMatchedRelease = verdict.deviceRunsMatchedRelease,
            )
        if (shouldRewriteTag) {
            dao.adoptMatchedTag(app = app, matchedTag = matchedRelease.tagName, isUpdateAvailable = verdict.isUpdateAvailable)
        }
    }

    private suspend fun resolveTrackedReleaseFor(
        app: InstalledAppEntity,
        releases: List<GithubRelease>,
    ): ResolvedRelease? {
        val compiledFilter =
            AssetFilter
                .parse(app.assetFilterRegex)
                ?.onFailure { error ->
                    Logger.w {
                        "Invalid asset filter for ${app.packageName} " +
                            "(${app.assetFilterRegex}): ${error.message}; ignoring"
                    }
                }?.getOrNull()

        return TrackedReleaseResolver.resolve(
            releases = releases,
            tracking =
                ReleaseTracking(
                    packageName = app.packageName,
                    installedAssetName = app.installedAssetName,
                    filter = compiledFilter,
                    fallbackToOlderReleases = app.fallbackToOlderReleases,
                    pin =
                        VariantPin(
                            variant = app.preferredAssetVariant,
                            tokens = AssetVariant.deserializeTokens(app.preferredAssetTokens),
                            glob = app.assetGlobPattern,
                            pickedIndex = app.pickedAssetIndex,
                            pickedSiblingCount = app.pickedAssetSiblingCount,
                        ),
                ),
            repoApps = dao.getAppsByRepoId(app.repoId).map { it.toDomain() },
            installer = installer,
        )
    }
}

private fun InstalledAppEntity.installedForVerdict() =
    UpdateVerdict.Installed(
        tag = installedVersion,
        versionCode = installedVersionCode,
        versionName = installedVersionName,
    )

private fun InstalledAppEntity.storedForVerdict() =
    UpdateVerdict.Stored(
        latestTag = latestVersion,
        latestVersionCode = latestVersionCode,
        publishedAt = latestReleasePublishedAt,
        wasUpdateAvailable = isUpdateAvailable,
        latestReleaseId = latestReleaseId,
        latestAssetId = latestAssetId,
        latestAssetDigest = latestAssetDigest,
        latestAssetSize = latestAssetSize,
    )

private fun GithubRelease.matchedForVerdict(asset: GithubAsset) =
    UpdateVerdict.Matched(
        tag = tagName,
        publishedAt = publishedAt,
        isPrerelease = isPrerelease,
        releaseId = id,
        assetId = asset.id,
        assetDigest = asset.digest,
        assetSize = asset.size,
    )
