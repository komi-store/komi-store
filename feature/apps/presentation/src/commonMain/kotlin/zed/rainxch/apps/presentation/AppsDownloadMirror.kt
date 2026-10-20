package zed.rainxch.apps.presentation

import zed.rainxch.apps.presentation.model.UpdateState
import zed.rainxch.core.domain.model.installation.InstalledApp
import zed.rainxch.core.domain.model.installation.markPending
import zed.rainxch.core.domain.model.installation.withLatestSnapshot
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.DownloadStagePhase
import zed.rainxch.core.domain.system.OrchestratedDownload
import zed.rainxch.core.domain.system.phase
import zed.rainxch.core.domain.utils.VersionMath

internal val transientCardStages =
    setOf(
        DownloadStage.Queued,
        DownloadStage.Downloading,
        DownloadStage.Paused,
        DownloadStage.Installing,
        DownloadStage.AwaitingInstall,
        DownloadStage.Failed,
    )

internal val rowTakeoverStages = transientCardStages - DownloadStage.AwaitingInstall

internal fun transientDownloads(
    snapshot: Map<String, OrchestratedDownload>,
    knownPackages: Set<String>,
    cardStages: Set<DownloadStage> = transientCardStages,
    takeoverStages: Set<DownloadStage> = rowTakeoverStages,
): List<OrchestratedDownload> =
    snapshot
        .filter { (key, entry) ->
            val hasRow = key in knownPackages
            entry.stage in cardStages && (!hasRow || entry.stage in takeoverStages)
        }.values
        .toList()

internal fun hiddenRowPackages(
    snapshot: Map<String, OrchestratedDownload>,
    knownPackages: Set<String>,
    takeoverStages: Set<DownloadStage> = rowTakeoverStages,
): Set<String> =
    snapshot
        .filter { (key, entry) -> key in knownPackages && entry.stage in takeoverStages }
        .keys
        .toSet()

// Recorded from the orchestrator's entry, not the target the update asked for: a refetch can
// move the download to a newer release.
internal fun InstalledApp.markDownloadedUpdate(
    download: OrchestratedDownload,
    versionName: String?,
    versionCode: Long?,
): InstalledApp =
    markPending(
        releaseId = download.releaseId,
        assetId = download.assetId,
        assetDigest = download.assetDigest,
    ).withLatestSnapshot(
        version = download.releaseTag,
        assetName = download.assetName,
        assetUrl = download.downloadUrl,
        versionName = versionName ?: download.releaseTag,
        versionCode = versionCode ?: 0L,
    )

internal fun isVersionDowngrade(
    installedVersion: String?,
    targetVersion: String?,
): Boolean {
    if (installedVersion.isNullOrBlank() || targetVersion.isNullOrBlank()) return false
    return VersionMath.compareVersions(targetVersion, installedVersion) < 0
}

internal fun mirroredUpdateState(
    stage: DownloadStage,
    errorMessage: String?,
    failedFallback: String,
): UpdateState =
    when (stage.phase) {
        DownloadStagePhase.Live -> UpdateState.Downloading
        DownloadStagePhase.Installing -> UpdateState.Installing
        DownloadStagePhase.Resting -> UpdateState.Idle
        DownloadStagePhase.Failed -> UpdateState.Error(errorMessage ?: failedFallback)
    }
