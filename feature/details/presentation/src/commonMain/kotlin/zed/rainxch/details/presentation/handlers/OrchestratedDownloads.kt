package zed.rainxch.details.presentation.handlers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import zed.rainxch.core.domain.logging.KomiStoreLogger
import zed.rainxch.core.domain.model.account.github.GithubAsset
import zed.rainxch.core.domain.model.account.github.GithubRepoSummary
import zed.rainxch.core.domain.model.installation.InstallerType
import zed.rainxch.core.domain.model.system.Platform
import zed.rainxch.core.domain.repository.TweaksRepository
import zed.rainxch.core.domain.system.DownloadOrchestrator
import zed.rainxch.core.domain.system.DownloadSpec
import zed.rainxch.core.domain.system.DownloadStagePhase
import zed.rainxch.core.domain.system.InstallOutcome
import zed.rainxch.core.domain.system.InstallPolicy
import zed.rainxch.core.domain.system.OrchestratedDownload
import zed.rainxch.core.domain.system.phase
import zed.rainxch.details.presentation.RawDetailsState
import zed.rainxch.details.presentation.model.AttestationStatus
import zed.rainxch.details.presentation.model.DownloadStage
import zed.rainxch.details.presentation.model.LogResult
import zed.rainxch.details.presentation.utils.recoverStepFailure
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import zed.rainxch.core.domain.system.DownloadStage as OrchestratorStage

