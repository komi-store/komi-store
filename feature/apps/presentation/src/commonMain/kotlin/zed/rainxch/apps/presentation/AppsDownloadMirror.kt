package zed.rainxch.apps.presentation

import zed.rainxch.apps.presentation.model.UpdateState
import zed.rainxch.core.domain.system.DownloadStage
import zed.rainxch.core.domain.system.DownloadStagePhase
import zed.rainxch.core.domain.system.OrchestratedDownload
import zed.rainxch.core.domain.system.phase

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
        DownloadStage.Installing,
        DownloadStage.AwaitingInstall,
        DownloadStage.Failed,
    )

// Downloads the library has no row for. Their registry key is "owner/name" rather than a package
// name, so they can never match a loaded row and are rendered from the orchestrator entry alone.
internal fun transientDownloads(
    snapshot: Map<String, OrchestratedDownload>,
    knownPackages: Set<String>,
    cardStages: Set<DownloadStage> = transientCardStages,
): List<OrchestratedDownload> =
    snapshot
        .filter { (key, entry) -> key !in knownPackages && entry.stage in cardStages }
        .values
        .toList()

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
