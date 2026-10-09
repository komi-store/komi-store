package zed.rainxch.apps.presentation

import zed.rainxch.apps.presentation.model.UpdateState
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.DownloadStagePhase
import zed.rainxch.core.domain.system.OrchestratedDownload
import zed.rainxch.core.domain.system.phase
import zed.rainxch.core.domain.utils.VersionMath

// The two halves of the library's orchestrator mirror that are decisions rather than plumbing.
// They live here so both can be pinned by a test without constructing a ViewModel and its
// collaborators, which is the only reason they were untested.

// The stages that can render a transient card. Completed and Cancelled are terminal: the row is
// either in a library group by then or gone entirely, and a card would outlive the download it
// describes. The set lives next to the function it feeds so a test pins this decision itself.
internal val transientCardStages =
    setOf(
        DownloadStage.Queued,
        DownloadStage.Downloading,
        DownloadStage.Paused,
        DownloadStage.Installing,
        DownloadStage.AwaitingInstall,
        DownloadStage.Failed,
    )

// A download that takes its row off the library groups and lives on a card instead. AwaitingInstall
// is deliberately absent: a parked app is offered by the library's own "pending installs" group,
// so its row must stay, card or no card. `transientCardStages` minus this set is exactly the stages
// that only ever apply to downloads with no row at all.
internal val rowTakeoverStages = transientCardStages - DownloadStage.AwaitingInstall

// Downloads the library renders as a card. Two kinds qualify: a download with no library row
// (its registry key is "owner/name", so it can never match one), across its whole life; and a
// download that does have a row but whose stage hands the row over to the card, which is then
// hidden from every library group so the app is on screen exactly once.
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
        }
        .values
        .toList()

// The library rows whose download now lives on a card. Kept next to [transientDownloads] because
// the two must agree: whatever a card shows, its row must not also show.
internal fun hiddenRowPackages(
    snapshot: Map<String, OrchestratedDownload>,
    knownPackages: Set<String>,
    takeoverStages: Set<DownloadStage> = rowTakeoverStages,
): Set<String> =
    snapshot
        .filter { (key, entry) -> key in knownPackages && entry.stage in takeoverStages }
        .keys
        .toSet()

// Whether the card's version line points backwards: the transfer would leave the app on an older
// version than the one installed. Only meaningful with a row to compare against; a fresh install
// has no installed version and is never a downgrade.
internal fun isVersionDowngrade(installedVersion: String?, targetVersion: String?): Boolean {
    if (installedVersion.isNullOrBlank() || targetVersion.isNullOrBlank()) return false
    return VersionMath.compareVersions(targetVersion, installedVersion) < 0
}

// The row state a stage maps to. [failedFallback] is used when a failure carries no message of its
// own; it is passed in rather than resolved here because reading a string resource suspends.
internal fun mirroredUpdateState(
    stage: DownloadStage,
    errorMessage: String?,
    failedFallback: String,
): UpdateState =
    when (stage.phase) {
        DownloadStagePhase.Live -> UpdateState.Downloading

        DownloadStagePhase.Installing -> UpdateState.Installing

        // A parked or finished download is offered by the library's own groups, which render from
        // the database; leaving Downloading behind would draw a progress bar instead of a button.
        DownloadStagePhase.Resting -> UpdateState.Idle

        DownloadStagePhase.Failed -> UpdateState.Error(errorMessage ?: failedFallback)
    }
