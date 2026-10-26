package zed.rainxch.core.data.services

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import zed.rainxch.core.data.download.catchingStepFailures
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.network.Downloader
import zed.rainxch.core.domain.repository.InstalledAppsRepository
import zed.rainxch.core.domain.system.DownloadOrchestrator
import zed.rainxch.core.domain.system.DownloadSpec
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.InstallPolicy
import zed.rainxch.core.domain.system.OrchestratedDownload
import zed.rainxch.core.domain.system.PendingInstallNotifier
import zed.rainxch.core.domain.utils.AssetFileName
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

class DefaultDownloadOrchestrator(
    private val downloader: Downloader,
    private val assetFetcher: AssetFetcher,
    private val installRunner: DownloadInstallRunner,
    private val installedAppsRepository: InstalledAppsRepository,
    private val pendingInstallNotifier: PendingInstallNotifier,
    private val appScope: CoroutineScope,
) : DownloadOrchestrator {
    private companion object {
        private const val DEFAULT_MAX_CONCURRENT = 3
        private const val QUEUE_POLL_DELAY_MS = 200L
        private const val ID_RADIX = 36
    }

    private val entries = DownloadEntries()
    override val downloads: StateFlow<Map<String, OrchestratedDownload>> = entries.downloads

    private val activeJobs = mutableMapOf<String, Job>()

    private var orphanSweepStarted = false

    override fun observe(packageName: String): Flow<OrchestratedDownload?> =
        downloads
            .map { it[packageName] }
            .distinctUntilChanged()

    override suspend fun enqueue(spec: DownloadSpec): String {
        val id = generateId()
        entries.lock.withLock {
            val existing = entries[spec.packageName]
            if (existing != null && existing.isInFlight()) {
                if (existing.installPolicy != spec.installPolicy &&
                    existing.installPolicy.priority < spec.installPolicy.priority
                ) {
                    entries.mutate { state ->
                        state + (spec.packageName to existing.copy(installPolicy = spec.installPolicy))
                    }
                }
                return existing.id
            }
            entries.mutate { it + (spec.packageName to initialEntry(id, spec)) }
        }

        maybeReclaimOrphanedPartials()

        val job = launchDownload(spec)
        entries.lock.withLock {
            activeJobs[spec.packageName] = job
        }
        return id
    }

    private fun initialEntry(
        id: String,
        spec: DownloadSpec,
    ): OrchestratedDownload =
        OrchestratedDownload(
            id = id,
            packageName = spec.packageName,
            repoOwner = spec.repoOwner,
            repoName = spec.repoName,
            repoId = spec.repoId,
            sourceHost = spec.sourceHost,
            repoOwnerAvatarUrl = spec.repoOwnerAvatarUrl,
            repoDescription = spec.repoDescription,
            displayAppName = spec.displayAppName,
            assetName = spec.asset.name,
            assetSize = spec.asset.size,
            assetId = spec.asset.id,
            assetDigest = spec.asset.digest,
            downloadUrl = spec.asset.downloadUrl,
            releaseTag = spec.releaseTag,
            releaseId = spec.releaseId,
            filePath = null,
            installPolicy = spec.installPolicy,
            stage = DownloadStage.Queued,
            progressPercent = null,
        )

    private fun launchDownload(spec: DownloadSpec): Job =
        appScope.launch {
            try {
                catchingStepFailures { runDownload(spec) }.onFailure { failure ->
                    Logger.e(failure) { "Orchestrator: download/install failed for ${spec.packageName}" }
                    entries.markFailed(spec.packageName, failure.message)
                }
            } catch (e: CancellationException) {
                Logger.d { "Orchestrator: download cancelled for ${spec.packageName}" }
                throw e
            } finally {
                entries.lock.withLock {
                    if (activeJobs[spec.packageName]?.isCompleted == true ||
                        activeJobs[spec.packageName] == null
                    ) {
                        activeJobs.remove(spec.packageName)
                    }
                }
            }
        }

    private suspend fun maybeReclaimOrphanedPartials() {
        val shouldSweep =
            entries.lock.withLock {
                if (orphanSweepStarted) {
                    false
                } else {
                    orphanSweepStarted = true
                    true
                }
            }
        if (!shouldSweep) return

        val claimed =
            downloads.value.values
                .map { AssetFileName.scoped(it.repoOwner, it.repoName, it.assetName) }
                .toSet()
        appScope.launch {
            catchingStepFailures {
                val removed = downloader.reclaimOrphanedPartials(claimed)
                if (removed > 0) {
                    Logger.d { "Orchestrator: reclaimed $removed orphaned partial file(s)" }
                }
            }.onFailure { Logger.w(it) { "Orchestrator: orphan partial reclaim failed" } }
        }
    }

    private fun OrchestratedDownload.isInFlight(): Boolean =
        when (stage) {
            DownloadStage.Failed,
            DownloadStage.Cancelled,
            DownloadStage.Paused,
            DownloadStage.Completed,
            -> false

            DownloadStage.Queued,
            DownloadStage.Downloading,
            DownloadStage.Installing,
            DownloadStage.AwaitingInstall,
            -> !lostItsFile()
        }

    private fun OrchestratedDownload.lostItsFile(): Boolean =
        (stage == DownloadStage.AwaitingInstall || stage == DownloadStage.Installing) &&
            filePath?.let { !java.io.File(it).exists() } == true

    private suspend fun runDownload(spec: DownloadSpec) {
        awaitDownloadSlot()
        val fetched = assetFetcher.fetch(spec, entries) ?: return

        val effectivePolicy =
            entries.lock.withLock {
                entries[spec.packageName]?.installPolicy ?: spec.installPolicy
            }

        when (effectivePolicy) {
            InstallPolicy.AlwaysInstall -> {
                installRunner.install(fetched.spec, fetched.filePath, entries)
            }

            InstallPolicy.InstallWhileForeground -> {
                installRunner.park(fetched.spec, fetched.filePath, notify = false, entries = entries)
            }

            InstallPolicy.DeferUntilUserAction -> {
                installRunner.park(fetched.spec, fetched.filePath, notify = true, entries = entries)
            }
        }
    }

    private suspend fun awaitDownloadSlot() {
        while (true) {
            val activeNow =
                entries.lock.withLock {
                    downloads.value.values.count { it.stage == DownloadStage.Downloading }
                }
            if (activeNow < DEFAULT_MAX_CONCURRENT) break
            delay(QUEUE_POLL_DELAY_MS)
        }
    }

    override suspend fun downgradeToDeferred(packageName: String) {
        val notifyFor =
            entries.lock.withLock {
                val existing = entries[packageName] ?: return
                if (!existing.stage.acceptsDeferral()) return
                entries.mutate { state ->
                    state + (packageName to existing.copy(installPolicy = InstallPolicy.DeferUntilUserAction))
                }
                existing.takeIf {
                    it.stage == DownloadStage.AwaitingInstall &&
                        it.installPolicy == InstallPolicy.InstallWhileForeground
                }
            }
        if (notifyFor != null) {
            pendingInstallNotifier.notifyPending(
                packageName = notifyFor.packageName,
                repoOwner = notifyFor.repoOwner,
                repoName = notifyFor.repoName,
                appName = notifyFor.displayAppName,
                versionTag = notifyFor.releaseTag,
            )
        }
    }

    private fun DownloadStage.acceptsDeferral(): Boolean =
        when (this) {
            DownloadStage.Queued,
            DownloadStage.Downloading,
            DownloadStage.Paused,
            DownloadStage.AwaitingInstall,
            -> true

            DownloadStage.Installing,
            DownloadStage.Completed,
            DownloadStage.Cancelled,
            DownloadStage.Failed,
            -> false
        }

    override suspend fun cancel(packageName: String) {
        val job = entries.lock.withLock { activeJobs.remove(packageName) }
        job?.cancel()

        entries[packageName]?.let { stopTransfer(it) }

        // The network call is only cut by stopTransfer's cancelDownload, so wait for the job after it, not before.
        job?.join()

        entries.lock.withLock {
            entries.mutate { state ->
                val current = state[packageName] ?: return@mutate state
                when (current.stage) {
                    DownloadStage.Queued, DownloadStage.Downloading, DownloadStage.Paused -> {
                        state + (packageName to current.copy(stage = DownloadStage.Paused))
                    }

                    else -> {
                        state - packageName
                    }
                }
            }
        }
    }

    private suspend fun stopTransfer(entry: OrchestratedDownload) {
        val scopedName =
            AssetFileName.scoped(entry.repoOwner, entry.repoName, entry.assetName)
        catchingStepFailures { downloader.cancelDownload(scopedName) }
            .onFailure { Logger.w(it) { "Orchestrator: cancelDownload failed for $scopedName" } }

        if (entry.stage == DownloadStage.AwaitingInstall) {
            catchingStepFailures { installedAppsRepository.setPendingInstallFilePath(entry.packageName, null) }
                .onFailure { Logger.w(it) { "Orchestrator: failed to clear pending path on cancel" } }
            catchingStepFailures { pendingInstallNotifier.clearPending(entry.packageName) }
                .onFailure { Logger.w(it) { "Orchestrator: failed to clear notification on cancel" } }
        }
    }

    override suspend fun resume(packageName: String) {
        val entry = entries[packageName] ?: return
        if (entry.stage != DownloadStage.Paused) return
        restart(entry)
    }

    override suspend fun retry(packageName: String) {
        val entry = entries[packageName] ?: return
        if (entry.stage != DownloadStage.Failed) return
        restart(entry)
    }

    private suspend fun restart(entry: OrchestratedDownload) {
        val spec =
            DownloadSpec(
                packageName = entry.packageName,
                repoOwner = entry.repoOwner,
                repoName = entry.repoName,
                repoId = entry.repoId,
                sourceHost = entry.sourceHost,
                repoOwnerAvatarUrl = entry.repoOwnerAvatarUrl,
                repoDescription = entry.repoDescription,
                asset =
                    GithubAsset(
                        id = entry.assetId,
                        name = entry.assetName,
                        contentType = "",
                        size = entry.assetSize,
                        downloadUrl = entry.downloadUrl,
                        digest = entry.assetDigest,
                    ),
                displayAppName = entry.displayAppName,
                installPolicy = entry.installPolicy,
                releaseTag = entry.releaseTag,
                releaseId = entry.releaseId,
            )
        enqueue(spec)
    }

    override suspend fun discard(packageName: String) {
        val entry = entries[packageName]
        cancel(packageName)
        entries.lock.withLock {
            entries.mutate { it - packageName }
        }
        if (entry != null) discardFiles(entry)
    }

    private suspend fun discardFiles(entry: OrchestratedDownload) {
        val scopedName =
            AssetFileName.scoped(entry.repoOwner, entry.repoName, entry.assetName)
        catchingStepFailures { downloader.discardPartial(scopedName) }
            .onFailure { Logger.w(it) { "Orchestrator: discardPartial failed for $scopedName" } }
        if (entry.stage != DownloadStage.AwaitingInstall && entry.stage != DownloadStage.Failed) return
        val path = entry.filePath ?: return
        java.io
            .File(path)
            .takeIf { it.exists() }
            ?.delete()
    }

    override suspend fun installPending(packageName: String): InstallOutcome? {
        val entry = entries[packageName] ?: return installParked(packageName)
        val filePath = entry.filePath ?: return null
        val ext = entry.assetName.substringAfterLast('.', "").lowercase()
        return installRunner.installStandalone(packageName, filePath, ext, entry, entries)
    }

    private suspend fun installParked(packageName: String): InstallOutcome? {
        val app =
            catchingStepFailures { installedAppsRepository.getAppByPackage(packageName) }
                .onFailure { Logger.e(it) { "Orchestrator: getAppByPackage failed" } }
                .getOrNull()
        val filePath = app?.pendingInstallFilePath ?: return null
        val ext = filePath.substringAfterLast('.', "").lowercase()
        return installRunner.installStandalone(packageName, filePath, ext, entry = null, entries = entries)
    }

    override fun dismiss(packageName: String) {
        entries.mutate { it - packageName }
    }

    private fun generateId(): String = Random.nextLong().toULong().toString(ID_RADIX) + Random.nextLong().toULong().toString(ID_RADIX)
}

private val InstallPolicy.priority: Int
    get() =
        when (this) {
            InstallPolicy.DeferUntilUserAction -> 0
            InstallPolicy.InstallWhileForeground -> 1
            InstallPolicy.AlwaysInstall -> 2
        }
