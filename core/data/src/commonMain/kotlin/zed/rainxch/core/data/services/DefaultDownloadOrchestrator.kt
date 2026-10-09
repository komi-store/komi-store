package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import zed.rainxch.core.data.data_source.TokenStore
import zed.rainxch.core.data.network.GithubAssetAuth
import zed.rainxch.core.domain.model.installation.DownloadProgress
import zed.rainxch.core.domain.model.installation.markPending
import zed.rainxch.core.domain.model.installation.withLatestSnapshot
import zed.rainxch.core.domain.network.AssetIdentity
import zed.rainxch.core.domain.network.DigestVerifier
import zed.rainxch.core.domain.network.Downloader
import zed.rainxch.core.domain.network.SlowDownloadDetector
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.DownloadOrchestrator
import zed.rainxch.core.domain.system.DownloadSpec
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.InstallPolicy
import zed.rainxch.core.domain.system.Installer
import zed.rainxch.core.domain.system.MultiSourceDownloader
import zed.rainxch.core.domain.system.OrchestratedDownload
import zed.rainxch.core.domain.system.PendingInstallNotifier
import zed.rainxch.core.domain.system.SystemInstallSerializer
import zed.rainxch.core.domain.utils.AssetFileName
import kotlin.random.Random

class DefaultDownloadOrchestrator(
    private val downloader: Downloader,
    private val multiSourceDownloader: MultiSourceDownloader,
    private val digestVerifier: DigestVerifier,
    private val installer: Installer,
    private val installedAppsRepository: InstalledAppsRepository,
    private val pendingInstallNotifier: PendingInstallNotifier,
    private val slowDownloadDetector: SlowDownloadDetector,
    private val appScope: CoroutineScope,
    private val systemInstallSerializer: SystemInstallSerializer,
    private val tokenStore: TokenStore,
) : DownloadOrchestrator {
    private companion object {
        private const val DEFAULT_MAX_CONCURRENT = 3
        private const val QUEUE_POLL_DELAY_MS = 200L
    }

    private val _downloads = MutableStateFlow<Map<String, OrchestratedDownload>>(emptyMap())
    override val downloads: StateFlow<Map<String, OrchestratedDownload>> = _downloads.asStateFlow()

    private val stateMutex = Mutex()

    private val activeJobs = mutableMapOf<String, Job>()

    private var orphanSweepStarted = false

    override fun observe(packageName: String): Flow<OrchestratedDownload?> =
        _downloads
            .map { it[packageName] }
            .distinctUntilChanged()

    override suspend fun enqueue(spec: DownloadSpec): String {
        stateMutex.withLock {
            val existing = _downloads.value[spec.packageName]
            if (existing != null && existing.stage != DownloadStage.Failed &&
                existing.stage != DownloadStage.Cancelled
            ) {
                if (existing.installPolicy != spec.installPolicy &&
                    existing.installPolicy.priority < spec.installPolicy.priority
                ) {
                    _downloads.update { state ->
                        state + (spec.packageName to existing.copy(installPolicy = spec.installPolicy))
                    }
                }
                return existing.id
            }
        }

        val id = generateId()
        val initial =
            OrchestratedDownload(
                id = id,
                packageName = spec.packageName,
                repoOwner = spec.repoOwner,
                repoName = spec.repoName,
                displayAppName = spec.displayAppName,
                assetName = spec.asset.name,
                assetSize = spec.asset.size,
                downloadUrl = spec.asset.downloadUrl,
                releaseTag = spec.releaseTag,
                filePath = null,
                installPolicy = spec.installPolicy,
                stage = DownloadStage.Queued,
                progressPercent = null,
            )
        stateMutex.withLock {
            _downloads.update { it + (spec.packageName to initial) }
        }

        maybeReclaimOrphanedPartials()

        val job = appScope.launch {
            try {
                runDownload(spec)
            } catch (e: CancellationException) {
                Logger.d { "Orchestrator: download cancelled for ${spec.packageName}" }
                throw e
            } catch (t: Throwable) {
                Logger.e(t) { "Orchestrator: download/install failed for ${spec.packageName}" }
                markFailed(spec.packageName, t.message)
            } finally {
                stateMutex.withLock {
                    if (activeJobs[spec.packageName]?.isCompleted == true ||
                        activeJobs[spec.packageName] == null
                    ) {
                        activeJobs.remove(spec.packageName)
                    }
                }
            }
        }
        stateMutex.withLock {
            activeJobs[spec.packageName] = job
        }
        return id
    }

    private suspend fun maybeReclaimOrphanedPartials() {
        val shouldSweep =
            stateMutex.withLock {
                if (orphanSweepStarted) {
                    false
                } else {
                    orphanSweepStarted = true
                    true
                }
            }
        if (!shouldSweep) return

        val claimed =
            _downloads.value.values
                .map { AssetFileName.scoped(it.repoOwner, it.repoName, it.assetName) }
                .toSet()
        appScope.launch {
            try {
                val removed = downloader.reclaimOrphanedPartials(claimed)
                if (removed > 0) {
                    Logger.d { "Orchestrator: reclaimed $removed orphaned partial file(s)" }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "Orchestrator: orphan partial reclaim failed" }
            }
        }
    }

    private suspend fun runDownload(spec: DownloadSpec) {
        while (true) {
            val activeNow =
                stateMutex.withLock {
                    _downloads.value.values.count { it.stage == DownloadStage.Downloading }
                }
            if (activeNow < DEFAULT_MAX_CONCURRENT) break
            kotlinx.coroutines.delay(QUEUE_POLL_DELAY_MS)
        }

        val scopedName =
            AssetFileName.scoped(
                owner = spec.repoOwner,
                repo = spec.repoName,
                originalName = spec.asset.name,
            )

        updateEntry(spec.packageName) {
            it.copy(
                stage = DownloadStage.Downloading,
                bytesDownloaded = 0L,
                totalBytes = spec.asset.size.takeIf { size -> size > 0 },
            )
        }

        suspend fun streamProgress(flow: Flow<DownloadProgress>) {
            flow.collect { progress ->
                if (progress.restart) {
                    slowDownloadDetector.reset()
                }
                slowDownloadDetector.onProgress(progress)
                updateEntry(spec.packageName) {
                    it.copy(
                        progressPercent = progress.percent,
                        bytesDownloaded = progress.bytesDownloaded,
                        totalBytes = progress.totalBytes ?: it.totalBytes,
                    )
                }
            }
        }

        val identity =
            AssetIdentity(
                assetId = spec.asset.id,
                digest = spec.asset.digest,
                size = spec.asset.size,
            )

        try {
            streamProgress(
                multiSourceDownloader.download(
                    spec.asset.downloadUrl,
                    scopedName,
                    identity = identity,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            val apiUrl = authenticatedGithubAssetUrl(spec)
                ?: throw e
            Logger.w(e) {
                "Orchestrator: primary download failed for ${spec.asset.name}, " +
                    "retrying via authenticated GitHub asset API"
            }
            slowDownloadDetector.reset()
            streamProgress(
                downloader.download(apiUrl, scopedName, bypassMirror = true, identity = identity),
            )
        }

        var filePath =
            downloader.getDownloadedFilePath(scopedName)
                ?: throw IllegalStateException("Downloaded file missing: $scopedName")

        updateEntry(spec.packageName) {
            it.copy(filePath = filePath, progressPercent = 100)
        }

        val expectedDigest = spec.asset.digest
        if (expectedDigest != null) {
            val mismatch = digestVerifier.verify(filePath, expectedDigest)
            if (mismatch != null) {
                Logger.w {
                    "Orchestrator: digest mismatch for ${spec.asset.name} ($mismatch); " +
                        "re-fetching from the source"
                }
                runCatching { java.io.File(filePath).delete() }
                runCatching { downloader.discardPartial(scopedName) }
                updateEntry(spec.packageName) {
                    it.copy(progressPercent = 0, bytesDownloaded = 0L)
                }
                val sourceUrl = authenticatedGithubAssetUrl(spec) ?: spec.asset.downloadUrl
                try {
                    streamProgress(
                        downloader.download(
                            sourceUrl,
                            scopedName,
                            bypassMirror = true,
                            identity = identity,
                        ),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Logger.e(t) { "Orchestrator: source re-fetch failed for ${spec.asset.name}" }
                    markFailed(spec.packageName, t.message)
                    return
                }
                filePath =
                    downloader.getDownloadedFilePath(scopedName)
                        ?: throw IllegalStateException(
                            "Downloaded file missing after re-fetch: $scopedName",
                        )
                val stillMismatched = digestVerifier.verify(filePath, expectedDigest)
                if (stillMismatched != null) {
                    Logger.w {
                        "Orchestrator: digest still mismatched after the source re-fetch for " +
                            "${spec.asset.name}: $stillMismatched"
                    }
                    runCatching { java.io.File(filePath).delete() }
                    markFailed(
                        spec.packageName,
                        "Checksum mismatch — file may have been tampered with",
                    )
                    return
                }
                updateEntry(spec.packageName) {
                    it.copy(filePath = filePath, progressPercent = 100)
                }
            }
        } else {
            Logger.i { "No digest for ${spec.asset.name}, skipping SHA-256 verification" }
        }

        val effectivePolicy =
            stateMutex.withLock {
                _downloads.value[spec.packageName]?.installPolicy ?: spec.installPolicy
            }

        when (effectivePolicy) {
            InstallPolicy.AlwaysInstall -> runInstall(spec, filePath)

            InstallPolicy.InstallWhileForeground -> parkForUser(spec, filePath, notify = false)

            InstallPolicy.DeferUntilUserAction -> parkForUser(spec, filePath, notify = true)
        }
    }

    private suspend fun authenticatedGithubAssetUrl(spec: DownloadSpec): String? {
        if (spec.asset.id <= 0L) return null
        if (!GithubAssetAuth.isGithubHost(spec.asset.downloadUrl)) return null
        val signedIn =
            try {
                tokenStore.currentToken()?.accessToken?.trim().isNullOrEmpty().not()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                false
            }
        if (!signedIn) return null
        return GithubAssetAuth.assetApiUrl(spec.repoOwner, spec.repoName, spec.asset.id)
    }

    private suspend fun runInstall(spec: DownloadSpec, filePath: String) {
        updateEntry(spec.packageName) { it.copy(stage = DownloadStage.Installing) }
        val ext = spec.asset.name.substringAfterLast('.', "").lowercase()
        var delegated = false
        var parkedTarget = false
        try {
            installer.ensurePermissionsOrThrow(ext)
            parkedTarget = parkTargetIdentity(spec, filePath)
            systemInstallSerializer.awaitFreeAndMarkPending(spec.packageName)
            val outcome = installer.install(filePath, ext)
            delegated = outcome == InstallOutcome.DELEGATED_TO_SYSTEM
            when (outcome) {
                InstallOutcome.COMPLETED -> {
                    systemInstallSerializer.markCompleted(spec.packageName)
                    try {
                        installedAppsRepository.setPendingInstallFilePath(spec.packageName, null)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Logger.w(e) { "Orchestrator: failed to clear pending install path" }
                    }
                    pendingInstallNotifier.clearPending(spec.packageName)
                    updateEntry(spec.packageName) {
                        it.copy(stage = DownloadStage.Completed, installOutcome = outcome)
                    }
                }

                InstallOutcome.DELEGATED_TO_SYSTEM -> {
                    Logger.i {
                        "Orchestrator: AlwaysInstall path returned DELEGATED_TO_SYSTEM " +
                            "for ${spec.packageName}; Completed-with-pending so DB row stays pending"
                    }
                    updateEntry(spec.packageName) {
                        it.copy(stage = DownloadStage.Completed, installOutcome = outcome)
                    }
                }
            }
        } catch (e: CancellationException) {
            if (!delegated) systemInstallSerializer.markCompleted(spec.packageName)
            throw e
        } catch (t: Throwable) {
            if (!delegated) systemInstallSerializer.markCompleted(spec.packageName)
            if (parkedTarget) {
                runCatching { installedAppsRepository.updatePendingStatus(spec.packageName, false) }
                    .onFailure { Logger.w(it) { "Orchestrator: failed to clear pending for ${spec.packageName}" } }
            }
            Logger.e(t) { "Orchestrator: install failed for ${spec.packageName}" }
            markFailed(spec.packageName, t.message)
        }
    }

    // Only callers that pass a release hand the install record to the orchestrator; Details writes its own.
    private suspend fun parkTargetIdentity(spec: DownloadSpec, filePath: String): Boolean {
        val releaseId = spec.releaseId ?: return false
        return try {
            val app = installedAppsRepository.getAppByPackage(spec.packageName) ?: return false
            val apkInfo = installer.getApkInfoExtractor().extractPackageInfo(filePath) ?: return false
            if (apkInfo.packageName != spec.packageName) return false
            installedAppsRepository.updateApp(
                app
                    .markPending(
                        releaseId = releaseId,
                        assetId = spec.asset.id,
                        assetDigest = spec.asset.digest,
                    ).withLatestSnapshot(
                        version = spec.releaseTag,
                        assetName = spec.asset.name,
                        assetUrl = spec.asset.downloadUrl,
                        versionName = apkInfo.versionName,
                        versionCode = apkInfo.versionCode,
                    ),
            )
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.w(e) { "Orchestrator: could not park install target for ${spec.packageName}" }
            false
        }
    }

    private suspend fun parkForUser(
        spec: DownloadSpec,
        filePath: String,
        notify: Boolean,
    ) {
        try {
            installedAppsRepository.setPendingInstallFilePath(
                packageName = spec.packageName,
                path = filePath,
                version = spec.releaseTag,
                assetName = spec.asset.name,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e) { "Orchestrator: failed to persist pending install path" }
        }
        if (notify) {
            pendingInstallNotifier.notifyPending(
                packageName = spec.packageName,
                repoOwner = spec.repoOwner,
                repoName = spec.repoName,
                appName = spec.displayAppName,
                versionTag = spec.releaseTag,
            )
        }
        updateEntry(spec.packageName) { it.copy(stage = DownloadStage.AwaitingInstall) }
    }

    override suspend fun downgradeToDeferred(packageName: String) {
        val shouldNotify: Boolean
        val notifySpec: NotifyData?
        stateMutex.withLock {
            val existing = _downloads.value[packageName] ?: return
            when (existing.stage) {
                DownloadStage.Queued, DownloadStage.Downloading -> {
                    _downloads.update { state ->
                        state + (
                            packageName to existing.copy(
                                installPolicy = InstallPolicy.DeferUntilUserAction,
                            )
                        )
                    }
                    shouldNotify = false
                    notifySpec = null
                }

                DownloadStage.AwaitingInstall -> {
                    val needsNotify =
                        existing.installPolicy == InstallPolicy.InstallWhileForeground
                    _downloads.update { state ->
                        state + (
                            packageName to existing.copy(
                                installPolicy = InstallPolicy.DeferUntilUserAction,
                            )
                        )
                    }
                    shouldNotify = needsNotify
                    notifySpec = if (needsNotify) {
                        NotifyData(
                            packageName = existing.packageName,
                            repoOwner = existing.repoOwner,
                            repoName = existing.repoName,
                            appName = existing.displayAppName,
                            versionTag = existing.releaseTag,
                        )
                    } else {
                        null
                    }
                }

                DownloadStage.Installing,
                DownloadStage.Completed,
                DownloadStage.Cancelled,
                DownloadStage.Failed,
                -> {
                    shouldNotify = false
                    notifySpec = null
                }
            }
        }
        if (shouldNotify && notifySpec != null) {
            pendingInstallNotifier.notifyPending(
                packageName = notifySpec.packageName,
                repoOwner = notifySpec.repoOwner,
                repoName = notifySpec.repoName,
                appName = notifySpec.appName,
                versionTag = notifySpec.versionTag,
            )
        }
    }

    private data class NotifyData(
        val packageName: String,
        val repoOwner: String,
        val repoName: String,
        val appName: String,
        val versionTag: String,
    )

    override suspend fun cancel(packageName: String) {
        val job = stateMutex.withLock { activeJobs.remove(packageName) }
        job?.cancel()

        val entry = _downloads.value[packageName]
        if (entry != null) {
            val scopedName =
                AssetFileName.scoped(entry.repoOwner, entry.repoName, entry.assetName)
            try {
                downloader.cancelDownload(scopedName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "Orchestrator: cancelDownload failed for $scopedName" }
            }

            if (entry.stage == DownloadStage.AwaitingInstall) {
                try {
                    installedAppsRepository.setPendingInstallFilePath(packageName, null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.w(e) { "Orchestrator: failed to clear pending path on cancel" }
                }
                try {
                    pendingInstallNotifier.clearPending(packageName)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.w(e) { "Orchestrator: failed to clear notification on cancel" }
                }
            }
        }

        stateMutex.withLock {
            _downloads.update { it - packageName }
        }
    }

    override suspend fun discard(packageName: String) {
        val entry = _downloads.value[packageName]
        cancel(packageName)
        if (entry != null) {
            val scopedName =
                AssetFileName.scoped(entry.repoOwner, entry.repoName, entry.assetName)
            try {
                downloader.discardPartial(scopedName)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logger.w(e) { "Orchestrator: discardPartial failed for $scopedName" }
            }
        }
    }

    override suspend fun installPending(packageName: String): InstallOutcome? {
        val entry = _downloads.value[packageName]
            ?: run {
                val app = try {
                    installedAppsRepository.getAppByPackage(packageName)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.e(e) { "Orchestrator: getAppByPackage failed" }
                    null
                }
                val filePath = app?.pendingInstallFilePath ?: return null
                val ext = filePath.substringAfterLast('.', "").lowercase()
                return runStandaloneInstall(packageName, filePath, ext)
            }

        val filePath = entry.filePath ?: return null
        val ext = entry.assetName.substringAfterLast('.', "").lowercase()
        return runStandaloneInstall(packageName, filePath, ext)
    }

    private suspend fun runStandaloneInstall(
        packageName: String,
        filePath: String,
        ext: String,
    ): InstallOutcome? {
        updateEntry(packageName) { it.copy(stage = DownloadStage.Installing) }
        var delegated = false
        return try {
            installer.ensurePermissionsOrThrow(ext)
            systemInstallSerializer.awaitFreeAndMarkPending(packageName)
            val outcome = installer.install(filePath, ext)
            delegated = outcome == InstallOutcome.DELEGATED_TO_SYSTEM
            if (outcome == InstallOutcome.COMPLETED) {
                systemInstallSerializer.markCompleted(packageName)
                try {
                    installedAppsRepository.setPendingInstallFilePath(packageName, null)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Logger.w(e) { "Orchestrator: failed to clear pending install path post-install" }
                }
                pendingInstallNotifier.clearPending(packageName)
                updateEntry(packageName) { it.copy(stage = DownloadStage.Completed) }
            }
            outcome
        } catch (e: CancellationException) {
            if (!delegated) systemInstallSerializer.markCompleted(packageName)
            throw e
        } catch (t: Throwable) {
            if (!delegated) systemInstallSerializer.markCompleted(packageName)
            Logger.e(t) { "Orchestrator: standalone install failed for $packageName" }
            markFailed(packageName, t.message)
            null
        }
    }

    override fun dismiss(packageName: String) {
        _downloads.update { it - packageName }
    }

    private suspend fun updateEntry(
        packageName: String,
        transform: (OrchestratedDownload) -> OrchestratedDownload,
    ) {
        stateMutex.withLock {
            _downloads.update { state ->
                val current = state[packageName] ?: return@update state
                state + (packageName to transform(current))
            }
        }
    }

    private suspend fun markFailed(packageName: String, message: String?) {
        stateMutex.withLock {
            _downloads.update { state ->
                val current = state[packageName] ?: return@update state
                state + (packageName to current.copy(stage = DownloadStage.Failed, errorMessage = message))
            }
        }
    }

    private fun generateId(): String =
        Random.nextLong().toULong().toString(36) + Random.nextLong().toULong().toString(36)
}

private val InstallPolicy.priority: Int
    get() = when (this) {
        InstallPolicy.DeferUntilUserAction -> 0
        InstallPolicy.InstallWhileForeground -> 1
        InstallPolicy.AlwaysInstall -> 2
    }