// Downloads run in the app-wide orchestrator, so they outlive the page; this starts them and mirrors their progress.
internal class OrchestratedDownloads(
    private val downloadOrchestrator: DownloadOrchestrator,
    private val installStep: ApkInstallStep,
    private val tweaksRepository: TweaksRepository,
    private val appScope: CoroutineScope,
    private val logger: KomiStoreLogger,
) {
    internal fun install(
        session: DetailsSession,
        request: InstallRequest,
    ) {
        session.currentDownloadJob?.cancel()
        val packageKey = orchestratorKey(session.current)
        val asset = session.current.primaryAsset
        val repository = session.current.repository
        if (asset == null || repository == null) {
            logger.warn("installAsset called with missing primaryAsset/repository")
            return
        }
        session.currentAssetName = request.assetName
        val parkedFilePath = parkedFilePathIfMatches(session.current, request)
        if (parkedFilePath != null) {
            logger.debug("Reusing parked file for ${request.releaseTag} / ${request.assetName}")
            session.currentDownloadJob = session.launch { installParked(session, request, parkedFilePath) }
        } else {
            session.appendLog(request, if (request.isUpdate) LogResult.UpdateStarted else LogResult.DownloadStarted)
            session.currentDownloadJob = session.launch { enqueueInstall(session, packageKey, repository, asset, request) }
        }
    }

    private suspend fun installParked(
        session: DetailsSession,
        request: InstallRequest,
        filePath: String,
    ) {
        recoverStepFailure(
            recover = { error ->
                logger.error("Install of parked file failed: ${error.message}")
                failInstall(session, request, error)
            },
        ) {
            session.appendLog(request, LogResult.Downloaded)
            installStep.install(session, request, filePath)
        }
    }

    private suspend fun enqueueInstall(
        session: DetailsSession,
        packageKey: String,
        repository: GithubRepoSummary,
        asset: GithubAsset,
        request: InstallRequest,
    ) {
        recoverStepFailure(
            recover = { error ->
                logger.error("Install failed: ${error.message}", error)
                failInstall(session, request, error)
            },
        ) {
            val policy = installPolicy(session)
            downloadOrchestrator.enqueue(repository.downloadSpec(packageKey, session.args.sourceHost, asset, request.releaseTag, policy))
            session.update {
                it.copy(
                    downloadError = null,
                    installError = null,
                    downloadProgressPercent = null,
                    downloadStage = DownloadStage.DOWNLOADING,
                    downloadedBytes = 0L,
                    totalBytes = request.sizeBytes,
                    attestationStatus = AttestationStatus.UNCHECKED,
                )
            }
            observeEntry(session, packageKey, request)
        }
    }

    private fun failInstall(
        session: DetailsSession,
        request: InstallRequest,
        error: Exception,
    ) {
        session.update { it.copy(downloadStage = DownloadStage.IDLE, installError = error.message) }
        session.currentAssetName = null
        session.appendLog(request, LogResult.Error(error.message))
    }

    // Shizuku and Dhizuku install silently, so they never wait for the page to be in front.
    private suspend fun installPolicy(session: DetailsSession): InstallPolicy {
        val installerType = tweaksRepository.getInstallerType().first()
        return when {
            session.platform != Platform.ANDROID -> InstallPolicy.AlwaysInstall
            installerType == InstallerType.SHIZUKU -> InstallPolicy.AlwaysInstall
            installerType == InstallerType.DHIZUKU -> InstallPolicy.AlwaysInstall
            else -> InstallPolicy.InstallWhileForeground
        }
    }

    private fun GithubRepoSummary.downloadSpec(
        packageKey: String,
        sourceHost: String?,
        asset: GithubAsset,
        releaseTag: String,
        policy: InstallPolicy,
    ): DownloadSpec =
        DownloadSpec(
            packageName = packageKey,
            repoOwner = owner.login,
            repoName = name,
            repoId = id,
            sourceHost = sourceHost,
            repoOwnerAvatarUrl = owner.avatarUrl,
            repoDescription = description,
            asset = asset,
            displayAppName = name,
            installPolicy = policy,
            releaseTag = releaseTag,
        )

    internal fun download(
        session: DetailsSession,
        request: InstallRequest,
    ) {
        session.currentDownloadJob?.cancel()
        val packageKey = orchestratorKey(session.current)
        val repository = session.current.repository ?: return
        val asset =
            session.current.selectedRelease
                ?.assets
                ?.find { it.downloadUrl == request.downloadUrl }
                ?: session.current.primaryAsset
                ?: return
        session.currentAssetName = request.assetName
        session.appendLog(request, LogResult.DownloadStarted)
        session.update { it.copy(isDownloading = true, downloadError = null, installError = null, downloadProgressPercent = null) }
        session.currentDownloadJob =
            session.launch {
                recoverStepFailure(
                    recover = { error ->
                        session.update { it.copy(isDownloading = false, downloadError = error.message) }
                        session.currentAssetName = null
                        session.appendLog(request, LogResult.Error(error.message))
                    },
                ) {
                    val spec =
                        repository.downloadSpec(
                            packageKey = packageKey,
                            sourceHost = session.args.sourceHost,
                            asset = asset,
                            releaseTag = request.releaseTag,
                            policy = InstallPolicy.DeferUntilUserAction,
                        )
                    downloadOrchestrator.enqueue(spec)
                    observeEntry(session, packageKey, request)
                }
            }
    }

    // A parked file from an earlier download of this same release and asset is installed instead of downloading again.
    private fun parkedFilePathIfMatches(
        state: RawDetailsState,
        request: InstallRequest,
    ): String? {
        val app = state.installedApp
        val matches = app?.pendingInstallVersion == request.releaseTag && app.pendingInstallAssetName == request.assetName
        val path = app?.pendingInstallFilePath?.takeIf { matches } ?: return null
        return try {
            path.takeIf { File(it).exists() && File(it).length() > 0 }
        } catch (e: SecurityException) {
            logger.warn("Failed to stat parked install file: ${e.message}")
            null
        }
    }

    internal fun orchestratorKey(state: RawDetailsState): String =
        state.installedApp?.packageName
            ?: state.repository?.let { "${it.owner.login}/${it.name}" }
            ?: NO_ORCHESTRATOR_KEY

    // While no page-started job runs, the page shows whatever the orchestrator is doing for this app.
    internal fun observeForDisplay(session: DetailsSession) {
        session.launch {
            combine(
                downloadOrchestrator.downloads,
                session.state.map { orchestratorKey(it) }.distinctUntilChanged(),
                session.localDownloadJob,
            ) { downloads, packageKey, job -> Triple(downloads[packageKey], packageKey, job) }
                .collect { (entry, packageKey, job) ->
                    if (packageKey != NO_ORCHESTRATOR_KEY && job?.isActive != true) session.update { mirrored(it, entry) }
                }
        }
    }

    private fun mirrored(
        current: RawDetailsState,
        entry: OrchestratedDownload?,
    ): RawDetailsState =
        when (entry?.stage) {
            OrchestratorStage.Queued,
            OrchestratorStage.Downloading,
            -> current.withProgress(DownloadStage.DOWNLOADING, entry)

            OrchestratorStage.Paused -> current.withProgress(DownloadStage.PAUSED, entry)

            OrchestratorStage.Installing -> current.copy(downloadStage = DownloadStage.INSTALLING)

            null,
            OrchestratorStage.AwaitingInstall,
            OrchestratorStage.Completed,
            OrchestratorStage.Cancelled,
            OrchestratorStage.Failed,
            -> current.atRest()
        }

    private fun RawDetailsState.withProgress(
        stage: DownloadStage,
        entry: OrchestratedDownload,
    ): RawDetailsState =
        copy(
            downloadStage = stage,
            downloadProgressPercent = entry.progressPercent,
            downloadedBytes = entry.bytesDownloaded,
            totalBytes = entry.totalBytes ?: totalBytes,
        )

    private fun RawDetailsState.atRest(): RawDetailsState {
        val isResting = downloadStage == DownloadStage.IDLE && downloadProgressPercent == null && totalBytes == null
        return if (isResting) {
            this
        } else {
            copy(downloadStage = DownloadStage.IDLE, downloadProgressPercent = null, downloadedBytes = 0L, totalBytes = null)
        }
    }

    private suspend fun observeEntry(
        session: DetailsSession,
        packageKey: String,
        request: InstallRequest,
    ) {
        val installFired = AtomicBoolean(false)
        downloadOrchestrator
            .observe(packageKey)
            .transformWhile { entry ->
                emit(entry)
                entry == null || entry.isStillRunning()
            }.collect { entry ->
                if (entry == null) {
                    onEntryGone(session)
                } else {
                    onEntry(session, packageKey, request, entry, installFired)
                }
            }
    }

    private fun OrchestratedDownload.isStillRunning(): Boolean =
        stage.phase == DownloadStagePhase.Live || stage.phase == DownloadStagePhase.Installing || stage == OrchestratorStage.Paused

    private fun onEntryGone(session: DetailsSession) {
        if (session.current.downloadStage != DownloadStage.IDLE) {
            session.update { it.copy(downloadStage = DownloadStage.IDLE, downloadProgressPercent = null) }
        }
        session.currentAssetName = null
    }

    private suspend fun onEntry(
        session: DetailsSession,
        packageKey: String,
        request: InstallRequest,
        entry: OrchestratedDownload,
        installFired: AtomicBoolean,
    ) {
        session.update {
            it.copy(
                downloadProgressPercent = entry.progressPercent,
                downloadedBytes = entry.bytesDownloaded,
                totalBytes = entry.totalBytes ?: request.sizeBytes,
            )
        }
        when (entry.stage) {
            OrchestratorStage.Queued,
            OrchestratorStage.Downloading,
            -> {
                session.update { it.copy(downloadStage = DownloadStage.DOWNLOADING) }
            }

            OrchestratorStage.Installing -> {
                session.update { it.copy(downloadStage = DownloadStage.INSTALLING) }
            }

            OrchestratorStage.Paused -> {
                session.update { it.copy(downloadStage = DownloadStage.PAUSED) }
            }

            OrchestratorStage.AwaitingInstall -> {
                if (installFired.compareAndSet(false, true)) installDownloaded(session, packageKey, request, entry)
            }

            OrchestratorStage.Completed -> {
                onCompleted(session, packageKey, request, entry)
            }

            OrchestratorStage.Cancelled -> {
                onCancelled(session, request)
            }

            OrchestratorStage.Failed -> {
                onFailed(session, packageKey, request, entry)
            }
        }
    }

    private suspend fun installDownloaded(
        session: DetailsSession,
        packageKey: String,
        request: InstallRequest,
        entry: OrchestratedDownload,
    ) {
        val filePath = entry.filePath ?: return
        session.update { it.copy(downloadStage = DownloadStage.VERIFYING) }
        session.appendLog(request, LogResult.Downloaded)
        recoverStepFailure(
            recover = { error ->
                logger.error("Foreground install failed: ${error.message}")
                session.update { it.copy(downloadStage = DownloadStage.IDLE, installError = error.message) }
                session.appendLog(request, LogResult.Error(error.message))
            },
        ) {
            installStep.install(session, request.copy(downloadUrl = entry.downloadUrl, releaseTag = entry.releaseTag), filePath)
            downloadOrchestrator.dismiss(packageKey)
        }
    }

    private suspend fun onCompleted(
        session: DetailsSession,
        packageKey: String,
        request: InstallRequest,
        entry: OrchestratedDownload,
    ) {
        val outcome = entry.installOutcome ?: InstallOutcome.COMPLETED
        val isCompleted = outcome == InstallOutcome.COMPLETED
        session.update { it.copy(downloadStage = DownloadStage.IDLE) }
        session.currentAssetName = null
        session.appendLog(request, if (isCompleted) request.finishedResult() else LogResult.Downloaded)
        if (session.platform == Platform.ANDROID) {
            val filePath = entry.filePath
            if (filePath != null) {
                val settled = request.copy(downloadUrl = entry.downloadUrl, releaseTag = entry.releaseTag)
                installStep.recordSettledInstall(session, settled, filePath, outcome)
            } else {
                logger.warn("Orchestrator install settled (outcome=$outcome) but filePath is null; DB not updated")
            }
        }
        if (isCompleted) downloadOrchestrator.dismiss(packageKey)
    }

    private fun onCancelled(
        session: DetailsSession,
        request: InstallRequest,
    ) {
        session.update { it.copy(downloadStage = DownloadStage.IDLE, downloadProgressPercent = null) }
        session.currentAssetName = null
        session.appendLog(request, LogResult.Cancelled)
    }

    private fun onFailed(
        session: DetailsSession,
        packageKey: String,
        request: InstallRequest,
        entry: OrchestratedDownload,
    ) {
        session.update { it.copy(downloadStage = DownloadStage.IDLE, downloadError = entry.errorMessage) }
        session.currentAssetName = null
        session.appendLog(request, LogResult.Error(entry.errorMessage))
        downloadOrchestrator.dismiss(packageKey)
    }

    internal fun pause(session: DetailsSession) {
        val packageKey = orchestratorKey(session.current)
        session.launch {
            recoverStepFailure({ logger.error("Failed to pause orchestrator download: ${it.message}") }) {
                downloadOrchestrator.cancel(packageKey)
            }
        }
    }

    internal fun resume(session: DetailsSession) {
        val packageKey = orchestratorKey(session.current)
        session.launch {
            recoverStepFailure({ logger.error("Failed to resume orchestrator download: ${it.message}") }) {
                downloadOrchestrator.resume(packageKey)
            }
        }
    }

    internal fun discard(session: DetailsSession) {
        session.currentDownloadJob?.cancel()
        session.currentDownloadJob = null
        val packageKey = orchestratorKey(session.current)
        session.launch {
            recoverStepFailure({ logger.error("Failed to discard orchestrator download: ${it.message}") }) {
                downloadOrchestrator.discard(packageKey)
            }
        }
        session.currentAssetName?.let { assetName ->
            val current = session.current
            session.appendLog(
                assetName = assetName,
                size = current.totalBytes ?: current.downloadedBytes,
                tag = current.selectedRelease?.tagName ?: "",
                result = LogResult.Cancelled,
            )
            logger.debug("Download discarded via orchestrator: $assetName")
        }
        session.currentAssetName = null
        session.update { it.copy(isDownloading = false, downloadProgressPercent = null, downloadStage = DownloadStage.IDLE) }
    }

    // Leaving the page hands an in-flight install back to the notification instead of installing in the background.
    internal fun onCleared(session: DetailsSession) {
        session.currentDownloadJob?.cancel()
        val packageKey = orchestratorKey(session.current)
        appScope.launch {
            recoverStepFailure({ logger.error("Failed to downgrade orchestrator on screen leave: ${it.message}") }) {
                downloadOrchestrator.downgradeToDeferred(packageKey)
            }
        }
    }

    private companion object {
        const val NO_ORCHESTRATOR_KEY = ""
    }
}
